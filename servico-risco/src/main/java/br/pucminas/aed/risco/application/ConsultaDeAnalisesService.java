package br.pucminas.aed.risco.application;

import br.pucminas.aed.risco.application.port.in.ConsultarAnalises;
import br.pucminas.aed.risco.application.port.out.AnalisesDeCredito;
import br.pucminas.aed.risco.domain.AnaliseDeCredito;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ConsultaDeAnalisesService implements ConsultarAnalises {

    private final AnalisesDeCredito analises;

    public ConsultaDeAnalisesService(AnalisesDeCredito analises) {
        this.analises = Objects.requireNonNull(analises);
    }

    @Override
    public List<AnaliseDeCredito> listarRecentes(int limite) {
        if (limite < 1 || limite > LIMITE_MAXIMO) {
            throw new IllegalArgumentException("limite deve ficar entre 1 e " + LIMITE_MAXIMO);
        }
        return analises.listarRecentes(limite);
    }

    @Override
    public Optional<AnaliseDeCredito> buscar(UUID solicitacaoId) {
        return analises.buscar(solicitacaoId);
    }
}
