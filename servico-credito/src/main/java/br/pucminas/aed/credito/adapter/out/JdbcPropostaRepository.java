package br.pucminas.aed.credito.adapter.out;

import br.pucminas.aed.credito.application.PersistenciaProposta;
import br.pucminas.aed.credito.domain.model.Dinheiro;
import br.pucminas.aed.credito.domain.model.SolicitacaoDeCredito;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcPropostaRepository implements PersistenciaProposta {
    private final JdbcTemplate jdbc;

    @Override public Optional<SolicitacaoDeCredito> buscarPorId(String id) {
        List<SolicitacaoDeCredito> linhas = jdbc.query("""
            select id, valor, estado from credito.solicitacao where id = ?
            """, (rs, index) -> SolicitacaoDeCredito.reconstituir(
                rs.getString("id"), new Dinheiro(rs.getBigDecimal("valor")),
                SolicitacaoDeCredito.Estado.valueOf(rs.getString("estado"))), id);
        return linhas.stream().findFirst();
    }

    @Override public void salvar(SolicitacaoDeCredito proposta) {
        jdbc.update("""
            insert into credito.solicitacao (id, valor, estado) values (?, ?, ?)
            """, proposta.id(), proposta.valor().valor(), proposta.estado().name());
    }

    @Override public boolean marcarAceitaSeCriada(String id, OffsetDateTime dataAceite) {
        return jdbc.update("""
            update credito.solicitacao set estado = 'ACEITA', aceita_em = ?
            where id = ? and estado = 'CRIADA'
            """, dataAceite, id) == 1;
    }
}
