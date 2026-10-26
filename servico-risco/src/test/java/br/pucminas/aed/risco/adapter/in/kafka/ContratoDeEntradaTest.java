package br.pucminas.aed.risco.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.pucminas.aed.risco.ContratoAsyncApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** O Risco consome {@code credito.solicitacao.v1} so pelos campos que o contrato garante. */
class ContratoDeEntradaTest {

    private static final String MENSAGEM = "SolicitacaoCriada";
    private static final List<String> CAMPOS_QUE_O_RISCO_LE =
            List.of("solicitacaoId", "clienteId", "valorSolicitado", "dataSolicitacao");

    private final ContratoAsyncApi contrato = ContratoAsyncApi.carregar();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void assinaOTopicoDoContrato() {
        assertThat(contrato.topico("credito_solicitacao_v1")).isEqualTo("credito.solicitacao.v1");
    }

    @Test
    void soDependeDeCampoObrigatorio() {
        assertThat(contrato.camposObrigatorios(MENSAGEM)).containsAll(CAMPOS_QUE_O_RISCO_LE);
    }

    @Test
    void entendeUmaCargaMontadaSoComOsObrigatoriosDoContrato() throws Exception {
        ObjectNode carga = json.createObjectNode();
        UUID solicitacaoId = UUID.randomUUID();
        for (String campo : contrato.camposObrigatorios(MENSAGEM)) {
            String formato = contrato.formato(MENSAGEM, campo);
            switch (contrato.tipo(MENSAGEM, campo)) {
                case "number" -> carga.put(campo, new BigDecimal("18500.10"));
                case "string" -> carga.put(campo,
                        "uuid".equals(formato) ? solicitacaoId.toString()
                                : "date-time".equals(formato) ? "2026-10-25T09:59:00-03:00" : "valor-" + campo);
                default -> throw new AssertionError("tipo nao previsto no teste: " + campo);
            }
        }

        var solicitacao = CargaDoEvento.solicitacao(json, json.writeValueAsString(carga));

        assertThat(solicitacao.id()).isEqualTo(solicitacaoId);
        assertThat(solicitacao.valorSolicitado()).isEqualTo(new BigDecimal("18500.10"));
        assertThat(solicitacao.solicitadaEm()).isEqualTo(OffsetDateTime.parse("2026-10-25T09:59:00-03:00"));
    }

    @Test
    void valorMonetarioNaoPassaPorDouble() throws Exception {
        // 19 digitos significativos: um double arredondaria para 12345678901234568.
        var solicitacao = CargaDoEvento.solicitacao(json, """
                {"solicitacaoId":"%s","clienteId":"c","valorSolicitado":12345678901234567.89,
                 "dataSolicitacao":"2026-10-25T09:59:00-03:00"}""".formatted(UUID.randomUUID()));

        assertThat(solicitacao.valorSolicitado()).isEqualTo(new BigDecimal("12345678901234567.89"));
    }

    @Test
    void campoForaDoFormatoEEntradaInvalida() {
        assertThatThrownBy(() -> CargaDoEvento.solicitacao(json, """
                {"solicitacaoId":"sol-1","clienteId":"c","valorSolicitado":10,"dataSolicitacao":"2026-10-25T09:59:00-03:00"}"""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("solicitacaoId");
        assertThatThrownBy(() -> CargaDoEvento.solicitacao(json, """
                {"solicitacaoId":"%s","clienteId":"c","valorSolicitado":"10","dataSolicitacao":"2026-10-25T09:59:00-03:00"}"""
                .formatted(UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("valorSolicitado");
        assertThatThrownBy(() -> CargaDoEvento.solicitacao(json, """
                {"solicitacaoId":"%s","clienteId":"c","valorSolicitado":10,"dataSolicitacao":"25/10/2026"}"""
                .formatted(UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dataSolicitacao");
        assertThatThrownBy(() -> CargaDoEvento.solicitacao(json, "[]"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
