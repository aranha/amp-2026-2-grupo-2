package br.pucminas.aed.credito.adapter.out;

import br.pucminas.aed.credito.cqrs.PropostaView;
import br.pucminas.aed.credito.cqrs.PropostaViewRepository;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPropostaViewRepository implements PropostaViewRepository {
    private final JdbcTemplate jdbc;
    public JdbcPropostaViewRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Optional<PropostaView> buscarPorSolicitacaoId(String id) {
        return jdbc.query("""
            SELECT solicitacao_id, elegivel, valor_aprovado, elegibilidade_em,
              antifraude_aprovada, score_antifraude, antifraude_em,
              contrato_id, proposta_id, status_contrato, contrato_em, atualizada_em
            FROM credito.proposta_view WHERE solicitacao_id = ?
            """, (rs, n) -> new PropostaView(
                rs.getString("solicitacao_id"),
                (Boolean)rs.getObject("elegivel"), rs.getBigDecimal("valor_aprovado"),
                rs.getObject("elegibilidade_em", java.time.OffsetDateTime.class),
                (Boolean)rs.getObject("antifraude_aprovada"),
                (Integer)rs.getObject("score_antifraude"),
                rs.getObject("antifraude_em", java.time.OffsetDateTime.class),
                rs.getString("contrato_id"), rs.getString("proposta_id"),
                rs.getString("status_contrato"),
                rs.getObject("contrato_em", java.time.OffsetDateTime.class),
                rs.getObject("atualizada_em", java.time.OffsetDateTime.class)
            ), id).stream().findFirst();
    }
}
