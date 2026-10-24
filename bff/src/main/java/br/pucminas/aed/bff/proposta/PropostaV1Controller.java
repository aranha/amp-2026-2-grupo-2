package br.pucminas.aed.bff.proposta;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/propostas")
public class PropostaV1Controller {

    private final AgregadorDePropostas agregador;
    private final String deprecation;
    private final String sunset;

    public PropostaV1Controller(AgregadorDePropostas agregador,
                                @Value("${bff.versionamento.deprecation}") String deprecation,
                                @Value("${bff.versionamento.sunset}") String sunset) {
        this.agregador = agregador;
        this.deprecation = deprecation;
        this.sunset = sunset;
    }

    @GetMapping("/{solicitacaoId}")
    public ResponseEntity<PropostaV1> consultar(@PathVariable String solicitacaoId) {
        return ResponseEntity.ok()
            .header("Deprecation", deprecation)
            .header("Sunset", sunset)
            .header(HttpHeaders.LINK, "</v2/propostas/" + solicitacaoId + ">; rel=\"successor-version\"")
            .body(PropostaV1.de(agregador.agregar(solicitacaoId)));
    }
}
