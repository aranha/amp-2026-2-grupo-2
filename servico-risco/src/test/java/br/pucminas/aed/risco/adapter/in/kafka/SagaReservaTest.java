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
        "spring.datasource.url=jdbc:h2:mem:risco-saga;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "app.kafka.retentativa.tentativas=4",
        "app.kafka.retentativa.intervalo-inicial-ms=500",
        "app.kafka.retentativa.intervalo-maximo-ms=2000"
})
class SagaReservaTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private KafkaListenerEndpointRegistry listeners;
    @Value("${spring.embedded.kafka.brokers}") private String servidores;
    private BancoDeTeste banco;
    private KafkaProducer<String, String> publicador;

    @BeforeEach
    void preparar() {
        banco = new BancoDeTeste(jdbc);
        banco.limpar();
        ApoioKafka.aguardarConsumidores(listeners);
        publicador = ApoioKafka.publicador(servidores);
    }

    @AfterEach
    void encerrar() {
        publicador.close();
    }

    @Test
    void estadoFinalDaSagaFicaObservavelNaAnaliseDeCredito() {
        String solicitacaoId = UUID.randomUUID().toString();
        publicarSolicitado("evt-sol-201", solicitacaoId);
        aguardarStatus(solicitacaoId, "PENDENTE");

        publicarReservado("evt-res-201", solicitacaoId);
        aguardarStatus(solicitacaoId, "RESERVADA");

        publicarCancelado("evt-can-201", solicitacaoId);
        aguardarStatus(solicitacaoId, "CANCELADA");
    }

    @Test
    void cancelamentoProcessadoAntesDaReservaNaoEDesfeitoPelaReservaAtrasada() {
        String solicitacaoId = UUID.randomUUID().toString();
        publicarSolicitado("evt-sol-202", solicitacaoId);
        aguardarStatus(solicitacaoId, "PENDENTE");

        publicarCancelado("evt-can-202", solicitacaoId);
        aguardarStatus(solicitacaoId, "CANCELADA");

        publicarReservado("evt-res-202", solicitacaoId);
        Awaitility.await().atMost(PRAZO).until(() -> banco.eventosProcessados() == 3);
        assertThat(banco.status(solicitacaoId)).isEqualTo("CANCELADA");
    }

    @Test
    void reservaQueChegaAntesDaAnaliseEAplicadaPelaRetentativa() {
        String solicitacaoId = UUID.randomUUID().toString();
        publicarReservado("evt-res-203", solicitacaoId);
        publicador.flush();
        sleep(Duration.ofMillis(700));
        publicarSolicitado("evt-sol-203", solicitacaoId);

        aguardarStatus(solicitacaoId, "RESERVADA");
    }

    @Test
    void mesmoEventoDaSagaEntregueDuasVezesAplicaUmaSo() {
        String solicitacaoId = UUID.randomUUID().toString();
        publicarSolicitado("evt-sol-204", solicitacaoId);
        aguardarStatus(solicitacaoId, "PENDENTE");

        publicarReservado("evt-res-204", solicitacaoId);
        publicarReservado("evt-res-204", solicitacaoId);

        aguardarStatus(solicitacaoId, "RESERVADA");
        Awaitility.await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(6))
                .untilAsserted(() -> assertThat(banco.eventosProcessados()).isEqualTo(2));
    }

    private void aguardarStatus(String solicitacaoId, String esperado) {
        publicador.flush();
        Awaitility.await().atMost(PRAZO).untilAsserted(() ->
                assertThat(banco.status(solicitacaoId)).isEqualTo(esperado));
    }

    private void publicarSolicitado(String eventoId, String solicitacaoId) {
        publicador.send(ApoioKafka.registro(SOLICITACAO, solicitacaoId, eventoId, "/credito/solicitacoes",
                ApoioKafka.solicitacaoJson(solicitacaoId, "3000.00", "2026-09-27T10:00:00-03:00")));
    }

    private void publicarReservado(String eventoId, String solicitacaoId) {
        publicador.send(ApoioKafka.registro(LIMITE_RESERVADO, solicitacaoId, eventoId, "/credito/limites", """
                {"eventoId":"%s","solicitacaoId":"%s","clienteId":"cli-ficticio-001","valorReservado":3000.00,
                 "limiteDisponivel":7000.00,"dataReserva":"2026-09-27T10:01:00-03:00"}
                """.formatted(eventoId, solicitacaoId)));
    }

    private void publicarCancelado(String eventoId, String solicitacaoId) {
        publicador.send(ApoioKafka.registro(RESERVA_CANCELADA, solicitacaoId, eventoId, "/credito/limites", """
                {"eventoId":"%s","eventoOrigemId":"evt-recusa","solicitacaoId":"%s","clienteId":"cli-ficticio-001",
                 "valorDevolvido":3000.00,"limiteDisponivel":10000.00,"motivo":"PROPOSTA_RECUSADA",
                 "dataCancelamento":"2026-09-27T10:02:00-03:00"}
                """.formatted(eventoId, solicitacaoId)));
    }

    private static void sleep(Duration duracao) {
        try {
            Thread.sleep(duracao.toMillis());
        } catch (InterruptedException interrompido) {
            Thread.currentThread().interrupt();
        }
    }
}
