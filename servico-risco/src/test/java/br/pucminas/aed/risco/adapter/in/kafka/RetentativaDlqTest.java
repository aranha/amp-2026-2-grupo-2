package br.pucminas.aed.risco.adapter.in.kafka;

import static br.pucminas.aed.risco.ApoioKafka.ELEGIBILIDADE;
import static br.pucminas.aed.risco.ApoioKafka.LIMITE_RESERVADO;
import static br.pucminas.aed.risco.ApoioKafka.PRAZO;
import static br.pucminas.aed.risco.ApoioKafka.RESERVA_CANCELADA;
import static br.pucminas.aed.risco.ApoioKafka.SOLICITACAO;
import static br.pucminas.aed.risco.ApoioKafka.SOLICITACAO_LEGADO;
import static br.pucminas.aed.risco.ApoioKafka.cabecalho;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import br.pucminas.aed.risco.ApoioKafka;
import br.pucminas.aed.risco.BancoDeTeste;
import br.pucminas.aed.risco.adapter.out.jdbc.JdbcAnalisesDeCredito;
import br.pucminas.aed.risco.adapter.out.kafka.KafkaPublicadorDeElegibilidade;
import br.pucminas.aed.risco.adapter.out.kafka.PublicacaoFalhouException;
import br.pucminas.aed.risco.domain.ElegibilidadeAvaliada;
import br.pucminas.aed.risco.domain.SolicitacaoInvalidaException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
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
        SOLICITACAO, SOLICITACAO + ".dlq", SOLICITACAO_LEGADO, LIMITE_RESERVADO, RESERVA_CANCELADA, ELEGIBILIDADE})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.datasource.url=jdbc:h2:mem:risco-dlq;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "app.kafka.retentativa.tentativas=2",
        "app.kafka.retentativa.intervalo-inicial-ms=50",
        "app.kafka.retentativa.intervalo-maximo-ms=100"
})
class RetentativaDlqTest {
    private static final String TOPICO_DLQ = SOLICITACAO + ".dlq";
    private static final int TENTATIVAS = 2;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private KafkaListenerEndpointRegistry listeners;
    @Value("${spring.embedded.kafka.brokers}") private String servidores;

    @SpyBean private JdbcAnalisesDeCredito analises;
    @SpyBean private KafkaPublicadorDeElegibilidade publicadorDeElegibilidade;

    private BancoDeTeste banco;
    private KafkaProducer<String, String> publicador;
    private KafkaConsumer<String, String> consumidorDaDlq;
    private KafkaConsumer<String, String> leitorDaElegibilidade;

    @BeforeEach
    void preparar() {
        banco = new BancoDeTeste(jdbc);
        banco.limpar();
        ApoioKafka.aguardarConsumidores(listeners);
        publicador = ApoioKafka.publicador(servidores);
        consumidorDaDlq = ApoioKafka.leitorNoFim(servidores, TOPICO_DLQ);
        leitorDaElegibilidade = ApoioKafka.leitorNoFim(servidores, ELEGIBILIDADE);
    }

    @AfterEach
    void encerrar() {
        publicador.close();
        consumidorDaDlq.close();
        leitorDaElegibilidade.close();
    }

    @Test
    void falhaTransitoriaQueSeRecuperaNaoVaiParaADlq() {
        doThrow(new DataAccessResourceFailureException("banco indisponivel"))
                .doCallRealMethod()
                .when(analises).registrar(any());

        publicar("evt-transitorio-recupera", UUID.randomUUID().toString(), "15000.00");

        Awaitility.await().atMost(PRAZO).untilAsserted(() -> {
            verify(analises, times(2)).registrar(any());
            assertThat(banco.analises()).isEqualTo(1);
        });
        assertThat(ApoioKafka.lerDe(consumidorDaDlq, Duration.ofSeconds(3))).isNull();
    }

