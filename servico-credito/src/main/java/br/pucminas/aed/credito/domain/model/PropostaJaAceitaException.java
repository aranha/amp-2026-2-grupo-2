package br.pucminas.aed.credito.domain.model;

/** Erro de domínio: mapear para HTTP 409 somente no adaptador REST. */
public final class PropostaJaAceitaException extends RuntimeException {
    public PropostaJaAceitaException(String id) { super("Proposta já aceita: " + id); }
}
