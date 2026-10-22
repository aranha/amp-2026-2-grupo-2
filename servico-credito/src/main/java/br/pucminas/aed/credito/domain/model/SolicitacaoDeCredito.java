package br.pucminas.aed.credito.domain.model;

import java.time.OffsetDateTime;
import java.util.Objects;

/** Aggregate Root: centraliza a transição de proposta criada para aceita. */
public final class SolicitacaoDeCredito {
    public enum Estado { CRIADA, ACEITA }
    private final String id;
    private final Dinheiro valor;
    private Estado estado;

    public SolicitacaoDeCredito(String id, Dinheiro valor) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id é obrigatório");
        this.id = id;
        this.valor = Objects.requireNonNull(valor, "valor é obrigatório");
        this.estado = Estado.CRIADA;
    }

    public PropostaAceita aceitar(OffsetDateTime agora) {
        Objects.requireNonNull(agora, "data é obrigatória");
        if (estado == Estado.ACEITA) throw new PropostaJaAceitaException(id);
        estado = Estado.ACEITA;
        return new PropostaAceita(id, agora);
    }

    public String id() { return id; }
    public Dinheiro valor() { return valor; }
    public Estado estado() { return estado; }
}
