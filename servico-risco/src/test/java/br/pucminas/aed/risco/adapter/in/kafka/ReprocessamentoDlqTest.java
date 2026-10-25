package br.pucminas.aed.risco.adapter.in.kafka;

import static br.pucminas.aed.risco.ApoioKafka.ELEGIBILIDADE;
import static br.pucminas.aed.risco.ApoioKafka.LIMITE_RESERVADO;
import static br.pucminas.aed.risco.ApoioKafka.PRAZO;
import static br.pucminas.aed.risco.ApoioKafka.RESERVA_CANCELADA;
import static br.pucminas.aed.risco.ApoioKafka.SOLICITACAO;
import static br.pucminas.aed.risco.ApoioKafka.SOLICITACAO_LEGADO;
import static br.pucminas.aed.risco.ApoioKafka.cabecalho;
import static br.pucminas.aed.risco.ApoioKafka.cabecalhos;
import static br.pucminas.aed.risco.ApoioKafka.lerDe;
import static br.pucminas.aed.risco.ApoioKafka.lerTodosDe;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import br.pucminas.aed.risco.ApoioKafka;
import br.pucminas.aed.risco.BancoDeTeste;
import br.pucminas.aed.risco.adapter.out.jdbc.JdbcAnalisesDeCredito;
import br.pucminas.aed.risco.adapter.out.jdbc.JdbcEventosProcessados;
import br.pucminas.aed.risco.application.port.in.AgregarFluxoDeSolicitacoes;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.TopicPartition;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@EmbeddedKafka(partitions = ApoioKafka.PARTICOES, topics = {
        SOLICITACAO, SOLICITACAO + ".dlq",
        SOLICITACAO_LEGADO, SOLICITACAO_LEGADO + ".dlq",
        LIMITE_RESERVADO, LIMITE_RESERVADO + ".dlq",
        RESERVA_CANCELADA, RESERVA_CANCELADA + ".dlq",
        ELEGIBILIDADE
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.datasource.url=jdbc:h2:mem:risco-reprocessamento;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "app.kafka.retentativa.tentativas=2",
        "app.kafka.retentativa.intervalo-inicial-ms=50",
        "app.kafka.retentativa.intervalo-maximo-ms=100"
})
class ReprocessamentoDlqTest {
    private static final String TOPICO_DLQ = SOLICITACAO + ".dlq";
    private static final String TOPICO_RESERVADO_DLQ = LIMITE_RESERVADO + ".dlq";
    private static final List<String> DLQS = List.of(TOPICO_DLQ, SOLICITACAO_LEGADO + ".dlq",
            TOPICO_RESERVADO_DLQ, RESERVA_CANCELADA + ".dlq");
    private static final String GRUPO_REPROCESSAMENTO = "risco-reprocessamento-dlq-v1";
    private static final int EXECUCOES_ATE_A_DLQ = 3;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private KafkaListenerEndpointRegistry listeners;
    @Autowired private ReprocessamentoDlqService reprocessamento;
    @Value("${spring.embedded.kafka.brokers}") private String servidores;

    @SpyBean private JdbcAnalisesDeCredito analises;
    @SpyBean private JdbcEventosProcessados eventosProcessados;
    @SpyBean private AgregarFluxoDeSolicitacoes fluxoDeSolicitacoes;

    private BancoDeTeste banco;
    private KafkaProducer<String, String> publicador;
    private KafkaConsumer<String, String> consumidorDaDlq;
    private KafkaConsumer<String, String> consumidorDoTopico;
    private KafkaConsumer<String, String> consumidorDaDlqDoReservado;

    @BeforeEach
    void preparar() {
        banco = new BancoDeTeste(jdbc);
        banco.limpar();
        ApoioKafka.aguardarConsumidores(listeners);
        publicador = ApoioKafka.publicador(servidores);

        // Cada teste enxerga e reprocessa apenas o que ele proprio produziu: os leitores
        // comecam no fim dos topicos e o grupo de reprocessamento, no fim de cada DLQ.
        consumidorDaDlq = ApoioKafka.leitorNoFim(servidores, TOPICO_DLQ);
        consumidorDoTopico = ApoioKafka.leitorNoFim(servidores, SOLICITACAO);
        consumidorDaDlqDoReservado = ApoioKafka.leitorNoFim(servidores, TOPICO_RESERVADO_DLQ);
        try (var grupo = ApoioKafka.consumidor(servidores, GRUPO_REPROCESSAMENTO)) {
            var fim = new LinkedHashMap<TopicPartition, OffsetAndMetadata>();
            for (String dlq : DLQS) {
                grupo.endOffsets(ApoioKafka.particoes(grupo, dlq)).forEach((particao, offset) ->
                        fim.put(particao, new OffsetAndMetadata(offset)));
            }
            grupo.commitSync(fim);
        }
    }

