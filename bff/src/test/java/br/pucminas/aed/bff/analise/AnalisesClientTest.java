package br.pucminas.aed.bff.analise;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Method;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AnalisesClientTest {

    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://servico-risco:8080");
    private final MockRestServiceServer risco = MockRestServiceServer.bindTo(builder).build();
    private final AnalisesClient cliente = new AnalisesClient(builder.build());

    @Test
    void traduzOsPareceresDoRiscoEIgnoraOQueATelaNaoUsa() {
        risco.expect(requestTo("http://servico-risco:8080/v1/analises"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""
                {"analises":[{"solicitacaoId":"3f1c9a60-7b4e-4a21-9c0d-5e2a1b8f4d33","elegivel":true,
                  "valorAprovado":100000.00,"motivo":"Aprovado parcialmente: valor limitado a R$ 100.000,00",
                  "dataAnalise":"2026-10-25T10:00:00.123-03:00","valorSolicitado":150000.00,
                  "situacaoDaReserva":"PENDENTE"}]}
                """, MediaType.APPLICATION_JSON));

        assertThat(cliente.listar()).singleElement().satisfies(analise -> {
            assertThat(analise.solicitacaoId()).isEqualTo("3f1c9a60-7b4e-4a21-9c0d-5e2a1b8f4d33");
            assertThat(analise.elegivel()).isTrue();
            assertThat(analise.valorAprovado()).isEqualByComparingTo("100000.00");
            // O risco fala no horario de Brasilia; o BFF devolve o instante em UTC.
            assertThat(analise.dataAnalise()).isEqualTo(Instant.parse("2026-10-25T13:00:00.123Z"));
        });
        risco.verify();
    }

    @Test
    void riscoSemAnalisesDevolveListaVazia() {
        risco.expect(requestTo("http://servico-risco:8080/v1/analises"))
            .andRespond(withSuccess("{\"analises\":[]}", MediaType.APPLICATION_JSON));

        assertThat(cliente.listar()).isEmpty();
    }

    @Test
    void erroDoRiscoSobeParaOCircuitBreaker() {
        risco.expect(requestTo("http://servico-risco:8080/v1/analises")).andRespond(withServerError());

        assertThatThrownBy(cliente::listar).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aLeituraTemRetryPorqueEIdempotenteEOResto() throws Exception {
        Method listar = AnalisesClient.class.getMethod("listar");

        assertThat(listar.getAnnotation(Retry.class)).isNotNull();
        assertThat(listar.getAnnotation(CircuitBreaker.class).name()).isEqualTo("risco");
        assertThat(listar.getAnnotation(Bulkhead.class)).isNotNull();
    }
}
