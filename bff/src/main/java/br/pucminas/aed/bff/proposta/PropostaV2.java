package br.pucminas.aed.bff.proposta;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PropostaV2(
    String solicitacaoId,
    String propostaId,
    Boolean elegivel,
    String statusContrato,
    CondicoesV2 condicoes,
    ParecerV2 parecer,
    AntifraudeV2 antifraude,
    boolean parecerIndisponivel,
    boolean antifraudeIndisponivel,
    OffsetDateTime atualizadaEm) {
}
