package br.pucminas.aed.bff.proposta;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgregadorDePropostasTest {

    private static final String ID = "3f1c9a60-7b4e-4a21-9c0d-5e2a1b8f4d33";

    private final CreditoClient credito = mock(CreditoClient.class);
    private final RiscoClient risco = mock(RiscoClient.class);
    private final AntifraudeClient antifraude = mock(AntifraudeClient.class);
    private final AgregadorDePropostas agregador =
        new AgregadorDePropostas(credito, risco, antifraude, Runnable::run);

    @BeforeEach
    void cenarioFeliz() {
        when(credito.buscar(anyString())).thenReturn(Optional.of(proposta()));
        when(risco.buscar(anyString())).thenReturn(Optional.of(parecer()));
        when(antifraude.buscar(anyString())).thenReturn(Optional.of(analise()));
    }

    @Test
    void juntaAsTresFontesNumaRespostaSo() {
        PropostaV2 agregada = agregador.agregar(ID);

        assertThat(agregada.solicitacaoId()).isEqualTo(ID);
        assertThat(agregada.condicoes().valorAprovado()).isEqualByComparingTo("18500.00");
        assertThat(agregada.condicoes().taxa()).isEqualByComparingTo("0.019");
        assertThat(agregada.parecer().motivo()).isEqualTo("Renda compativel");
        assertThat(agregada.antifraude().score()).isEqualTo(870);
        assertThat(agregada.parecerIndisponivel()).isFalse();
        assertThat(agregada.antifraudeIndisponivel()).isFalse();
    }

    @Test
    void semAntifraudeDevolveORestoEMarcaIndisponivel() {
        when(antifraude.buscar(anyString())).thenReturn(Optional.empty());

        PropostaV2 agregada = agregador.agregar(ID);

        assertThat(agregada.antifraude()).isNull();
        assertThat(agregada.antifraudeIndisponivel()).isTrue();
        assertThat(agregada.condicoes().valorAprovado()).isEqualByComparingTo("18500.00");
        assertThat(agregada.parecer()).isNotNull();
    }

    @Test
    void semParecerDeRiscoDevolveORestoEMarcaIndisponivel() {
        when(risco.buscar(anyString())).thenReturn(Optional.empty());

        PropostaV2 agregada = agregador.agregar(ID);

        assertThat(agregada.parecer()).isNull();
        assertThat(agregada.parecerIndisponivel()).isTrue();
        assertThat(agregada.antifraude()).isNotNull();
    }

    @Test
    void semAPropostaNaoHaOQueAgregar() {
        when(credito.buscar(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> agregador.agregar(ID))
            .isInstanceOf(PropostaNaoEncontradaException.class);
    }

    @Test
    void creditoSemCondicoesAindaDevolveOValorAprovado() {
        when(credito.buscar(anyString())).thenReturn(Optional.of(
            new PropostaDoCredito(ID, "p-1", true, new BigDecimal("18500.00"),
                "ATIVO", "c-1", OffsetDateTime.parse("2026-10-24T13:02:11Z"), null)));

        PropostaV2 agregada = agregador.agregar(ID);

        assertThat(agregada.condicoes().valorAprovado()).isEqualByComparingTo("18500.00");
        assertThat(agregada.condicoes().taxa()).isNull();
        assertThat(agregada.condicoes().parcelas()).isNull();
    }

    private PropostaDoCredito proposta() {
        return new PropostaDoCredito(ID, "p-1", true, new BigDecimal("18500.00"), "ATIVO", "c-1",
            OffsetDateTime.parse("2026-10-24T13:02:11Z"),
            new CondicoesDoCredito(new BigDecimal("0.019"), 24,
                new BigDecimal("950.12"), new BigDecimal("22802.88"), new BigDecimal("0.0241")));
    }

    private ParecerDeRisco parecer() {
        return new ParecerDeRisco(ID, true, new BigDecimal("18500.00"), "Renda compativel",
            OffsetDateTime.parse("2026-10-24T13:01:00Z"));
    }

    private StatusAntifraude analise() {
        return new StatusAntifraude(ID, true, 870, "Sem indicio",
            OffsetDateTime.parse("2026-10-24T13:02:00Z"));
    }
}
