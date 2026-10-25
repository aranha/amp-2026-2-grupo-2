package br.pucminas.aed.risco.adapter.out.jdbc;

import br.pucminas.aed.risco.application.port.out.AnalisesDeCredito;
import br.pucminas.aed.risco.domain.AnaliseDeCredito;
import br.pucminas.aed.risco.domain.Parecer;
import br.pucminas.aed.risco.domain.SituacaoDaReserva;
import br.pucminas.aed.risco.domain.Solicitacao;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Adaptador de saida do repositorio, no schema {@code risco}, pelo usuario {@code risco_user}. */
@Repository
public class JdbcAnalisesDeCredito implements AnalisesDeCredito {

    private static final ZoneOffset OFFSET_BRASILIA = ZoneOffset.of("-03:00");
    private static final String COLUNAS = """
            solicitacao_id, cliente_id, valor_solicitado, solicitada_em,
            elegivel, valor_aprovado, motivo, analisada_em, status
            """;

    private final JdbcTemplate bancoDeDados;

    public JdbcAnalisesDeCredito(JdbcTemplate bancoDeDados) {
        this.bancoDeDados = bancoDeDados;
    }

    /** A chave primaria e a solicitacao: o {@code on conflict} garante uma analise por solicitacao. */
    @Override
    public boolean registrar(AnaliseDeCredito analise) {
        Solicitacao solicitacao = analise.solicitacao();
        Parecer parecer = analise.parecer();
        int linhas = bancoDeDados.update(
                "insert into analise_credito (" + COLUNAS + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?) on conflict do nothing",
                solicitacao.id().toString(), solicitacao.clienteId(), solicitacao.valorSolicitado(),
                solicitacao.solicitadaEm(), parecer.elegivel(), parecer.valorAprovado(), parecer.motivo(),
                analise.analisadaEm(), analise.situacaoDaReserva().name());
        return linhas == 1;
    }

    @Override
    public Optional<AnaliseDeCredito> buscar(UUID solicitacaoId) {
        return bancoDeDados.query("select " + COLUNAS + " from analise_credito where solicitacao_id = ?",
                this::mapear, solicitacaoId.toString()).stream().findFirst();
    }

    @Override
    public boolean atualizarSituacao(UUID solicitacaoId, SituacaoDaReserva anterior, SituacaoDaReserva nova) {
        int linhas = bancoDeDados.update(
                "update analise_credito set status = ? where solicitacao_id = ? and status = ?",
                nova.name(), solicitacaoId.toString(), anterior.name());
        return linhas == 1;
    }

    @Override
    public List<AnaliseDeCredito> listarRecentes(int limite) {
        return bancoDeDados.query(
                "select " + COLUNAS + " from analise_credito order by analisada_em desc, solicitacao_id limit ?",
                this::mapear, limite);
    }

    private AnaliseDeCredito mapear(ResultSet linha, int numero) throws SQLException {
        var solicitacao = new Solicitacao(
                UUID.fromString(linha.getString("solicitacao_id")),
                linha.getString("cliente_id"),
                linha.getBigDecimal("valor_solicitado"),
                brasilia(linha.getObject("solicitada_em", OffsetDateTime.class)));
        var parecer = new Parecer(
                linha.getBoolean("elegivel"),
                linha.getBigDecimal("valor_aprovado"),
                linha.getString("motivo"));
        return AnaliseDeCredito.reconstituir(solicitacao, parecer,
                brasilia(linha.getObject("analisada_em", OffsetDateTime.class)),
                SituacaoDaReserva.valueOf(linha.getString("status")));
    }

    /** O banco devolve em UTC; o servico fala no horario de Brasilia, como os eventos. */
    private static OffsetDateTime brasilia(OffsetDateTime instante) {
        return instante.withOffsetSameInstant(OFFSET_BRASILIA);
    }
}
