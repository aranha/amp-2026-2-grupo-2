package br.pucminas.aed.risco.adapter.in.web;

class AnaliseNaoEncontradaException extends RuntimeException {

    AnaliseNaoEncontradaException(String solicitacaoId) {
        super("Analise nao encontrada: " + solicitacaoId);
    }
}
