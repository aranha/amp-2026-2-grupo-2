package br.pucminas.aed.bff.proposta;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Component
public class CreditoClient {

    private final RestClient http;

    public CreditoClient(@Qualifier("creditoRestClient") RestClient http) {
        this.http = http;
    }

    @Retry(name = "credito")
    @CircuitBreaker(name = "credito", fallbackMethod = "indisponivel")
    @Bulkhead(name = "credito")
    public Optional<PropostaDoCredito> buscar(String solicitacaoId) {
        return http.get()
            .uri("/v1/propostas/{id}", solicitacaoId)
            .exchange((pedido, resposta) -> {
                if (resposta.getStatusCode().value() == 404) {
                    return Optional.empty();
                }
                if (resposta.getStatusCode().isError()) {
                    throw new IllegalStateException("credito respondeu " + resposta.getStatusCode());
                }
                return Optional.ofNullable(resposta.bodyTo(PropostaDoCredito.class));
            });
    }

    @SuppressWarnings("unused")
    private Optional<PropostaDoCredito> indisponivel(String solicitacaoId, Throwable causa) {
        throw new FonteIndisponivelException("credito", causa);
    }
}
