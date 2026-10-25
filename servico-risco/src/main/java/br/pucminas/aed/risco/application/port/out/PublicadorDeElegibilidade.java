package br.pucminas.aed.risco.application.port.out;

import br.pucminas.aed.risco.domain.ElegibilidadeAvaliada;

/**
 * Porta de saida: entrega o parecer a quem depende dele. So retorna depois que o broker
 * confirmou; uma falha aqui desfaz a analise, e a retentativa avalia de novo.
 */
public interface PublicadorDeElegibilidade {

    void publicar(ElegibilidadeAvaliada evento);
}
