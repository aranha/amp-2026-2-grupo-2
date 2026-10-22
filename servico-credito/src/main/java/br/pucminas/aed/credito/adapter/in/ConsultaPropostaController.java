package br.pucminas.aed.credito.adapter.in;

import br.pucminas.aed.credito.cqrs.PropostaView;
import br.pucminas.aed.credito.cqrs.PropostaViewRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Nao injeta repository do agregado: GET acessa SOMENTE a read model. */
@RestController
@RequestMapping("/v1/propostas")
public class ConsultaPropostaController {
    private final PropostaViewRepository views;
    public ConsultaPropostaController(PropostaViewRepository views) { this.views = views; }
    @GetMapping("/{id}")
    public ResponseEntity<PropostaView> consultar(@PathVariable String id) {
        return views.buscarPorSolicitacaoId(id).map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
