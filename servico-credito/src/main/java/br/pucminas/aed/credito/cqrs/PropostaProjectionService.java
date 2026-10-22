package br.pucminas.aed.credito.cqrs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Atualizacoes atomicas + deduplicacao por (topico, eventoId).
 * Eventos fora de ordem nao substituem informacoes mais recentes do mesmo campo.
 */
@Service
public class PropostaProjectionService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public PropostaProjectionService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc; this.json = json;
    }
    @Transactional
    public void aplicar(String topico, String payload) throws Exception {
        JsonNode evento = json.readTree(payload);
        String id = obrigatorio(evento,"solicitacaoId");
        String eventoId = obrigatorio(evento,"eventoId");
        String dataCampo = topico.equals("contratacao.contrato.v1") ? "dataContrato" : "dataAnalise";
        OffsetDateTime data = OffsetDateTime.parse(obrigatorio(evento,dataCampo));
        if (!topico.equals("risco.elegibilidade.v1") &&
            !topico.equals("antifraude.analise.v1") &&
            !topico.equals("contratacao.contrato.v1")) throw new IllegalArgumentException("Topico invalido");
        int novo = jdbc.update("""
            INSERT INTO credito.proposta_eventos_processados(topico,evento_id)
            VALUES (?,?) ON CONFLICT DO NOTHING
            """,topico,eventoId);
        if (novo == 0) return;
        jdbc.update("INSERT INTO credito.proposta_view(solicitacao_id) VALUES (?) ON CONFLICT DO NOTHING", id);
        switch (topico) {
            case "risco.elegibilidade.v1" -> jdbc.update("""
                UPDATE credito.proposta_view SET elegivel=?, valor_aprovado=?, elegibilidade_em=?, atualizada_em=now()
                WHERE solicitacao_id=? AND (elegibilidade_em IS NULL OR elegibilidade_em <= ?)
                """, booleano(evento,"elegivel"), decimal(evento,"valorAprovado"), data, id, data);
            case "antifraude.analise.v1" -> jdbc.update("""
                UPDATE credito.proposta_view SET antifraude_aprovada=?, score_antifraude=?, antifraude_em=?, atualizada_em=now()
                WHERE solicitacao_id=? AND (antifraude_em IS NULL OR antifraude_em <= ?)
                """, booleano(evento,"aprovada"), inteiro(evento,"score"), data, id, data);
            case "contratacao.contrato.v1" -> jdbc.update("""
                UPDATE credito.proposta_view SET contrato_id=?, proposta_id=?, status_contrato=?, contrato_em=?, atualizada_em=now()
                WHERE solicitacao_id=? AND (contrato_em IS NULL OR contrato_em <= ?)
                """, obrigatorio(evento,"contratoId"), obrigatorio(evento,"propostaId"),
                obrigatorio(evento,"status"), data, id, data);
            default -> throw new IllegalStateException();
        }
    }
    private static String obrigatorio(JsonNode node,String campo) {
        JsonNode valor=node.path(campo);
        if (valor.isMissingNode() || valor.isNull() || valor.asText().isBlank())
            throw new IllegalArgumentException("Campo obrigatorio: " + campo);
        return valor.asText();
    }
    private static boolean booleano(JsonNode node,String campo) {
        if (!node.path(campo).isBoolean()) throw new IllegalArgumentException("Booleano obrigatorio: "+campo);
        return node.path(campo).booleanValue();
    }
    private static Integer inteiro(JsonNode node,String campo) {
        if (!node.path(campo).isIntegralNumber()) throw new IllegalArgumentException("Inteiro obrigatorio: "+campo);
        return node.path(campo).intValue();
    }
    private static java.math.BigDecimal decimal(JsonNode node,String campo) {
        if (!node.path(campo).isNumber()) throw new IllegalArgumentException("Numero obrigatorio: "+campo);
        return node.path(campo).decimalValue();
    }
}
