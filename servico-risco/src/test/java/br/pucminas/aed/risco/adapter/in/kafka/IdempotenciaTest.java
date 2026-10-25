package br.pucminas.aed.risco.adapter.in.kafka;

import static br.pucminas.aed.risco.ApoioKafka.ELEGIBILIDADE;
import static br.pucminas.aed.risco.ApoioKafka.LIMITE_RESERVADO;
import static br.pucminas.aed.risco.ApoioKafka.PRAZO;
import static br.pucminas.aed.risco.ApoioKafka.RESERVA_CANCELADA;
import static br.pucminas.aed.risco.ApoioKafka.SOLICITACAO;
import static br.pucminas.aed.risco.ApoioKafka.SOLICITACAO_LEGADO;
import static org.assertj.core.api.Assertions.assertThat;

import br.pucminas.aed.risco.ApoioKafka;
import br.pucminas.aed.risco.BancoDeTeste;
import java.time.Duration;
import java.util.UUID;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@EmbeddedKafka(partitions = ApoioKafka.PARTICOES,
        topics = {SOLICITACAO, SOLICITACAO_LEGADO, LIMITE_RESERVADO, RESERVA_CANCELADA, ELEGIBILIDADE})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.datasource.url=jdbc:h2:mem:risco;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
class IdempotenciaTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private KafkaListenerEndpointRegistry listeners;
    @Value("${spring.embedded.kafka.brokers}") private String servidores;
    private BancoDeTeste banco;
    private KafkaProducer<String, String> publicador;
    private KafkaConsumer<String, String> leitorDaElegibilidade;

    @BeforeEach
    void preparar() {
        banco = new BancoDeTeste(jdbc);
        banco.limpar();
        ApoioKafka.aguardarConsumidores(listeners);
        publicador = ApoioKafka.publicador(servidores);
        leitorDaElegibilidade = ApoioKafka.leitorNoFim(servidores, ELEGIBILIDADE);
    }

    @AfterEach
    void encerrar() {
        publicador.close();
        leitorDaElegibilidade.close();
    }

    @Test
    void mesmoEventoEntregueTresVezesProduzUmUnicoEfeito() {
        String solicitacaoId = UUID.randomUUID().toString();
        String json = ApoioKafka.solicitacaoJson(solicitacaoId, "15000.00", "2026-10-25T09:30:00-03:00");

        for (int entrega = 0; entrega < 3; entrega++) {
            publicador.send(ApoioKafka.registro(SOLICITACAO, solicitacaoId, "evt-reentrega-001",
                    "/credito/solicitacoes", json));
        }
        publicador.flush();

        Awaitility.await().atMost(PRAZO).untilAsserted(() -> {
            assertThat(banco.eventosProcessados()).isEqualTo(1);
            assertThat(banco.analises()).isEqualTo(1);
        });
        Awaitility.await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(banco.analises()).isEqualTo(1));
        assertThat(ApoioKafka.lerTodosDe(leitorDaElegibilidade, Duration.ofSeconds(3))).hasSize(1);
    }

    @Test
    void consumidorIgnoraCampoQueNaoDeclarou() {
        String solicitacaoId = UUID.randomUUID().toString();
        String json = """
                {"eventoId":"id-do-corpo","solicitacaoId":"%s","clienteId":"cli-ficticio-001",
                 "valorSolicitado":15000.00,"dataSolicitacao":"2026-10-25T09:30:00-03:00",
                 "canalOrigem":"CAMPO_DESCONHECIDO_PELO_CONSUMIDOR","campoNovoOpcional":{"versao":2}}
                """.formatted(solicitacaoId);

        publicador.send(ApoioKafka.registro(SOLICITACAO, solicitacaoId, "evt-tolerante-001",
                "/credito/solicitacoes", json));
        publicador.flush();

        Awaitility.await().atMost(PRAZO).untilAsserted(() -> {
            assertThat(banco.eventosProcessados()).isEqualTo(1);
            assertThat(banco.analises()).isEqualTo(1);
        });
    }

    @Test
    void mesmaSolicitacaoNoTopicoDoContratoENoHerdadoGeraUmParecerSo() {
        String solicitacaoId = UUID.randomUUID().toString();
        String json = ApoioKafka.solicitacaoJson(solicitacaoId, "15000.00", "2026-10-25T09:30:00-03:00");

        publicador.send(ApoioKafka.registro(SOLICITACAO, solicitacaoId, "evt-topico-novo",
                "/credito/solicitacoes", json));
        publicador.send(ApoioKafka.registro(SOLICITACAO_LEGADO, solicitacaoId, "evt-topico-herdado",
                "/credito/solicitacoes", json));
        publicador.flush();

        Awaitility.await().atMost(PRAZO).untilAsserted(() -> {
            assertThat(banco.eventosProcessados()).isEqualTo(2);
            assertThat(banco.analises()).isEqualTo(1);
        });
        assertThat(ApoioKafka.lerTodosDe(leitorDaElegibilidade, Duration.ofSeconds(3))).hasSize(1);
    }
}