    @AfterEach
    void encerrar() {
        publicador.close();
        consumidorDaDlq.close();
        consumidorDoTopico.close();
        consumidorDaDlqDoReservado.close();
    }

    @Test
    void reprocessamentoRepublicaNoTopicoOriginalComMesmoCeIdEMesmaChave() {
        doThrow(new DataAccessResourceFailureException("banco indisponivel")).when(analises).registrar(any());
        String solicitacaoId = UUID.randomUUID().toString();
        publicar("evt-reprocessa-001", solicitacaoId);
        ConsumerRecord<String, String> original = lerDe(consumidorDoTopico);
        assertThat(lerDe(consumidorDaDlq)).isNotNull();
        assertThat(banco.analises()).isZero();

        doCallRealMethod().when(analises).registrar(any());
        assertThat(reprocessamento.reprocessar()).isEqualTo(1);

        ConsumerRecord<String, String> republicado = lerDe(consumidorDoTopico);
        assertThat(republicado).isNotNull();
        assertThat(republicado.key()).isEqualTo(solicitacaoId);
        assertThat(republicado.partition()).isEqualTo(original.partition());
        // Os mesmos cabecalhos ce_* do original, e nenhum kafka_dlt-* ou classificacao da
        // passagem pela DLQ. E os mesmos bytes: campo que o Risco nao declara sobrevive.
        assertThat(cabecalhos(republicado)).isEqualTo(cabecalhos(original));
        assertThat(republicado.value()).isEqualTo(original.value()).contains("\"canalOrigem\": \"app-cliente\"");

        Awaitility.await().atMost(PRAZO).untilAsserted(() -> assertThat(banco.analises()).isEqualTo(1));
    }

