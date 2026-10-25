package br.pucminas.aed.risco.adapter.in.web;

import java.util.List;

/** Envelope da lista: paginacao entra depois como campo novo, sem quebrar quem ja consome. */
public record ListaDeAnalises(List<AnaliseResposta> analises) {
}
