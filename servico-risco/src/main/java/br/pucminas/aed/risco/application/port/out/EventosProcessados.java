package br.pucminas.aed.risco.application.port.out;

/** Porta de saida: deduplicacao pelo {@code ce_id}, o que torna o consumo idempotente. */
public interface EventosProcessados {

    /** @return {@code false} se o evento ja tinha sido processado. */
    boolean registrarSeNovo(String eventoId);
}
