package br.pucminas.aed.risco.domain;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate Root do contexto de Risco: a analise de uma solicitacao de credito.
 *
 * <p>Protege duas regras. A de elegibilidade, aplicada uma unica vez na criacao, decide se o
 * pedido segue e quanto dele pode ser aprovado. A da saga de reserva, herdada da AED, so deixa a
 * situacao avancar. Nada aqui conhece Spring, Kafka ou banco: e o centro do hexagono.
 */
public final class AnaliseDeCredito {

    /** Abaixo dele a concessao nao se paga. */
    public static final BigDecimal VALOR_MINIMO = new BigDecimal("1000.00");
    /** Teto da concessao automatica: ate ele, aprova o valor pedido. */
    public static final BigDecimal LIMITE_AUTOMATICO = new BigDecimal("100000.00");
    /** Acima do limite e ate o dobro dele, aprova so o limite. Acima disso, recusa. */
    public static final BigDecimal TETO_DE_ANALISE = LIMITE_AUTOMATICO.multiply(BigDecimal.TWO);

    private final Solicitacao solicitacao;
    private final Parecer parecer;
    private final OffsetDateTime analisadaEm;
    private SituacaoDaReserva situacaoDaReserva;

    private AnaliseDeCredito(Solicitacao solicitacao, Parecer parecer, OffsetDateTime analisadaEm,
                             SituacaoDaReserva situacaoDaReserva) {
        this.solicitacao = Objects.requireNonNull(solicitacao, "solicitacao e obrigatoria");
        this.parecer = Objects.requireNonNull(parecer, "parecer e obrigatorio");
        this.analisadaEm = Objects.requireNonNull(analisadaEm, "analisadaEm e obrigatoria");
        this.situacaoDaReserva = Objects.requireNonNull(situacaoDaReserva, "situacaoDaReserva e obrigatoria");
    }

    /** Aplica a regra de elegibilidade. A analise nasce com a reserva pendente. */
    public static AnaliseDeCredito avaliar(Solicitacao solicitacao, OffsetDateTime agora) {
        Objects.requireNonNull(solicitacao, "solicitacao e obrigatoria");
        return new AnaliseDeCredito(solicitacao, parecerPara(solicitacao.valorSolicitado()), agora,
                SituacaoDaReserva.PENDENTE);
    }

    /** Reconstrucao de uma analise persistida, sem reaplicar a regra nem gerar evento. */
    public static AnaliseDeCredito reconstituir(Solicitacao solicitacao, Parecer parecer,
                                                OffsetDateTime analisadaEm,
                                                SituacaoDaReserva situacaoDaReserva) {
        return new AnaliseDeCredito(solicitacao, parecer, analisadaEm, situacaoDaReserva);
    }

    private static Parecer parecerPara(BigDecimal valor) {
        if (valor.compareTo(VALOR_MINIMO) < 0) {
            return Parecer.recusado("Valor abaixo do mínimo de R$ 1.000,00 para concessão");
        }
        if (valor.compareTo(LIMITE_AUTOMATICO) <= 0) {
            return Parecer.aprovado(valor, "Valor dentro do limite de concessão automática");
        }
        if (valor.compareTo(TETO_DE_ANALISE) <= 0) {
            return Parecer.aprovado(LIMITE_AUTOMATICO, "Aprovado parcialmente: valor limitado a R$ 100.000,00");
        }
        return Parecer.recusado("Valor acima do dobro do limite de concessão automática");
    }

    /** @return {@code false} quando a transicao nao se aplica e foi ignorada. */
    public boolean registrarReserva() {
        return avancarPara(SituacaoDaReserva.RESERVADA);
    }

    /** @return {@code false} quando a transicao nao se aplica e foi ignorada. */
    public boolean registrarCancelamento() {
        return avancarPara(SituacaoDaReserva.CANCELADA);
    }

    private boolean avancarPara(SituacaoDaReserva destino) {
        if (!situacaoDaReserva.podeIrPara(destino)) {
            return false;
        }
        situacaoDaReserva = destino;
        return true;
    }

    /**
     * O fato a publicar. O id deriva da solicitacao, e nao de um sorteio: avaliar de novo a mesma
     * solicitacao, numa reentrega ou no reprocessamento da DLQ, gera o mesmo eventoId, e quem
     * consome deduplica por ele.
     */
    public ElegibilidadeAvaliada elegibilidadeAvaliada() {
        UUID eventoId = UUID.nameUUIDFromBytes(
                ("ElegibilidadeAvaliada:" + solicitacao.id()).getBytes(StandardCharsets.UTF_8));
        return new ElegibilidadeAvaliada(eventoId, solicitacao.id(), parecer.elegivel(),
                parecer.valorAprovado(), parecer.motivo(), analisadaEm);
    }

    public UUID solicitacaoId() { return solicitacao.id(); }
    public Solicitacao solicitacao() { return solicitacao; }
    public Parecer parecer() { return parecer; }
    public OffsetDateTime analisadaEm() { return analisadaEm; }
    public SituacaoDaReserva situacaoDaReserva() { return situacaoDaReserva; }
}
