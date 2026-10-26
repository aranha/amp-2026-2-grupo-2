package br.pucminas.aed.bff.analise;

import br.pucminas.aed.bff.proposta.FonteIndisponivelException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class AnalisesClient {

    private final RestClient http;

    public AnalisesClient(@Qualifier("riscoRestClient") RestClient http) {
        this.http = http;
    }

    @Retry(name = "risco")
    @CircuitBreaker(name = "risco", fallbackMethod = "indisponivel")
    @Bulkhead(name = "risco")
    public List<AnaliseResumo> listar() {
        return http.get()
            .uri("/v1/analises")
            .exchange((pedido, resposta) -> {
                if (resposta.getStatusCode().isError()) {
                    throw new IllegalStateException("risco respondeu " + resposta.getStatusCode());
                }
                AnalisesDoRisco corpo = resposta.bodyTo(AnalisesDoRisco.class);
                return corpo == null || corpo.analises() == null ? List.of() : corpo.analises();
            });
    }

    // Aqui o risco é a única fonte: sem ele não há lista parcial a devolver, então não degrada.
    @SuppressWarnings("unused")
    private List<AnaliseResumo> indisponivel(Throwable causa) {
        throw new FonteIndisponivelException("risco", causa);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AnalisesDoRisco(List<AnaliseResumo> analises) {
    }
}
