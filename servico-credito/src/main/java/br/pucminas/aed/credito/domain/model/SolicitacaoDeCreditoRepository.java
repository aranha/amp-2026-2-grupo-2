package br.pucminas.aed.credito.domain.model;

import java.util.Optional;

/** Porta de persistência sem dependências do Spring/JDBC. */
public interface SolicitacaoDeCreditoRepository {
    Optional<SolicitacaoDeCredito> buscarPorId(String id);
    void salvar(SolicitacaoDeCredito solicitacao);
}
