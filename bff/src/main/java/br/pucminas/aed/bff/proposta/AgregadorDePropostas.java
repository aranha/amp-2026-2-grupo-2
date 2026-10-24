package br.pucminas.aed.bff.proposta;

import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class AgregadorDePropostas {

    private final CreditoClient credito;
    private final RiscoClient risco;
    private final AntifraudeClient antifraude;
    private final Executor executor;

    public AgregadorDePropostas(CreditoClient credito, RiscoClient risco,
                                AntifraudeClient antifraude, Executor executor) {
        this.credito = credito;
        this.risco = risco;
        this.antifraude = antifraude;
        this.executor = executor;
    }

    public PropostaV2 agregar(String solicitacaoId) {
        PropostaDoCredito proposta = credito.buscar(solicitacaoId)
            .orElseThrow(() -> new PropostaNaoEncontradaException(solicitacaoId));

        CompletableFuture<Optional<ParecerDeRisco>> parecer =
            CompletableFuture.supplyAsync(() -> risco.buscar(solicitacaoId), executor);
        CompletableFuture<Optional<StatusAntifraude>> analise =
            CompletableFuture.supplyAsync(() -> antifraude.buscar(solicitacaoId), executor);

        return montar(proposta, parecer.join(), analise.join());
    }

    private PropostaV2 montar(PropostaDoCredito proposta,
                              Optional<ParecerDeRisco> parecer,
                              Optional<StatusAntifraude> analise) {
        return new PropostaV2(
            proposta.solicitacaoId(),
            proposta.propostaId(),
            proposta.elegivel(),
            proposta.statusContrato(),
            condicoes(proposta),
            parecer.map(p -> new ParecerV2(p.elegivel(), p.motivo(), p.dataAnalise())).orElse(null),
            analise.map(a -> new AntifraudeV2(a.aprovada(), a.score(), a.motivo(), a.dataAnalise())).orElse(null),
            parecer.isEmpty(),
            analise.isEmpty(),
            proposta.atualizadaEm());
    }

    private CondicoesV2 condicoes(PropostaDoCredito proposta) {
        CondicoesDoCredito origem = proposta.condicoes();
        if (origem == null) {
            return new CondicoesV2(proposta.valorAprovado(), null, null, null);
        }
        return new CondicoesV2(
            proposta.valorAprovado(),
            origem.taxaMensal(),
            origem.numeroParcelas(),
            origem.cet());
    }
}
