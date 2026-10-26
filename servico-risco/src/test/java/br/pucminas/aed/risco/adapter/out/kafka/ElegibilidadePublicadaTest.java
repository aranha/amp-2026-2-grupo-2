package br.pucminas.aed.risco.adapter.out.kafka;

import static br.pucminas.aed.risco.ApoioKafka.ELEGIBILIDADE;
import static br.pucminas.aed.risco.ApoioKafka.LIMITE_RESERVADO;
import static br.pucminas.aed.risco.ApoioKafka.RESERVA_CANCELADA;
import static br.pucminas.aed.risco.ApoioKafka.SOLICITACAO;
import static br.pucminas.aed.risco.ApoioKafka.SOLICITACAO_LEGADO;
import static br.pucminas.aed.risco.ApoioKafka.cabecalhos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.pucminas.aed.risco.ApoioKafka;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * O caminho inteiro dentro do servico: solicitacao entra pelo Kafka, o parecer sai no
 * {@code risco.elegibilidade.v1} com o envelope que Credito e Antifraude esperam, e a mesma
 * analise responde no HTTP.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = ApoioKafka.PARTICOES,
        topics = {SOLICITACAO, SOLICITACAO_LEGADO, LIMITE_RESERVADO, RESERVA_CANCELADA, ELEGIBILIDADE})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.datasource.url=jdbc:h2:mem:risco-elegibilidade;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.kafka.consumer.auto-offset-reset=earliest"})
class ElegibilidadePublicadaTest {

    @Autowired private MockMvc http;
    @Autowired private ObjectMapper json;
    @Autowired private KafkaListenerEndpointRegistry listeners;
    @Value("${spring.embedded.kafka.brokers}") private String servidores;
    private KafkaProducer<String, String> publicador;
    private KafkaConsumer<String, String> leitorDaElegibilidade;

    @BeforeEach
    void preparar() {
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
    void publicaOParecerComOEnvelopeCloudEventsDoContrato() throws Exception {
        String solicitacaoId = UUID.randomUUID().toString();

        ConsumerRecord<String, String> publicado = solicitarEsperarParecer(SOLICITACAO, solicitacaoId, "18500.00");
        JsonNode carga = json.readTree(publicado.value());

        assertThat(publicado.key()).isEqualTo(solicitacaoId);
        assertThat(cabecalhos(publicado))
                .containsEntry("ce_specversion", "1.0")
                .containsEntry("ce_source", "/risco/analises")
                .containsEntry("ce_type", "risco.elegibilidade.v1")
                .containsEntry("ce_datacontenttype", "application/json")
                .containsEntry("ce_id", carga.get("eventoId").asText())
                .containsEntry("ce_time", carga.get("dataAnalise").asText());
        assertThat(carga.get("solicitacaoId").asText()).isEqualTo(solicitacaoId);
        assertThat(carga.get("elegivel").isBoolean()).isTrue();
        assertThat(carga.get("elegivel").booleanValue()).isTrue();
        assertThat(carga.get("valorAprovado").isNumber()).isTrue();
        assertThat(carga.get("valorAprovado").decimalValue()).isEqualByComparingTo("18500.00");
        assertThat(carga.get("motivo").asText()).isEqualTo("Valor dentro do limite de concessão automática");
        assertThat(OffsetDateTime.parse(carga.get("dataAnalise").asText()).getOffset())
                .isEqualTo(ZoneOffset.of("-03:00"));
        // O Antifraude (Python) le ce_id e solicitacaoId como UUID.
        assertThat(UUID.fromString(carga.get("eventoId").asText())).isNotNull();

        http.perform(get("/v1/analises/{id}", solicitacaoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.solicitacaoId").value(solicitacaoId))
                .andExpect(jsonPath("$.elegivel").value(true))
                .andExpect(jsonPath("$.valorAprovado").value(18500.00))
                .andExpect(jsonPath("$.dataAnalise").value(carga.get("dataAnalise").asText()))
                .andExpect(jsonPath("$.situacaoDaReserva").value("PENDENTE"));
        http.perform(get("/v1/analises"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analises[?(@.solicitacaoId == '%s')]", solicitacaoId).exists());
    }

    @Test
    void parecerRecusadoSaiComElegivelFalsoEValorZero() throws Exception {
        String solicitacaoId = UUID.randomUUID().toString();

        JsonNode carga = json.readTree(solicitarEsperarParecer(SOLICITACAO, solicitacaoId, "300000.00").value());

        assertThat(carga.get("elegivel").booleanValue()).isFalse();
        assertThat(carga.get("valorAprovado").isNumber()).isTrue();
        assertThat(carga.get("valorAprovado").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(carga.get("motivo").asText()).isNotBlank();
    }

    @Test
    void solicitacaoNoTopicoHerdadoTambemGeraParecer() throws Exception {
        String solicitacaoId = UUID.randomUUID().toString();

        JsonNode carga = json.readTree(solicitarEsperarParecer(SOLICITACAO_LEGADO, solicitacaoId, "150000.00").value());

        assertThat(carga.get("solicitacaoId").asText()).isEqualTo(solicitacaoId);
        assertThat(carga.get("valorAprovado").decimalValue()).isEqualByComparingTo("100000.00");
    }

    private ConsumerRecord<String, String> solicitarEsperarParecer(String topico, String solicitacaoId, String valor) {
        publicador.send(ApoioKafka.registro(topico, solicitacaoId, UUID.randomUUID().toString(),
                "/credito/solicitacoes", ApoioKafka.solicitacaoJson(solicitacaoId, valor, "2026-10-25T09:59:00-03:00")));
        publicador.flush();
        ConsumerRecord<String, String> publicado = ApoioKafka.lerDe(leitorDaElegibilidade);
        assertThat(publicado).as("parecer publicado em risco.elegibilidade.v1").isNotNull();
        return publicado;
    }
}
