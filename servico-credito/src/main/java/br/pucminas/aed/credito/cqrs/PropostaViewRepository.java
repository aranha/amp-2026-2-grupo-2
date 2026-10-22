package br.pucminas.aed.credito.cqrs;

import java.util.Optional;

public interface PropostaViewRepository {
    Optional<PropostaView> buscarPorSolicitacaoId(String id);
}
