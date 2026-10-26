package br.pucminas.aed.risco.application.port.in;

import br.pucminas.aed.risco.domain.AnaliseDeCredito;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Porta de entrada: leitura dos pareceres, que o BFF expoe ao analista. */
public interface ConsultarAnalises {

    int LIMITE_MAXIMO = 200;

    /** As mais recentes primeiro. */
    List<AnaliseDeCredito> listarRecentes(int limite);

    Optional<AnaliseDeCredito> buscar(UUID solicitacaoId);
}
