package br.pucminas.aed.risco.adapter.in.kafka;

import br.pucminas.aed.risco.application.port.in.AgregarFluxoDeSolicitacoes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Segundo grupo sobre os mesmos topicos de solicitacao: recebe cada evento de forma independente
 * do grupo da analise, sem roubar o evento dele, e confirma o proprio offset.
 */
@Component
public class FluxoDeSolicitacoesListener {

    private final AgregarFluxoDeSolicitacoes agregarFluxo;
    private final ObjectMapper json;

    public FluxoDeSolicitacoesListener(AgregarFluxoDeSolicitacoes agregarFluxo, ObjectMapper json) {
        this.agregarFluxo = agregarFluxo;
        this.json = json;
    }

    @KafkaListener(topics = "#{'${app.kafka.topico.solicitacao}'.split(',')}",
            groupId = "${app.kafka.grupo.fluxo-credito}")
    public void receber(ConsumerRecord<String, String> registro, Acknowledgment confirmacao)
            throws JsonProcessingException {
        String eventoId = CargaDoEvento.idDoEvento(registro);
        agregarFluxo.agregar(eventoId, CargaDoEvento.solicitacao(json, registro.value()));
        confirmacao.acknowledge();
    }
}
