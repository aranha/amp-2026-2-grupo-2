package br.pucminas.aed.bff.proposta;

import java.time.OffsetDateTime;

public record ParecerV2(Boolean elegivel, String motivo, OffsetDateTime dataAnalise) {
}
