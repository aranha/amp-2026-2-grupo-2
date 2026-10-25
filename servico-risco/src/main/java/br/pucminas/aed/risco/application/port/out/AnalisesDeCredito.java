package br.pucminas.aed.risco.application.port.out;

import br.pucminas.aed.risco.domain.AnaliseDeCredito;
import br.pucminas.aed.risco.domain.SituacaoDaReserva;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Porta de saida: o repositorio do agregado. Uma analise por solicitacao. */
public interface AnalisesDeCredito {

    /** Grava a analise se a solicitacao ainda nao tem uma. @return {@code false} se ja tinha. */
    boolean registrar(AnaliseDeCredito analise);

    Optional<AnaliseDeCredito> buscar(UUID solicitacaoId);

    /**
     * Grava a nova situacao so se a gravada ainda for {@code anterior}: duas transicoes
     * concorrentes nao se sobrescrevem. @return {@code false} se outra chegou antes.
     */
    boolean atualizarSituacao(UUID solicitacaoId, SituacaoDaReserva anterior, SituacaoDaReserva nova);

    /** As mais recentes primeiro. */
    List<AnaliseDeCredito> listarRecentes(int limite);
}
