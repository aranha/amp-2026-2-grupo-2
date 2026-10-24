package br.pucminas.aed.bff.solicitacao;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.OffsetDateTime;

@RestController
@RequestMapping("/v1/solicitacoes")
public class SolicitacaoController {

    private final SolicitacaoClient credito;
    private final String canalOrigem;
    private final Clock relogio;

    public SolicitacaoController(SolicitacaoClient credito,
                                 @Value("${bff.canal-origem}") String canalOrigem,
                                 Clock relogio) {
        this.credito = credito;
        this.canalOrigem = canalOrigem;
        this.relogio = relogio;
    }

    @PostMapping
    public ResponseEntity<SolicitacaoAceita> solicitar(@RequestBody NovaSolicitacao nova,
                                                       @AuthenticationPrincipal Jwt token) {
        BigDecimal valor = nova == null ? null : nova.valorSolicitado();
        if (valor == null || valor.signum() <= 0) {
            throw new SolicitacaoInvalidaException("valorSolicitado deve ser maior que zero");
        }

        SolicitacaoRecebida recebida = credito.criar(
            token.getSubject(), valor, canalOrigem, OffsetDateTime.now(relogio));

        return ResponseEntity
            .accepted()
            .location(URI.create("/v2/propostas/" + recebida.solicitacaoId()))
            .body(new SolicitacaoAceita(
                recebida.solicitacaoId(),
                recebida.eventoId(),
                recebida.valorSolicitado(),
                recebida.dataSolicitacao()));
    }
}
