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
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

/** Os dois grupos sobre o mesmo topico: a analise e o volume por janela recebem cada evento. */
@SpringBootTest
@EmbeddedKafka(partitions = ApoioKafka.PARTICOES,
        topics = {SOLICITACAO, SOLICITACAO_LEGADO, LIMITE_RESERVADO, RESERVA_CANCELADA, ELEGIBILIDADE})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.datasource.url=jdbc:h2:mem:risco-fluxo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.kafka.consumer.auto-offset-reset=earliest"})
@ExtendWith(OutputCaptureExtension.class)
class FluxoDeSolicitacoesKafkaTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private KafkaListenerEndpointRegistry listeners;
    @Value("${spring.embedded.kafka.brokers}") private String servidores;
    private BancoDeTeste banco;
    private KafkaProducer<String, String> publicador;

    @BeforeEach
    void preparar() {
        banco = new BancoDeTeste(jdbc);
        ApoioKafka.aguardarConsumidores(listeners);
        publicador = ApoioKafka.publicador(servidores);
    }

    @AfterEach
    void encerrar() {
        publicador.close();
    }

    @Test
    void agregaValorSolicitadoPorJanelaDeCincoMinutos(CapturedOutput saida) {
        publicar("evt-fluxo-001", "1000.00", "2026-08-22T12:02:34-03:00");
        publicar("evt-fluxo-002", "2000.00", "2026-08-22T12:04:10-03:00");
        publicar("evt-fluxo-003", "5000.00", "2026-08-22T12:07:00-03:00");
        publicador.flush();

        Awaitility.await().atMost(PRAZO).untilAsserted(() -> {
            assertThat(saida)
                    .contains("Fluxo de credito solicitado | janela=2026-08-22T12:00-03:00 | quantidade=2 | totalSolicitado=3000.00")
                    .contains("Fluxo de credito solicitado | janela=2026-08-22T12:05-03:00 | quantidade=1 | totalSolicitado=5000.00");
            assertThat(banco.eventosProcessados()).isEqualTo(3);
            assertThat(banco.analises()).isEqualTo(3);
        });
    }

    private void publicar(String eventoId, String valor, String dataSolicitacao) {
        String solicitacaoId = UUID.randomUUID().toString();
        publicador.send(ApoioKafka.registro(SOLICITACAO, solicitacaoId, eventoId, "/credito/solicitacoes",
                ApoioKafka.solicitacaoJson(solicitacaoId, valor, dataSolicitacao)));
    }
}
