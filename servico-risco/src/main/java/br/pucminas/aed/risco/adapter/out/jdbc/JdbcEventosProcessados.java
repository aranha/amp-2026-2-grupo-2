package br.pucminas.aed.risco.adapter.out.jdbc;

import br.pucminas.aed.risco.application.port.out.EventosProcessados;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEventosProcessados implements EventosProcessados {

    private final JdbcTemplate bancoDeDados;

    public JdbcEventosProcessados(JdbcTemplate bancoDeDados) {
        this.bancoDeDados = bancoDeDados;
    }

    @Override
    public boolean registrarSeNovo(String eventoId) {
        int linhas = bancoDeDados.update(
                "insert into evento_processado (evento_id) values (?) on conflict do nothing", eventoId);
        return linhas == 1;
    }
}
