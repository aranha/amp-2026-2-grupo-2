package br.pucminas.aed.risco.application;

import java.util.UUID;

/**
 * Evento da saga chegou antes da solicitacao que ele acompanha. Nao ha ordem entre topicos:
 * a falha e transitoria e se resolve na retentativa, quando a analise ja existir.
 */
public class AnaliseAindaNaoRegistradaException extends RuntimeException {

    public AnaliseAindaNaoRegistradaException(UUID solicitacaoId) {
        super("analise de credito ainda nao registrada para a solicitacao " + solicitacaoId);
    }
}
