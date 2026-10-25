package br.pucminas.aed.risco.adapter.in.kafka;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;

/**
 * Politica de falha dos consumidores do servico-risco.
 *
 * <p>Falha transitoria (banco indisponivel, timeout de conexao, broker sem confirmar a
 * publicacao) e retentada com espera exponencial e limite de tentativas. Falha permanente
 * (payload malformado, cabecalho obrigatorio ausente, solicitacao que o dominio recusa) vai
 * direto para a DLQ, sem gastar tentativa: reprocessar o mesmo registro produziria exatamente o
 * mesmo erro.
 *
 * <p>Os tres listeners leem o valor como texto e o traduzem no proprio adaptador. Assim a DLQ
 * guarda exatamente o que chegou, e o reprocessamento republica os mesmos bytes.
 */
@Configuration
public class PoliticaDeFalhaKafkaConfig {

    /** Uma DLQ por topico consumido: {@code <topico>.dlq}. */
    @Bean
    public KafkaAdmin.NewTopics topicosDeDlq(
            @Value("${app.kafka.topico.solicitacao}") List<String> topicosDeSolicitacao,
            @Value("${app.kafka.topico.limite-reservado}") String topicoLimiteReservado,
            @Value("${app.kafka.topico.reserva-cancelada}") String topicoReservaCancelada,
            @Value("${app.kafka.topico.sufixo-dlq}") String sufixo) {
        return new KafkaAdmin.NewTopics(
                Stream.concat(topicosDeSolicitacao.stream(), Stream.of(topicoLimiteReservado, topicoReservaCancelada))
                        .map(topico -> new NewTopic(topico + sufixo, 3, (short) 1))
                        .toArray(NewTopic[]::new));
    }

    /**
     * Publicador da DLQ. O valor chega como texto, quando quem falhou foi o listener, ou como
     * {@code byte[]}, quando o reprocessamento republica o que leu da DLQ.
     */
    @Bean
    public KafkaTemplate<String, Object> clienteDaDlq(KafkaProperties propriedades) {
        Map<Class<?>, Serializer<?>> delegados = new LinkedHashMap<>();
        delegados.put(byte[].class, new ByteArraySerializer());
        delegados.put(String.class, new StringSerializer());

        Map<String, Object> configuracao = propriedades.buildProducerProperties(null);
        configuracao.remove(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG);
        configuracao.remove(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG);

        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                configuracao, new StringSerializer(), new DelegatingByTypeSerializer(delegados, true)));
    }

    /**
     * Um unico publicador para todas as DLQs do servico, com destino {@code <topico>.dlq}. O
     * reprocessamento republica no topico original, entao uma nova falha volta para a mesma DLQ.
     */
    @Bean
    public DeadLetterPublishingRecoverer publicadorDaDlq(
            KafkaTemplate<String, Object> clienteDaDlq,
            @Value("${app.kafka.topico.sufixo-dlq}") String sufixoDlq) {
        var recuperador = new DeadLetterPublishingRecoverer(clienteDaDlq,
                (registro, falha) -> new TopicPartition(registro.topic() + sufixoDlq, -1));
        recuperador.setHeadersFunction(new CabecalhosDeFalhaFunction());
        // Sem isso o envio para a DLQ so aparece em DEBUG: a mensagem sumiria do fluxo sem
        // deixar rastro no log da aplicacao.
        recuperador.setLogRecoveryRecord(true);
        return recuperador;
    }

    /** Unico CommonErrorHandler do contexto: o Spring Boot o liga a fabrica padrao dos listeners. */
    @Bean
    public DefaultErrorHandler tratadorDeFalhaDoConsumidor(
            DeadLetterPublishingRecoverer publicadorDaDlq,
            @Value("${app.kafka.retentativa.tentativas}") int tentativas,
            @Value("${app.kafka.retentativa.intervalo-inicial-ms}") long intervaloInicialMs,
            @Value("${app.kafka.retentativa.multiplicador}") double multiplicador,
            @Value("${app.kafka.retentativa.intervalo-maximo-ms}") long intervaloMaximoMs) {

        var espera = new ExponentialBackOffWithMaxRetries(tentativas);
        espera.setInitialInterval(intervaloInicialMs);
        espera.setMultiplier(multiplicador);
        espera.setMaxInterval(intervaloMaximoMs);

        var tratador = new DefaultErrorHandler(publicadorDaDlq, espera);
        registrarFalhasPermanentes(tratador);
        tratador.setCommitRecovered(true);
        return tratador;
    }

    @SuppressWarnings("unchecked")
    private void registrarFalhasPermanentes(DefaultErrorHandler tratador) {
        tratador.addNotRetryableExceptions(
                CabecalhosDeFalhaFunction.FALHAS_PERMANENTES.toArray(new Class[0]));
    }

    /** Reprocessa a DLQ uma vez, na inicializacao, somente quando pedido explicitamente. */
    @Bean
    @ConditionalOnProperty("app.kafka.reprocessamento-dlq.habilitado")
    public ApplicationRunner reprocessamentoDaDlqNaInicializacao(ReprocessamentoDlqService reprocessamento) {
        return argumentos -> reprocessamento.reprocessar();
    }
}
