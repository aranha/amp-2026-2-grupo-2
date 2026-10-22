package br.pucminas.aed.credito.adapter.in;

import br.pucminas.aed.credito.cqrs.PropostaProjectionService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

@Component
public class PropostaProjectionListener {
    private final PropostaProjectionService service;
    public PropostaProjectionListener(PropostaProjectionService service) { this.service = service; }
    @KafkaListener(topics={"risco.elegibilidade.v1","antifraude.analise.v1","contratacao.contrato.v1"},
        groupId="credito-proposta-view-v1", containerFactory="propostaViewKafkaFactory")
    public void receber(String payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topico,
        Acknowledgment ack) throws Exception {
        service.aplicar(topico,payload);
        ack.acknowledge();
    }
}
