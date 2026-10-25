package br.pucminas.aed.risco.application;

import java.util.UUID;

/** Outra transicao gravou a analise entre a leitura e a escrita. Transitoria: a retentativa le de novo. */
public class ConflitoDeAtualizacaoException extends RuntimeException {

    public ConflitoDeAtualizacaoException(UUID solicitacaoId) {
        super("analise de credito alterada durante a transicao: " + solicitacaoId);
    }
}
