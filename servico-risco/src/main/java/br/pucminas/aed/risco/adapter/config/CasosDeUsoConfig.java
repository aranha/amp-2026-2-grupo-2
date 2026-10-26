package br.pucminas.aed.risco.adapter.config;

import br.pucminas.aed.risco.application.AcompanhamentoDeReservaService;
import br.pucminas.aed.risco.application.AvaliacaoDeCreditoService;
import br.pucminas.aed.risco.application.ConsultaDeAnalisesService;
import br.pucminas.aed.risco.application.FluxoDeSolicitacoesService;
import br.pucminas.aed.risco.application.port.in.AcompanharReserva;
import br.pucminas.aed.risco.application.port.in.AgregarFluxoDeSolicitacoes;
import br.pucminas.aed.risco.application.port.in.AvaliarSolicitacao;
import br.pucminas.aed.risco.application.port.in.ConsultarAnalises;
import br.pucminas.aed.risco.application.port.out.AnalisesDeCredito;
import br.pucminas.aed.risco.application.port.out.EventosProcessados;
import br.pucminas.aed.risco.application.port.out.PublicadorDeElegibilidade;
import java.time.Clock;
import java.time.ZoneOffset;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.MatchAlwaysTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/**
 * Monta o hexagono. Os casos de uso nao conhecem Spring: sao instanciados aqui, e o Spring liga
 * cada porta de saida ao adaptador que a implementa. A transacao tambem e decidida deste lado de
 * fora, e o caso de uso nao sabe que ela existe.
 */
@Configuration
public class CasosDeUsoConfig {

    /** Os eventos do trabalho falam no horario de Brasilia. */
    @Bean
    public Clock relogio() {
        return Clock.system(ZoneOffset.of("-03:00"));
    }

    @Bean
    public AvaliarSolicitacao avaliarSolicitacao(EventosProcessados eventosProcessados, AnalisesDeCredito analises,
                                                 PublicadorDeElegibilidade publicador, Clock relogio,
                                                 PlatformTransactionManager transacoes) {
        return transacional(AvaliarSolicitacao.class,
                new AvaliacaoDeCreditoService(eventosProcessados, analises, publicador, relogio), transacoes);
    }

    @Bean
    public AcompanharReserva acompanharReserva(EventosProcessados eventosProcessados, AnalisesDeCredito analises,
                                               PlatformTransactionManager transacoes) {
        return transacional(AcompanharReserva.class,
                new AcompanhamentoDeReservaService(eventosProcessados, analises), transacoes);
    }

    @Bean
    public ConsultarAnalises consultarAnalises(AnalisesDeCredito analises) {
        return new ConsultaDeAnalisesService(analises);
    }

    @Bean
    public AgregarFluxoDeSolicitacoes agregarFluxoDeSolicitacoes(
            @Value("${app.fluxo-credito.maximo-janelas-retidas:288}") int maximoJanelasRetidas) {
        return new FluxoDeSolicitacoesService(maximoJanelasRetidas);
    }

    /** O que o {@code @Transactional} faria, aplicado de fora: cada chamada da porta e uma transacao. */
    private static <T> T transacional(Class<T> porta, T casoDeUso, PlatformTransactionManager transacoes) {
        var fabrica = new ProxyFactory(casoDeUso);
        fabrica.setInterfaces(porta);
        fabrica.addAdvice(new TransactionInterceptor(transacoes, new MatchAlwaysTransactionAttributeSource()));
        return porta.cast(fabrica.getProxy());
    }
}
