package br.pucminas.aed.risco.adapter.out.kafka;

import br.pucminas.aed.risco.domain.ElegibilidadeAvaliada;

/** O broker nao confirmou a publicacao no prazo. Transitoria: a retentativa publica de novo. */
public class PublicacaoFalhouException extends RuntimeException {

    public PublicacaoFalhouException(ElegibilidadeAvaliada evento, Throwable causa) {
        super("elegibilidade nao publicada | solicitacaoId=" + evento.solicitacaoId()
                + " | eventoId=" + evento.eventoId(), causa);
    }
}
