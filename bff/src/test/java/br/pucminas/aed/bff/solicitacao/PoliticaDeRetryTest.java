package br.pucminas.aed.bff.solicitacao;

import br.pucminas.aed.bff.proposta.AntifraudeClient;
import br.pucminas.aed.bff.proposta.CreditoClient;
import br.pucminas.aed.bff.proposta.RiscoClient;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PoliticaDeRetryTest {

    @Test
    void osGetTemRetryPorqueSaoIdempotentes() throws Exception {
        for (Class<?> cliente : new Class<?>[] {CreditoClient.class, RiscoClient.class, AntifraudeClient.class}) {
            Method busca = cliente.getMethod("buscar", String.class);

            assertThat(busca.getAnnotation(Retry.class))
                .withFailMessage("%s.buscar deveria ter @Retry", cliente.getSimpleName())
                .isNotNull();
        }
    }

    @Test
    void oPostNaoTemRetryPorqueCriaSolicitacao() throws Exception {
        Method criar = SolicitacaoClient.class.getMethod(
            "criar", String.class, BigDecimal.class, String.class, OffsetDateTime.class);

        assertThat(criar.getAnnotation(Retry.class))
            .withFailMessage("POST /solicitacoes nao pode ter @Retry: repetir cria solicitacao duplicada")
            .isNull();
    }

    @Test
    void masOPostContinuaProtegidoPeloRestante() throws Exception {
        Method criar = SolicitacaoClient.class.getMethod(
            "criar", String.class, BigDecimal.class, String.class, OffsetDateTime.class);

        assertThat(criar.getAnnotation(CircuitBreaker.class)).isNotNull();
        assertThat(criar.getAnnotation(Bulkhead.class)).isNotNull();
    }
}
