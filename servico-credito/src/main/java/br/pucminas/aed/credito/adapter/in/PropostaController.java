package br.pucminas.aed.credito.adapter.in;

import br.pucminas.aed.credito.application.AceitarPropostaUseCase;
import br.pucminas.aed.credito.application.PersistenciaProposta;
import br.pucminas.aed.credito.application.PropostaNaoEncontradaException;
import br.pucminas.aed.credito.domain.model.Dinheiro;
import br.pucminas.aed.credito.domain.model.PropostaAceita;
import br.pucminas.aed.credito.domain.model.PropostaJaAceitaException;
import br.pucminas.aed.credito.domain.model.SolicitacaoDeCredito;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/propostas")
public class PropostaController {
    private final PersistenciaProposta persistencia;
    private final AceitarPropostaUseCase aceitar;
    public PropostaController(PersistenciaProposta persistencia, AceitarPropostaUseCase aceitar) {
        this.persistencia = persistencia;
        this.aceitar = aceitar;
    }

    public record CriarPropostaRequest(BigDecimal valor) {}
    public record CriarPropostaResponse(String id, String estado) {}

    /** Endpoint local para testar o agregado; ainda nao publica Kafka. */
    @PostMapping
    public ResponseEntity<CriarPropostaResponse> criar(@RequestBody CriarPropostaRequest request) {
        if (request == null) throw new IllegalArgumentException("Corpo obrigatorio");
        var proposta = new SolicitacaoDeCredito(UUID.randomUUID().toString(), new Dinheiro(request.valor()));
        persistencia.salvar(proposta);
        return ResponseEntity.created(URI.create("/v1/propostas/" + proposta.id()))
            .body(new CriarPropostaResponse(proposta.id(), proposta.estado().name()));
    }

    @PostMapping("/{id}/aceitar")
    public ResponseEntity<PropostaAceita> aceitar(@PathVariable String id) {
        return ResponseEntity.ok(aceitar.executar(id));
    }

    @ExceptionHandler(PropostaJaAceitaException.class)
    public ResponseEntity<Map<String,String>> duplicada(PropostaJaAceitaException ex) {
        return ResponseEntity.status(409).body(Map.of("erro", ex.getMessage()));
    }
    @ExceptionHandler(PropostaNaoEncontradaException.class)
    public ResponseEntity<Map<String,String>> naoEncontrada(PropostaNaoEncontradaException ex) {
        return ResponseEntity.status(404).body(Map.of("erro", ex.getMessage()));
    }
    @ExceptionHandler({IllegalArgumentException.class, NullPointerException.class})
    public ResponseEntity<Map<String,String>> entradaInvalida(RuntimeException ex) {
        return ResponseEntity.badRequest().body(Map.of("erro", ex.getMessage() == null ? "Dados invalidos" : ex.getMessage()));
    }
}

@Configuration
class PropostaUseCaseConfig {
    @Bean AceitarPropostaUseCase aceitarPropostaUseCase(PersistenciaProposta persistencia) {
        return new AceitarPropostaUseCase(persistencia, Clock.systemUTC());
    }
}
