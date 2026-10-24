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
public class RiscoClient {

    private static final Logger log = LoggerFactory.getLogger(RiscoClient.class);

    private final RestClient http;

    public RiscoClient(@Qualifier("riscoRestClient") RestClient http) {
        this.http = http;
    }

    @Retry(name = "risco")
    @CircuitBreaker(name = "risco", fallbackMethod = "semParecer")
    @Bulkhead(name = "risco")
    public Optional<ParecerDeRisco> buscar(String solicitacaoId) {
        return http.get()
            .uri("/v1/analises/{id}", solicitacaoId)
            .exchange((pedido, resposta) -> {
                if (resposta.getStatusCode().value() == 404) {
                    return Optional.empty();
                }
                if (resposta.getStatusCode().isError()) {
                    throw new IllegalStateException("risco respondeu " + resposta.getStatusCode());
                }
                return Optional.ofNullable(resposta.bodyTo(ParecerDeRisco.class));
            });
    }

    @SuppressWarnings("unused")
    private Optional<ParecerDeRisco> semParecer(String solicitacaoId, Throwable causa) {
        log.warn("Degradando sem o parecer de risco de {}: {}", solicitacaoId, causa.toString());
        return Optional.empty();
    }
}
