package br.pucminas.aed.risco.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.pucminas.aed.risco.ContratoAsyncApi;
import br.pucminas.aed.risco.domain.ElegibilidadeAvaliada;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

/** O que o Risco publica bate com o que o asyncapi.yaml promete a Credito e Antifraude. */
class ContratoDeSaidaTest {

    private static final String MENSAGEM = "ElegibilidadeAvaliada";

    private final ContratoAsyncApi contrato = ContratoAsyncApi.carregar();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void publicaNoTopicoDoContratoComOTipoIgualAoTopico() {
        assertThat(contrato.topico("risco_elegibilidade_v1")).isEqualTo(KafkaPublicadorDeElegibilidade.TIPO);
        assertThat(publicar().topic()).isEqualTo(KafkaPublicadorDeElegibilidade.TIPO);
    }

    @Test
    void aCargaTemTodoCampoObrigatorioNoTipoDoContrato() throws Exception {
        JsonNode carga = json.readTree(publicar().value());

        for (String campo : contrato.camposObrigatorios(MENSAGEM)) {
            assertThat(carga.has(campo)).as("campo obrigatorio %s", campo).isTrue();
            JsonNode valor = carga.get(campo);
            switch (contrato.tipo(MENSAGEM, campo)) {
                case "string" -> assertThat(valor.isTextual()).as("%s e string", campo).isTrue();
                case "boolean" -> assertThat(valor.isBoolean()).as("%s e boolean", campo).isTrue();
                case "number" -> assertThat(valor.isNumber()).as("%s e number", campo).isTrue();
                default -> throw new AssertionError("tipo nao previsto no teste: " + campo);
            }
            if ("uuid".equals(contrato.formato(MENSAGEM, campo))) {
                assertThat(UUID.fromString(valor.asText()).toString()).isEqualTo(valor.asText());
            }
            if ("date-time".equals(contrato.formato(MENSAGEM, campo))) {
                assertThat(OffsetDateTime.parse(valor.asText())).isNotNull();
            }
        }
    }

    @Test
    void oEnvelopeTemTodoCabecalhoCloudEventsObrigatorio() {
        Map<String, String> cabecalhos = new HashMap<>();
        publicar().headers().forEach(cabecalho ->
                cabecalhos.put(cabecalho.key(), new String(cabecalho.value(), StandardCharsets.UTF_8)));

        assertThat(cabecalhos).containsKeys(contrato.cabecalhosObrigatorios().toArray(String[]::new));
        assertThat(cabecalhos).containsEntry("ce_specversion", "1.0")
                .containsEntry("ce_datacontenttype", "application/json");
    }

    @SuppressWarnings("unchecked")
    private ProducerRecord<String, String> publicar() {
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        when(template.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        new KafkaPublicadorDeElegibilidade(template, json, KafkaPublicadorDeElegibilidade.TIPO)
                .publicar(new ElegibilidadeAvaliada(UUID.randomUUID(), UUID.randomUUID(), true,
                        new BigDecimal("18500.00"), "Valor dentro do limite de concessão automática",
                        OffsetDateTime.parse("2026-10-25T10:00:00-03:00")));
        ArgumentCaptor<ProducerRecord<String, String>> registro = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(registro.capture());
        return registro.getValue();
    }
}
