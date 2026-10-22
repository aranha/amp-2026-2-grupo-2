package br.pucminas.aed.credito.application;

import br.pucminas.aed.credito.domain.model.PropostaAceita;
import br.pucminas.aed.credito.domain.model.PropostaJaAceitaException;
import br.pucminas.aed.credito.domain.model.SolicitacaoDeCredito;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;

/** Orquestracao da aceitacao; depende somente de porta e dominio. */
public final class AceitarPropostaUseCase {
    private final PersistenciaProposta porta;
    private final Clock clock;

    public AceitarPropostaUseCase(PersistenciaProposta porta, Clock clock) {
        this.porta = Objects.requireNonNull(porta);
        this.clock = Objects.requireNonNull(clock);
    }

    public PropostaAceita executar(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id e obrigatorio");
        SolicitacaoDeCredito proposta = porta.buscarPorId(id)
                .orElseThrow(() -> new PropostaNaoEncontradaException(id));
        OffsetDateTime agora = OffsetDateTime.now(clock);
        PropostaAceita evento = proposta.aceitar(agora);
        // UPDATE condicional garante exclusao mesmo entre requisicoes em paralelo.
        if (!porta.marcarAceitaSeCriada(id, agora)) {
            throw new PropostaJaAceitaException(id);
        }
        return evento;
    }
}
