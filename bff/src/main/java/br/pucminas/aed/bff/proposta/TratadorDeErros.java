package br.pucminas.aed.bff.proposta;

import br.pucminas.aed.bff.solicitacao.SolicitacaoInvalidaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class TratadorDeErros {

    @ExceptionHandler(PropostaNaoEncontradaException.class)
    public ProblemDetail naoEncontrada(PropostaNaoEncontradaException erro) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, erro.getMessage());
    }

    @ExceptionHandler(SolicitacaoInvalidaException.class)
    public ProblemDetail solicitacaoInvalida(SolicitacaoInvalidaException erro) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, erro.getMessage());
    }

    @ExceptionHandler(FonteIndisponivelException.class)
    public ProblemDetail fonteIndisponivel(FonteIndisponivelException erro) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE, erro.getMessage());
        problema.setProperty("fonte", erro.fonte());
        return problema;
    }
}
