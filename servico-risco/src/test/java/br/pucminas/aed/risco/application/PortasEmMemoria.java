package br.pucminas.aed.risco.application;

import br.pucminas.aed.risco.application.port.out.AnalisesDeCredito;
import br.pucminas.aed.risco.application.port.out.EventosProcessados;
import br.pucminas.aed.risco.application.port.out.PublicadorDeElegibilidade;
import br.pucminas.aed.risco.domain.AnaliseDeCredito;
import br.pucminas.aed.risco.domain.ElegibilidadeAvaliada;
import br.pucminas.aed.risco.domain.SituacaoDaReserva;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Adaptadores de saida em memoria. Trocar o Postgres e o Kafka por isto sem mexer no caso de uso
 * e o que o hexagono promete.
 */
final class PortasEmMemoria {

    private PortasEmMemoria() {
    }

    static final class Analises implements AnalisesDeCredito {
        final Map<UUID, AnaliseDeCredito> gravadas = new LinkedHashMap<>();
        boolean simularConflito;

        @Override
        public boolean registrar(AnaliseDeCredito analise) {
            return gravadas.putIfAbsent(analise.solicitacaoId(), analise) == null;
        }

        @Override
        public Optional<AnaliseDeCredito> buscar(UUID solicitacaoId) {
            var gravada = gravadas.get(solicitacaoId);
            // Devolve uma copia, como um banco faria: mudar o objeto lido nao grava nada.
            return Optional.ofNullable(gravada).map(analise -> AnaliseDeCredito.reconstituir(
                    analise.solicitacao(), analise.parecer(), analise.analisadaEm(), analise.situacaoDaReserva()));
        }

        @Override
        public boolean atualizarSituacao(UUID solicitacaoId, SituacaoDaReserva anterior, SituacaoDaReserva nova) {
            var gravada = gravadas.get(solicitacaoId);
            if (simularConflito || gravada == null || gravada.situacaoDaReserva() != anterior) {
                return false;
            }
            gravadas.put(solicitacaoId, AnaliseDeCredito.reconstituir(
                    gravada.solicitacao(), gravada.parecer(), gravada.analisadaEm(), nova));
            return true;
        }

        @Override
        public List<AnaliseDeCredito> listarRecentes(int limite) {
            return gravadas.values().stream()
                    .sorted(Comparator.comparing(AnaliseDeCredito::analisadaEm).reversed())
                    .limit(limite)
                    .toList();
        }
    }

    static final class Eventos implements EventosProcessados {
        final Set<String> processados = new HashSet<>();

        @Override
        public boolean registrarSeNovo(String eventoId) {
            return processados.add(eventoId);
        }
    }

    static final class Publicador implements PublicadorDeElegibilidade {
        final List<ElegibilidadeAvaliada> publicados = new ArrayList<>();
        RuntimeException falha;

        @Override
        public void publicar(ElegibilidadeAvaliada evento) {
            if (falha != null) {
                throw falha;
            }
            publicados.add(evento);
        }
    }
}
