package br.pucminas.aed.bff.proposta;

import java.time.OffsetDateTime;

public record AntifraudeV2(Boolean aprovada, Integer score, String motivo, OffsetDateTime dataAnalise) {
}
