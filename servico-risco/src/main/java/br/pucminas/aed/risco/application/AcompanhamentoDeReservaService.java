package br.pucminas.aed.risco.application;

import br.pucminas.aed.risco.application.port.in.AcompanharReserva;
import br.pucminas.aed.risco.application.port.out.AnalisesDeCredito;
import br.pucminas.aed.risco.application.port.out.EventosProcessados;
import br.pucminas.aed.risco.domain.AnaliseDeCredito;
import br.pucminas.aed.risco.domain.SituacaoDaReserva;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Leva o desfecho da saga de reserva para a analise, tornando o estado final observavel:
 * PENDENTE, RESERVADA ou CANCELADA. Quem decide se a transicao vale e o agregado.
 *
 * <p>Se a analise ainda nao existe, a solicitacao ainda nao foi processada: a falha e transitoria
 * e vai para a retentativa.
 */
public final class AcompanhamentoDeReservaService implements AcompanharReserva {
    private static final Logger log = LoggerFactory.getLogger(AcompanhamentoDeReservaService.class);

    private final EventosProcessados eventosProcessados;
    private final AnalisesDeCredito analises;

    public AcompanhamentoDeReservaService(EventosProcessados eventosProcessados, AnalisesDeCredito analises) {
        this.eventosProcessados = Objects.requireNonNull(eventosProcessados);
        this.analises = Objects.requireNonNull(analises);
    }

    @Override
    public boolean registrarReserva(String eventoId, UUID solicitacaoId) {
        return registrar(eventoId, solicitacaoId, SituacaoDaReserva.RESERVADA, AnaliseDeCredito::registrarReserva);
    }

    @Override
    public boolean registrarCancelamento(String eventoId, UUID solicitacaoId) {
        return registrar(eventoId, solicitacaoId, SituacaoDaReserva.CANCELADA, AnaliseDeCredito::registrarCancelamento);
    }

    private boolean registrar(String eventoId, UUID solicitacaoId, SituacaoDaReserva pretendida,
                              Predicate<AnaliseDeCredito> transicao) {
        if (!eventosProcessados.registrarSeNovo(eventoId)) {
            return false;
        }
        var analise = analises.buscar(solicitacaoId)
                .orElseThrow(() -> new AnaliseAindaNaoRegistradaException(solicitacaoId));
        var anterior = analise.situacaoDaReserva();
        if (!transicao.test(analise)) {
            log.info("Transicao ignorada | solicitacaoId={} | statusAtual={} | statusIgnorado={}",
                    solicitacaoId, anterior, pretendida);
            return false;
        }
        if (!analises.atualizarSituacao(solicitacaoId, anterior, analise.situacaoDaReserva())) {
            throw new ConflitoDeAtualizacaoException(solicitacaoId);
        }
        log.info("Analise de credito atualizada | solicitacaoId={} | status={}",
                solicitacaoId, analise.situacaoDaReserva());
        return true;
    }
}
