package br.pucminas.aed.bff.proposta;

public class FonteIndisponivelException extends RuntimeException {

    private final String fonte;

    public FonteIndisponivelException(String fonte, Throwable causa) {
        super("Fonte indisponivel: " + fonte, causa);
        this.fonte = fonte;
    }

    public String fonte() {
        return fonte;
    }
}