    @Test
    void falhaTransitoriaEsgotaAsTentativasEVaiParaADlq() {
        doThrow(new DataAccessResourceFailureException("banco indisponivel"))
                .when(analises).registrar(any());
        String solicitacaoId = UUID.randomUUID().toString();

        String json = publicar("evt-transitorio-esgota", solicitacaoId, "15000.00");

        ConsumerRecord<String, String> naDlq = ApoioKafka.lerDe(consumidorDaDlq);
        assertThat(naDlq).isNotNull();
        assertThat(naDlq.key()).isEqualTo(solicitacaoId);
        assertThat(cabecalho(naDlq, KafkaHeaders.DLT_ORIGINAL_TOPIC)).isEqualTo(SOLICITACAO);
        assertThat(cabecalho(naDlq, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN))
                .isEqualTo(DataAccessResourceFailureException.class.getName());
        assertThat(cabecalho(naDlq, "classificacao")).isEqualTo("TRANSITORIA");
        assertThat(cabecalho(naDlq, "ce_id")).isEqualTo("evt-transitorio-esgota");
        assertThat(cabecalho(naDlq, "ce_time")).isEqualTo("2026-10-25T10:00:00-03:00");
        // O listener le texto: a DLQ guarda os bytes que chegaram, sem reserializar nada.
        assertThat(naDlq.value()).isEqualTo(json);

        verify(analises, times(TENTATIVAS + 1)).registrar(any());
        // A transacao desfez tambem o registro do ce_id: o reprocessamento ainda vai processar.
        assertThat(banco.analises()).isZero();
        assertThat(banco.eventosProcessados()).isZero();
        confirmarOffsetDoRegistroRecuperado(naDlq);
    }

    @Test
    void payloadMalformadoVaiDiretoParaADlqComOConteudoOriginal() {
        String payloadInvalido = "{ isso nao e json valido";
        publicador.send(ApoioKafka.registro(SOLICITACAO, UUID.randomUUID().toString(), "evt-payload-invalido",
                "/credito/solicitacoes", payloadInvalido));
        publicador.flush();

        // Os dois grupos de consumo leem o mesmo topico, entao os dois rejeitam o payload e cada
        // um gera o seu proprio registro na DLQ. Sao distinguiveis pelo grupo de origem.
        var naDlq = ApoioKafka.lerTodosDe(consumidorDaDlq, Duration.ofSeconds(8));
        assertThat(naDlq).hasSize(2);
        assertThat(naDlq).allSatisfy(registro -> {
            assertThat(registro.value()).isEqualTo(payloadInvalido);
            assertThat(cabecalho(registro, KafkaHeaders.DLT_ORIGINAL_TOPIC)).isEqualTo(SOLICITACAO);
            assertThat(cabecalho(registro, "classificacao")).isEqualTo("PERMANENTE");
        });
        assertThat(naDlq)
                .extracting(registro -> cabecalho(registro, KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP))
                .containsExactlyInAnyOrder("risco-credito-v1", "risco-fluxo-creditos-v1");

        verify(analises, never()).registrar(any());
        assertThat(banco.analises()).isZero();
    }

    @Test
    void cabecalhoObrigatorioAusenteVaiDiretoParaADlqSemRetentar() {
        String solicitacaoId = UUID.randomUUID().toString();
        var registro = new ProducerRecord<String, String>(SOLICITACAO, solicitacaoId,
                ApoioKafka.solicitacaoJson(solicitacaoId, "15000.00", "2026-10-25T09:30:00-03:00"));
        registro.headers().add("ce_type", SOLICITACAO.getBytes());
        publicador.send(registro);
        publicador.flush();

        ConsumerRecord<String, String> naDlq = ApoioKafka.lerDe(consumidorDaDlq);
        assertThat(naDlq).isNotNull();
        assertThat(cabecalho(naDlq, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN))
                .isEqualTo(IllegalArgumentException.class.getName());

        verify(analises, never()).registrar(any());
        assertThat(banco.analises()).isZero();
    }

