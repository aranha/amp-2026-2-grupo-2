package br.pucminas.aed.bff.proposta;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v2/propostas")
public class PropostaV2Controller {

    private final AgregadorDePropostas agregador;

    public PropostaV2Controller(AgregadorDePropostas agregador) {
        this.agregador = agregador;
    }

    @GetMapping("/{solicitacaoId}")
    public PropostaV2 consultar(@PathVariable String solicitacaoId) {
        return agregador.agregar(solicitacaoId);
    }
}
