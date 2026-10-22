package br.pucminas.aed.credito.application;

public final class PropostaNaoEncontradaException extends RuntimeException {
    public PropostaNaoEncontradaException(String id) { super("Proposta nao encontrada: " + id); }
}
