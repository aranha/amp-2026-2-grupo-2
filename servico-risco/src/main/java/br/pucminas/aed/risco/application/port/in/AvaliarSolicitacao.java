package br.pucminas.aed.risco.application.port.in;

import br.pucminas.aed.risco.domain.AnaliseDeCredito;
import br.pucminas.aed.risco.domain.Solicitacao;
import java.util.Optional;

/** Porta de entrada: uma solicitacao de credito chegou e precisa de parecer de elegibilidade. */
public interface AvaliarSolicitacao {

    /**
     * @param eventoId o {@code ce_id} do evento recebido, chave de deduplicacao.
     * @return a analise criada; vazio quando o evento ja foi processado ou a solicitacao ja tinha
     *         analise por outro evento.
     */
    Optional<AnaliseDeCredito> avaliar(String eventoId, Solicitacao solicitacao);
}
