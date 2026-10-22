package br.pucminas.aed.credito.application;

import br.pucminas.aed.credito.domain.model.SolicitacaoDeCreditoRepository;
import java.time.OffsetDateTime;

/** Porta adicional para transicao atomica no banco de dados. */
public interface PersistenciaProposta extends SolicitacaoDeCreditoRepository {
    boolean marcarAceitaSeCriada(String id, OffsetDateTime dataAceite);
}
