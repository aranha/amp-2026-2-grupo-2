package br.pucminas.aed.credito.application;

import br.pucminas.aed.credito.domain.model.Dinheiro;
import br.pucminas.aed.credito.domain.model.PropostaJaAceitaException;
import br.pucminas.aed.credito.domain.model.SolicitacaoDeCredito;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AceitarPropostaUseCaseTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneOffset.UTC);
    private SolicitacaoDeCredito nova() {
        return new SolicitacaoDeCredito("s1", new Dinheiro(new BigDecimal("100.00")));
    }

    @Test void aceitaPropostaEAtualizaEstadoAtomicamente() {
        PersistenciaProposta repo = mock(PersistenciaProposta.class);
        when(repo.buscarPorId("s1")).thenReturn(Optional.of(nova()));
        when(repo.marcarAceitaSeCriada(eq("s1"), any())).thenReturn(true);
        var evento = new AceitarPropostaUseCase(repo, clock).executar("s1");
        assertEquals("s1", evento.solicitacaoId());
        verify(repo).marcarAceitaSeCriada(eq("s1"), any());
    }

    @Test void duasRequisicoesConcorrentesNaoConfirmamAmbas() {
        PersistenciaProposta repo = mock(PersistenciaProposta.class);
        when(repo.buscarPorId("s1")).thenAnswer(a -> Optional.of(nova()));
        when(repo.marcarAceitaSeCriada(eq("s1"), any())).thenReturn(true, false);
        var useCase = new AceitarPropostaUseCase(repo, clock);
        useCase.executar("s1");
        assertThrows(PropostaJaAceitaException.class, () -> useCase.executar("s1"));
    }

    @Test void retornaNaoEncontrada() {
        PersistenciaProposta repo = mock(PersistenciaProposta.class);
        when(repo.buscarPorId("ausente")).thenReturn(Optional.empty());
        assertThrows(PropostaNaoEncontradaException.class,
            () -> new AceitarPropostaUseCase(repo, clock).executar("ausente"));
    }
}