    @Test
    void solicitacaoQueODominioRecusaVaiDiretoParaADlq() {
        publicar("evt-valor-zerado", UUID.randomUUID().toString(), "0.00");

        var naDlq = ApoioKafka.lerTodosDe(consumidorDaDlq, Duration.ofSeconds(8));
        assertThat(naDlq).hasSize(2).allSatisfy(registro -> {
            assertThat(cabecalho(registro, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN))
                    .isEqualTo(SolicitacaoInvalidaException.class.getName());
            assertThat(cabecalho(registro, "classificacao")).isEqualTo("PERMANENTE");
        });
        verify(analises, never()).registrar(any());
    }

    @Test
    void falhaAoPublicarDesfazAAnaliseEARetentativaPublicaUmaVez() {
        doThrow(semConfirmacaoDoBroker())
                .doCallRealMethod()
                .when(publicadorDeElegibilidade).publicar(any());

        publicar("evt-publicacao-recupera", UUID.randomUUID().toString(), "15000.00");

        Awaitility.await().atMost(PRAZO).untilAsserted(() -> {
            verify(publicadorDeElegibilidade, times(2)).publicar(any());
            assertThat(banco.analises()).isEqualTo(1);
            assertThat(banco.eventosProcessados()).isEqualTo(1);
        });
        assertThat(ApoioKafka.lerTodosDe(leitorDaElegibilidade, Duration.ofSeconds(3))).hasSize(1);
        assertThat(ApoioKafka.lerDe(consumidorDaDlq, Duration.ofSeconds(1))).isNull();
    }

    @Test
    void publicacaoQueNuncaConfirmaVaiParaADlqSemDeixarAnaliseOrfa() {
        doThrow(semConfirmacaoDoBroker()).when(publicadorDeElegibilidade).publicar(any());

        publicar("evt-publicacao-esgota", UUID.randomUUID().toString(), "15000.00");

        ConsumerRecord<String, String> naDlq = ApoioKafka.lerDe(consumidorDaDlq);
        assertThat(naDlq).isNotNull();
        assertThat(cabecalho(naDlq, "classificacao")).isEqualTo("TRANSITORIA");
        assertThat(banco.analises()).isZero();
        assertThat(banco.eventosProcessados()).isZero();
    }

    private static PublicacaoFalhouException semConfirmacaoDoBroker() {
        var evento = new ElegibilidadeAvaliada(UUID.randomUUID(), UUID.randomUUID(), true, BigDecimal.TEN,
                "teste", OffsetDateTime.parse("2026-10-25T10:00:00-03:00"));
        return new PublicacaoFalhouException(evento, new TimeoutException("broker sem confirmar"));
    }

    /**
     * O registro recuperado precisa ter o offset confirmado, senao ele volta na proxima
     * atribuicao de particao e duplica na DLQ. Como o container usa ack manual e o listener
     * lancou excecao, quem confirma e o {@code setCommitRecovered(true)} do tratador de falha.
     */
    private void confirmarOffsetDoRegistroRecuperado(ConsumerRecord<String, String> naDlq) {
        var particao = new TopicPartition(
                SOLICITACAO, ByteBuffer.wrap(naDlq.headers()
                        .lastHeader(KafkaHeaders.DLT_ORIGINAL_PARTITION).value()).getInt());
        long offsetOriginal = ByteBuffer.wrap(naDlq.headers()
                .lastHeader(KafkaHeaders.DLT_ORIGINAL_OFFSET).value()).getLong();

        // committed() e uma consulta direta ao broker: nao entra no grupo nem provoca rebalanceamento.
        try (var leitor = ApoioKafka.consumidor(servidores, "risco-credito-v1")) {
            Awaitility.await().atMost(PRAZO).untilAsserted(() -> {
                var confirmado = leitor.committed(Set.of(particao)).get(particao);
                assertThat(confirmado).isNotNull();
                assertThat(confirmado.offset()).isGreaterThan(offsetOriginal);
            });
        }
    }

    private String publicar(String eventoId, String solicitacaoId, String valor) {
        String json = ApoioKafka.solicitacaoJson(solicitacaoId, valor, "2026-10-25T09:30:00-03:00");
        publicador.send(ApoioKafka.registro(SOLICITACAO, solicitacaoId, eventoId, "/credito/solicitacoes", json));
        publicador.flush();
        return json;
    }
}
