package br.pucminas.aed.bff.solicitacao;

import br.pucminas.aed.bff.proposta.FonteIndisponivelException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

@Component
public class SolicitacaoClient {

    private final RestClient http;

    public SolicitacaoClient(@Qualifier("creditoRestClient") RestClient http) {
        this.http = http;
    }

    @CircuitBreaker(name = "credito", fallbackMethod = "indisponivel")
    @Bulkhead(name = "credito")
    public SolicitacaoRecebida criar(String clienteId, BigDecimal valorSolicitado,
                                     String canalOrigem, OffsetDateTime dataSolicitacao) {
        return http.post()
            .uri("/solicitacoes")
            .body(Map.of(
                "clienteId", clienteId,
                "valorSolicitado", valorSolicitado,
                "canalOrigem", canalOrigem,
                "dataSolicitacao", dataSolicitacao))
            .exchange((pedido, resposta) -> {
                if (resposta.getStatusCode().is4xxClientError()) {
                    throw new SolicitacaoInvalidaException(resposta.bodyTo(String.class));
                }
                if (resposta.getStatusCode().isError()) {
                    throw new IllegalStateException("credito respondeu " + resposta.getStatusCode());
                }
                return resposta.bodyTo(SolicitacaoRecebida.class);
            });
    }

    @SuppressWarnings("unused")
    private SolicitacaoRecebida indisponivel(String clienteId, BigDecimal valorSolicitado,
                                             String canalOrigem, OffsetDateTime dataSolicitacao,
                                             Throwable causa) {
        throw new FonteIndisponivelException("credito", causa);
    }
}
