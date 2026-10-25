package br.pucminas.aed.risco.application.port.in;

import java.util.UUID;

/** Porta de entrada: o desfecho da saga de reserva de limite chega a analise. */
public interface AcompanharReserva {

    /** @return {@code true} quando a situacao da analise mudou. */
    boolean registrarReserva(String eventoId, UUID solicitacaoId);

    /** @return {@code true} quando a situacao da analise mudou. */
    boolean registrarCancelamento(String eventoId, UUID solicitacaoId);
}
