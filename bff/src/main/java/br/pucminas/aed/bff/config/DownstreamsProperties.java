package br.pucminas.aed.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "bff.downstreams")
public record DownstreamsProperties(
    String credito,
    String risco,
    String antifraude,
    Duration conexao,
    Duration leitura) {
}
