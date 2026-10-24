package br.pucminas.aed.bff.analise;

import java.util.List;

public record RespostaDeAnalises(String consultadoPor, List<AnaliseResumo> analises) {
}
