package br.pucminas.aed.risco.adapter.in.web;

import br.pucminas.aed.risco.application.port.in.ConsultarAnalises;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adaptador de entrada HTTP. Interno a rede dos servicos: quem fala com o mundo e o BFF, que exige
 * a role {@code analista} em {@code GET /v1/analises} e busca o parecer de uma proposta aqui.
 */
@RestController
@RequestMapping("/v1/analises")
@Tag(name = "Análises", description = "Pareceres de elegibilidade do contexto de Risco")
public class AnalisesController {

    private final ConsultarAnalises consulta;

    public AnalisesController(ConsultarAnalises consulta) {
        this.consulta = consulta;
    }

    @GetMapping
    @Operation(summary = "Lista os pareceres, os mais recentes primeiro")
    public ListaDeAnalises listar(
            @Parameter(description = "Quantos pareceres devolver, de 1 a 200")
            @RequestParam(defaultValue = "50") int limite) {
        return new ListaDeAnalises(consulta.listarRecentes(limite).stream().map(AnaliseResposta::de).toList());
    }

    /**
     * Id fora do formato responde 404, e nao 400: para o circuit breaker do BFF, 404 e resposta,
     * e uma rajada de ids errados nao deve abrir o circuito de um servico saudavel.
     */
    @GetMapping("/{solicitacaoId}")
    @Operation(summary = "Parecer de uma solicitação")
    public AnaliseResposta buscar(@PathVariable String solicitacaoId) {
        return comoUuid(solicitacaoId).flatMap(consulta::buscar).map(AnaliseResposta::de)
                .orElseThrow(() -> new AnaliseNaoEncontradaException(solicitacaoId));
    }

    @ExceptionHandler(AnaliseNaoEncontradaException.class)
    public ProblemDetail naoEncontrada(AnaliseNaoEncontradaException erro) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, erro.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail pedidoInvalido(IllegalArgumentException erro) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, erro.getMessage());
    }

    private static Optional<UUID> comoUuid(String valor) {
        try {
            UUID id = UUID.fromString(valor);
            return id.toString().equalsIgnoreCase(valor) ? Optional.of(id) : Optional.empty();
        } catch (IllegalArgumentException formatoInvalido) {
            return Optional.empty();
        }
    }
}
