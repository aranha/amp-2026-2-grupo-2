package br.pucminas.aed.risco;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.utils.ContainerTestUtils;

/** Apoio dos testes de integracao: publica como o servico-credito publicaria e le os topicos. */
public final class ApoioKafka {

    public static final String SOLICITACAO = "credito.solicitacao.v1";
    public static final String SOLICITACAO_LEGADO = "credito.solicitacao.solicitada.v1";
    public static final String LIMITE_RESERVADO = "credito.limite.reservado.v1";
    public static final String RESERVA_CANCELADA = "credito.reserva-limite.cancelada.v1";
    public static final String ELEGIBILIDADE = "risco.elegibilidade.v1";
    public static final int PARTICOES = 3;
    public static final Duration PRAZO = Duration.ofSeconds(20);

    private ApoioKafka() {
    }

    /**
     * Espera cada listener do servico receber as particoes dos seus topicos. Sem isso o primeiro
     * teste da classe disputa o prazo com a entrada no grupo.
     */
    public static void aguardarConsumidores(KafkaListenerEndpointRegistry registro) {
        for (MessageListenerContainer container : registro.getListenerContainers()) {
            ContainerTestUtils.waitForAssignment(container,
                    container.getContainerProperties().getTopics().length * PARTICOES);
        }
    }

    public static KafkaProducer<String, String> publicador(String servidores) {
        Properties propriedades = new Properties();
        propriedades.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, servidores);
        propriedades.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        propriedades.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        propriedades.put(ProducerConfig.ACKS_CONFIG, "all");
        return new KafkaProducer<>(propriedades);
    }

    /** Registro com o envelope CloudEvents em binary mode, como no asyncapi.yaml. */
    public static ProducerRecord<String, String> registro(String topico, String chave, String eventoId,
                                                          String origem, String valor) {
        var registro = new ProducerRecord<String, String>(topico, chave, valor);
        registro.headers().add("ce_specversion", "1.0".getBytes(UTF_8));
        registro.headers().add("ce_id", eventoId.getBytes(UTF_8));
        registro.headers().add("ce_source", origem.getBytes(UTF_8));
        registro.headers().add("ce_type", topico.getBytes(UTF_8));
        registro.headers().add("ce_time", "2026-10-25T10:00:00-03:00".getBytes(UTF_8));
        registro.headers().add("ce_datacontenttype", "application/json".getBytes(UTF_8));
        return registro;
    }

    public static String solicitacaoJson(String solicitacaoId, String valorSolicitado, String dataSolicitacao) {
        return """
                {
                  "eventoId": "id-do-corpo-nao-usado-para-deduplicacao",
                  "solicitacaoId": "%s",
                  "clienteId": "cli-ficticio-001",
                  "valorSolicitado": %s,
                  "dataSolicitacao": "%s",
                  "canalOrigem": "app-cliente"
                }
                """.formatted(solicitacaoId, valorSolicitado, dataSolicitacao);
    }

    public static KafkaConsumer<String, String> consumidor(String servidores, String grupo) {
        Properties propriedades = new Properties();
        propriedades.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, servidores);
        propriedades.put(ConsumerConfig.GROUP_ID_CONFIG, grupo);
        propriedades.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        propriedades.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(propriedades);
    }

    /**
     * Atribuicao explicita no fim do topico: cada teste enxerga apenas o que ele proprio produziu,
     * sem herdar os registros dos testes anteriores da classe.
     */
    public static KafkaConsumer<String, String> leitorNoFim(String servidores, String topico) {
        var leitor = consumidor(servidores, "teste-" + System.nanoTime());
        var particoes = particoes(leitor, topico);
        leitor.assign(particoes);
        leitor.seekToEnd(particoes);
        particoes.forEach(leitor::position);
        return leitor;
    }

    public static List<TopicPartition> particoes(KafkaConsumer<String, String> consumidor, String topico) {
        return consumidor.partitionsFor(topico).stream()
                .map(informacao -> new TopicPartition(topico, informacao.partition()))
                .toList();
    }

    public static ConsumerRecord<String, String> lerDe(KafkaConsumer<String, String> leitor) {
        return lerDe(leitor, PRAZO);
    }

    public static ConsumerRecord<String, String> lerDe(KafkaConsumer<String, String> leitor, Duration prazo) {
        long limite = System.currentTimeMillis() + prazo.toMillis();
        while (System.currentTimeMillis() < limite) {
            for (ConsumerRecord<String, String> registro : leitor.poll(Duration.ofMillis(500))) {
                return registro;
            }
        }
        return null;
    }

    /** Coleta durante toda a janela, em vez de parar no primeiro registro. */
    public static List<ConsumerRecord<String, String>> lerTodosDe(KafkaConsumer<String, String> leitor,
                                                                 Duration janela) {
        var coletados = new ArrayList<ConsumerRecord<String, String>>();
        long limite = System.currentTimeMillis() + janela.toMillis();
        while (System.currentTimeMillis() < limite) {
            leitor.poll(Duration.ofMillis(500)).forEach(coletados::add);
        }
        return coletados;
    }

    public static String cabecalho(ConsumerRecord<String, String> registro, String nome) {
        Header cabecalho = registro.headers().lastHeader(nome);
        return cabecalho == null ? null : new String(cabecalho.value(), UTF_8);
    }

    public static Map<String, String> cabecalhos(ConsumerRecord<String, String> registro) {
        var cabecalhos = new LinkedHashMap<String, String>();
        registro.headers().forEach(cabecalho ->
                cabecalhos.put(cabecalho.key(), new String(cabecalho.value(), UTF_8)));
        return cabecalhos;
    }
}
