package br.pucminas.aed.bff.proposta;

public class PropostaNaoEncontradaException extends RuntimeException {

    public PropostaNaoEncontradaException(String solicitacaoId) {
        super("Proposta nao encontrada: " + solicitacaoId);
    }
}
