package br.pucminas.aed.risco.adapter.in.kafka;

import br.pucminas.aed.risco.application.port.in.AvaliarSolicitacao;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Adaptador de entrada: traduz o registro Kafka e chama a porta. Nenhuma regra mora aqui.
 *
 * <p>Grupo padrao, {@code risco-credito-v1}. Assina {@code credito.solicitacao.v1}, o topico do
 * contrato, e o nome herdado da AED, onde o servico-credito ainda publica: os dois convivem
 * durante a migracao, e o dedup por {@code ce_id} cobre quem publicar nos dois.
 */
@Component
public class SolicitacaoDeCreditoListener {

    private final AvaliarSolicitacao avaliarSolicitacao;
    private final ObjectMapper json;

    public SolicitacaoDeCreditoListener(AvaliarSolicitacao avaliarSolicitacao, ObjectMapper json) {
        this.avaliarSolicitacao = avaliarSolicitacao;
        this.json = json;
    }

    @KafkaListener(topics = "#{'${app.kafka.topico.solicitacao}'.split(',')}")
    public void receber(ConsumerRecord<String, String> registro, Acknowledgment confirmacao)
            throws JsonProcessingException {
        String eventoId = CargaDoEvento.idDoEvento(registro);
        avaliarSolicitacao.avaliar(eventoId, CargaDoEvento.solicitacao(json, registro.value()));
        confirmacao.acknowledge();
    }
}
