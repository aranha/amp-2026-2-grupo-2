
package br.pucminas.aed.credito.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

@Service
public class ResultadoPublicacaoService {

    private static final Logger log =
            LoggerFactory.getLogger(ResultadoPublicacaoService.class);

    public <T> void registrar(String eventoId,
                              SendResult<String, T> resultado,
                              Throwable falha) {
        if (falha != null) {
            log.error("Falha ao publicar o evento {}", eventoId, falha);
            return;
        }

        log.info("Evento {} publicado na particao {} e offset {}",
                eventoId,
                resultado.getRecordMetadata().partition(),
                resultado.getRecordMetadata().offset());
    }
}
