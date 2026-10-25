package br.pucminas.aed.risco.domain;

/**
 * Desfecho da saga de reserva de limite, herdada da AED, visto pela analise.
 *
 * <p>Os eventos da saga chegam por topicos diferentes, entao a ordem entre eles nao e garantida.
 * As transicoes so avancam: um LimiteReservado atrasado nao desfaz um cancelamento ja registrado.
 */
public enum SituacaoDaReserva {
    PENDENTE, RESERVADA, CANCELADA;

    boolean podeIrPara(SituacaoDaReserva destino) {
        return switch (destino) {
            case PENDENTE -> false;
            case RESERVADA -> this == PENDENTE;
            case CANCELADA -> this == PENDENTE || this == RESERVADA;
        };
    }
}
