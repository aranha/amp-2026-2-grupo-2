package br.pucminas.aed.credito.domain.model;

import java.time.OffsetDateTime;
import java.util.Objects;

/** Aggregate Root sem dependencia de infraestrutura. */
public final class SolicitacaoDeCredito {
    public enum Estado { CRIADA, ACEITA }
    private final String id;
    private final Dinheiro valor;
    private Estado estado;

    public SolicitacaoDeCredito(String id, Dinheiro valor) {
        this(id, valor, Estado.CRIADA);
    }

    private SolicitacaoDeCredito(String id, Dinheiro valor, Estado estado) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id e obrigatorio");
        this.id = id;
        this.valor = Objects.requireNonNull(valor, "valor e obrigatorio");
        this.estado = Objects.requireNonNull(estado, "estado e obrigatorio");
    }

    /** Reconstrucao de um agregado persistido sem disparar eventos. */
    public static SolicitacaoDeCredito reconstituir(String id, Dinheiro valor, Estado estado) {
        return new SolicitacaoDeCredito(id, valor, estado);
    }

    public PropostaAceita aceitar(OffsetDateTime agora) {
        Objects.requireNonNull(agora, "data e obrigatoria");
        if (estado == Estado.ACEITA) throw new PropostaJaAceitaException(id);
        estado = Estado.ACEITA;
        return new PropostaAceita(id, agora);
    }

    public String id() { return id; }
    public Dinheiro valor() { return valor; }
    public Estado estado() { return estado; }
}
