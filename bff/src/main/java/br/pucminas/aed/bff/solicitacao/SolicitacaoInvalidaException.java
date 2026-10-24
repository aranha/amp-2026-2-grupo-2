package br.pucminas.aed.bff.solicitacao;

public class SolicitacaoInvalidaException extends RuntimeException {

    public SolicitacaoInvalidaException(String motivo) {
        super(motivo);
    }
}