    @Test
    void mesmoRegistroReprocessadoDuasVezesProduzUmUnicoEfeito() {
        doThrow(new DataAccessResourceFailureException("banco indisponivel")).when(analises).registrar(any());
        publicar("evt-reprocessa-002", UUID.randomUUID().toString());
        ConsumerRecord<String, String> naDlq = lerDe(consumidorDaDlq);
        assertThat(naDlq).isNotNull();

        doCallRealMethod().when(analises).registrar(any());
        assertThat(reprocessamento.reprocessar()).isEqualTo(1);
        // Como um operador que roda o reprocessamento de novo depois de voltar o grupo.
        voltarGrupoDeReprocessamentoPara(naDlq);
        assertThat(reprocessamento.reprocessar()).isEqualTo(1);

        // O evento foi entregue de novo nas duas vezes, e o dedup por ce_id descartou a segunda.
        Awaitility.await().atMost(PRAZO).untilAsserted(() ->
                verify(eventosProcessados, times(EXECUCOES_ATE_A_DLQ + 2)).registrarSeNovo("evt-reprocessa-002"));
        Awaitility.await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(banco.eventosProcessados()).isEqualTo(1);
            assertThat(banco.analises()).isEqualTo(1);
        });
    }

    @Test
    void registroQueVoltaAFalharRetornaADlqSemLoop() {
        doThrow(new DataAccessResourceFailureException("banco indisponivel")).when(analises).registrar(any());
        String solicitacaoId = UUID.randomUUID().toString();
        publicar("evt-reprocessa-003", solicitacaoId);
        lerDe(consumidorDoTopico);
        assertThat(lerDe(consumidorDaDlq)).isNotNull();

        assertThat(reprocessamento.reprocessar()).isEqualTo(1);

        ConsumerRecord<String, String> republicado = lerDe(consumidorDoTopico);
        ConsumerRecord<String, String> deVoltaNaDlq = lerDe(consumidorDaDlq);
        assertThat(deVoltaNaDlq).isNotNull();
        assertThat(deVoltaNaDlq.key()).isEqualTo(solicitacaoId);
        assertThat(cabecalho(deVoltaNaDlq, "ce_id")).isEqualTo("evt-reprocessa-003");
        // Um unico conjunto de cabecalhos de origem, apontando para o registro republicado.
        assertThat(deVoltaNaDlq.headers().headers(KafkaHeaders.DLT_ORIGINAL_OFFSET)).hasSize(1);
        assertThat(ByteBuffer.wrap(deVoltaNaDlq.headers()
                .lastHeader(KafkaHeaders.DLT_ORIGINAL_OFFSET).value()).getLong())
                .isEqualTo(republicado.offset());
        assertThat(cabecalho(deVoltaNaDlq, KafkaHeaders.DLT_ORIGINAL_TOPIC)).isEqualTo(SOLICITACAO);

        // Passou de novo pela retentativa completa, e nada o traz de volta sozinho.
        assertThat(lerDe(consumidorDaDlq, Duration.ofSeconds(3))).isNull();
        verify(analises, times(2 * EXECUCOES_ATE_A_DLQ)).registrar(any());
        assertThat(banco.analises()).isZero();
    }

    @Test
    void falhaComumAosDoisGruposERepublicadaUmaUnicaVez() {
        doThrow(new DataAccessResourceFailureException("banco indisponivel")).when(analises).registrar(any());
        doThrow(new DataAccessResourceFailureException("banco indisponivel"))
                .when(fluxoDeSolicitacoes).agregar(any(), any());
        publicar("evt-reprocessa-004", UUID.randomUUID().toString());
        lerDe(consumidorDoTopico);
        assertThat(lerTodosDe(consumidorDaDlq, Duration.ofSeconds(8))).hasSize(2);

        doCallRealMethod().when(analises).registrar(any());
        doCallRealMethod().when(fluxoDeSolicitacoes).agregar(any(), any());
        assertThat(reprocessamento.reprocessar()).isEqualTo(1);

        // Uma unica republicacao, lida pelos dois grupos.
        assertThat(lerTodosDe(consumidorDoTopico, Duration.ofSeconds(3))).hasSize(1);
        Awaitility.await().atMost(PRAZO).untilAsserted(() -> {
            assertThat(banco.analises()).isEqualTo(1);
            verify(fluxoDeSolicitacoes, times(EXECUCOES_ATE_A_DLQ + 1)).agregar(any(), any());
        });
    }

    @Test
    void falhaPermanenteFicaNaDlqSemSerRepublicada() {
        publicador.send(ApoioKafka.registro(SOLICITACAO, UUID.randomUUID().toString(), "evt-reprocessa-005",
                "/credito/solicitacoes", "{ isso nao e json valido"));
        publicador.flush();
        lerDe(consumidorDoTopico);
        assertThat(lerTodosDe(consumidorDaDlq, Duration.ofSeconds(8))).hasSize(2).allSatisfy(registro ->
                assertThat(cabecalho(registro, "classificacao")).isEqualTo("PERMANENTE"));

        assertThat(reprocessamento.reprocessar()).isZero();

        assertThat(lerDe(consumidorDoTopico, Duration.ofSeconds(3))).isNull();
    }

    @Test
    void desfechoDaSagaQueFoiParaADlqEAplicadoPeloReprocessamento() {
        // LimiteDeCreditoReservado antes da solicitacao: a analise ainda nao existe, as
        // tentativas se esgotam e o evento vai para a DLQ da saga.
        String solicitacaoId = UUID.randomUUID().toString();
        publicador.send(ApoioKafka.registro(LIMITE_RESERVADO, solicitacaoId, "evt-reserva-206", "/credito/limites", """
                {"eventoId":"evt-reserva-206","solicitacaoId":"%s","clienteId":"cli-ficticio-001",
                 "valorReservado":15000.00,"limiteDisponivel":5000.00,"dataReserva":"2026-10-25T09:31:00-03:00"}
                """.formatted(solicitacaoId)));
        publicador.flush();
        ConsumerRecord<String, String> naDlq = lerDe(consumidorDaDlqDoReservado);
        assertThat(naDlq).isNotNull();
        assertThat(cabecalho(naDlq, "classificacao")).isEqualTo("TRANSITORIA");

        publicar("evt-reprocessa-006", solicitacaoId);
        Awaitility.await().atMost(PRAZO).untilAsserted(() ->
                assertThat(banco.status(solicitacaoId)).isEqualTo("PENDENTE"));

        assertThat(reprocessamento.reprocessar()).isEqualTo(1);

        Awaitility.await().atMost(PRAZO).untilAsserted(() ->
                assertThat(banco.status(solicitacaoId)).isEqualTo("RESERVADA"));
    }

    private void voltarGrupoDeReprocessamentoPara(ConsumerRecord<String, String> naDlq) {
        try (var grupo = ApoioKafka.consumidor(servidores, GRUPO_REPROCESSAMENTO)) {
            grupo.commitSync(Map.of(new TopicPartition(naDlq.topic(), naDlq.partition()),
                    new OffsetAndMetadata(naDlq.offset())));
        }
    }

    private void publicar(String eventoId, String solicitacaoId) {
        publicador.send(ApoioKafka.registro(SOLICITACAO, solicitacaoId, eventoId, "/credito/solicitacoes",
                ApoioKafka.solicitacaoJson(solicitacaoId, "15000.00", "2026-10-25T09:30:00-03:00")));
        publicador.flush();
    }
}
