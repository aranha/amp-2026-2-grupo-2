package br.pucminas.aed.bff.proposta;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Component
public class AntifraudeClient {

    private static final Logger log = LoggerFactory.getLogger(AntifraudeClient.class);

    private final RestClient http;

    public AntifraudeClient(@Qualifier("antifraudeRestClient") RestClient http) {
        this.http = http;
    }

    @Retry(name = "antifraude")
    @CircuitBreaker(name = "antifraude", fallbackMethod = "semAnalise")
    @Bulkhead(name = "antifraude")
    public Optional<StatusAntifraude> buscar(String solicitacaoId) {
        return http.get()
            .uri("/v1/analises/{id}", solicitacaoId)
            .exchange((pedido, resposta) -> {
                if (resposta.getStatusCode().value() == 404) {
                    return Optional.empty();
                }
                if (resposta.getStatusCode().isError()) {
                    throw new IllegalStateException("antifraude respondeu " + resposta.getStatusCode());
                }
                return Optional.ofNullable(resposta.bodyTo(StatusAntifraude.class));
            });
    }

    @SuppressWarnings("unused")
    private Optional<StatusAntifraude> semAnalise(String solicitacaoId, Throwable causa) {
        log.warn("Degradando sem a analise antifraude de {}: {}", solicitacaoId, causa.toString());
        return Optional.empty();
    }
}
