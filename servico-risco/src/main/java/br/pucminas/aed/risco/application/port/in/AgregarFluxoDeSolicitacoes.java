package br.pucminas.aed.risco.application.port.in;

import br.pucminas.aed.risco.domain.Solicitacao;

/** Porta de entrada: volume de credito solicitado por janela de tempo, herdado da AED. */
public interface AgregarFluxoDeSolicitacoes {

    void agregar(String eventoId, Solicitacao solicitacao);
}
