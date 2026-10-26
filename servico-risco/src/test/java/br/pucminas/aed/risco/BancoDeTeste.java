package br.pucminas.aed.risco;

import org.springframework.jdbc.core.JdbcTemplate;

/** Consultas so dos testes: o adaptador de producao nao ganha metodo que so teste usa. */
public final class BancoDeTeste {

    private final JdbcTemplate bancoDeDados;

    public BancoDeTeste(JdbcTemplate bancoDeDados) {
        this.bancoDeDados = bancoDeDados;
    }

    public int analises() {
        return bancoDeDados.queryForObject("select count(*) from analise_credito", Integer.class);
    }

    public int eventosProcessados() {
        return bancoDeDados.queryForObject("select count(*) from evento_processado", Integer.class);
    }

    public String status(String solicitacaoId) {
        return bancoDeDados.queryForList(
                "select status from analise_credito where solicitacao_id = ?", String.class, solicitacaoId)
                .stream().findFirst().orElse(null);
    }

    public void limpar() {
        bancoDeDados.update("delete from analise_credito");
        bancoDeDados.update("delete from evento_processado");
    }
}
