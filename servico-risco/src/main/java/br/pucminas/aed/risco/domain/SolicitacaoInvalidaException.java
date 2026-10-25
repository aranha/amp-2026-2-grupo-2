package br.pucminas.aed.risco.domain;

/**
 * Solicitacao que nenhuma regra consegue avaliar: valor zerado, cliente ausente. Estende
 * {@link IllegalArgumentException} porque repetir a mesma entrada produz o mesmo erro.
 */
public class SolicitacaoInvalidaException extends IllegalArgumentException {

    public SolicitacaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
