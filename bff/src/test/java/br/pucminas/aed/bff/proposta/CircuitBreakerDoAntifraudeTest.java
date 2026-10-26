package br.pucminas.aed.bff.proposta;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.ServerSocket;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O passo 6 da demo. O antifraude aponta para uma porta sem ninguem ouvindo, que e o mais perto
 * de `docker stop amp-servico-antifraude` que um teste consegue chegar: conexao recusada na hora,
 * sem esperar timeout.
 *
 * <p>O que a apresentacao afirma, e que precisa continuar verdade em 03/11: o breaker abre
 * sozinho, e enquanto ele esta aberto a consulta continua respondendo — degradada, nunca com
 * excecao vazando para o agregador.
 */
@SpringBootTest
class CircuitBreakerDoAntifraudeTest {

    private static final String ID = "3f1c9a60-7b4e-4a21-9c0d-5e2a1b8f4d33";

    @DynamicPropertySource
    static void antifraudeForaDoAr(DynamicPropertyRegistry registro) {
        registro.add("bff.downstreams.antifraude", () -> "http://localhost:" + portaFechada());
    }

    @Autowired
    private AntifraudeClient antifraude;

    @Autowired
    private CircuitBreakerRegistry registry;

    private CircuitBreaker breaker;

    @BeforeEach
    void comecaFechado() {
        breaker = registry.circuitBreaker("antifraude");
        breaker.reset();
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void aJanelaEAQueARoteiroDaDemoAnuncia() {
        CircuitBreakerConfig config = breaker.getCircuitBreakerConfig();

        assertThat(config.getSlidingWindowSize()).isEqualTo(10);
        assertThat(config.getMinimumNumberOfCalls()).isEqualTo(10);
        assertThat(config.getFailureRateThreshold()).isEqualTo(50f);
    }

    /**
     * O roteiro conta dez chamadas em voz alta. Vale porque o fallback do breaker absorve a
     * excecao antes do @Retry contar falha: uma consulta e uma chamada na janela, nao tres.
     * Se alguem inverter essa ordem, a janela enche na quarta e este teste avisa antes do palco.
     */
    @Test
    void comOServicoForaOBreakerAbreNaDecimaChamada() {
        for (int i = 1; i <= 9; i++) {
            assertThat(antifraude.buscar(ID))
                .withFailMessage("chamada %d deveria cair no fallback, nao estourar", i)
                .isEmpty();
            assertThat(breaker.getState())
                .withFailMessage("abriu na chamada %d: o roteiro da demo conta dez", i)
                .isEqualTo(CircuitBreaker.State.CLOSED);
        }

        assertThat(antifraude.buscar(ID)).isEmpty();
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void abertoOBffParaDeBaterNoServicoCaido() {
        breaker.transitionToOpenState();

        assertThat(antifraude.buscar(ID)).isEmpty();

        assertThat(breaker.getMetrics().getNumberOfNotPermittedCalls())
            .withFailMessage("a chamada deveria ter sido barrada pelo breaker, nao tentada")
            .isPositive();
    }

    @Test
    void depoisDaJanelaAbertaVoltaSozinhoParaHalfOpen() {
        breaker.transitionToOpenState();
        breaker.transitionToHalfOpenState();

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        assertThat(breaker.getCircuitBreakerConfig().isAutomaticTransitionFromOpenToHalfOpenEnabled())
            .withFailMessage("sem a transicao automatica, a demo trava no estado aberto")
            .isTrue();
        assertThat(breaker.getCircuitBreakerConfig().getPermittedNumberOfCallsInHalfOpenState())
            .isEqualTo(3);
    }

    private static int portaFechada() {
        try (ServerSocket porta = new ServerSocket(0)) {
            return porta.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("nao consegui reservar uma porta livre", e);
        }
    }
}
