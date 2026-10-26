package br.pucminas.aed.risco.application;

import br.pucminas.aed.risco.application.port.in.AvaliarSolicitacao;
import br.pucminas.aed.risco.application.port.out.AnalisesDeCredito;
import br.pucminas.aed.risco.application.port.out.EventosProcessados;
import br.pucminas.aed.risco.application.port.out.PublicadorDeElegibilidade;
import br.pucminas.aed.risco.domain.AnaliseDeCredito;
import br.pucminas.aed.risco.domain.Solicitacao;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Caso de uso da avaliacao. Depende so do dominio e das portas: quem chama pode ser o consumidor
 * Kafka ou um teste, e quem guarda pode ser o Postgres ou a memoria.
 *
 * <p>A ordem importa. O dedup pelo ce_id vem primeiro, na mesma transacao da gravacao: uma falha
 * depois dele desfaz o registro e a retentativa processa de novo. A publicacao vem por ultimo e e
 * sincrona, entao a analise so e confirmada depois que o parecer chegou ao broker. Se a
 * confirmacao falhar depois da publicacao, a retentativa publica o mesmo eventoId de novo e os
 * consumidores descartam a copia.
 */
public final class AvaliacaoDeCreditoService implements AvaliarSolicitacao {
    private static final Logger log = LoggerFactory.getLogger(AvaliacaoDeCreditoService.class);

    private final EventosProcessados eventosProcessados;
    private final AnalisesDeCredito analises;
    private final PublicadorDeElegibilidade publicador;
    private final Clock relogio;

    public AvaliacaoDeCreditoService(EventosProcessados eventosProcessados, AnalisesDeCredito analises,
                                     PublicadorDeElegibilidade publicador, Clock relogio) {
        this.eventosProcessados = Objects.requireNonNull(eventosProcessados);
        this.analises = Objects.requireNonNull(analises);
        this.publicador = Objects.requireNonNull(publicador);
        this.relogio = Objects.requireNonNull(relogio);
    }

    @Override
    public Optional<AnaliseDeCredito> avaliar(String eventoId, Solicitacao solicitacao) {
        if (!eventosProcessados.registrarSeNovo(eventoId)) {
            log.info("Evento duplicado ignorado | ce_id={}", eventoId);
            return Optional.empty();
        }
        var analise = AnaliseDeCredito.avaliar(solicitacao,
                OffsetDateTime.now(relogio).truncatedTo(ChronoUnit.MILLIS));
        if (!analises.registrar(analise)) {
            log.info("Solicitacao ja analisada, evento ignorado | solicitacaoId={} | ce_id={}",
                    solicitacao.id(), eventoId);
            return Optional.empty();
        }
        publicador.publicar(analise.elegibilidadeAvaliada());
        log.info("Elegibilidade avaliada | solicitacaoId={} | elegivel={} | valorAprovado={}",
                analise.solicitacaoId(), analise.parecer().elegivel(), analise.parecer().valorAprovado());
        return Optional.of(analise);
    }
}
