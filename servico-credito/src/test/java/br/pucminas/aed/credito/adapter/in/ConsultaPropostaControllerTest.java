package br.pucminas.aed.credito.adapter.in;

import br.pucminas.aed.credito.cqrs.PropostaView;
import br.pucminas.aed.credito.cqrs.PropostaViewRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConsultaPropostaControllerTest {
    @Test void retorna404QuandoNaoHaProjecao() {
        PropostaViewRepository repo=mock(PropostaViewRepository.class);
        when(repo.buscarPorSolicitacaoId("id")).thenReturn(Optional.empty());
        var controller=new ConsultaPropostaController(repo);
        assertEquals(HttpStatus.NOT_FOUND, controller.consultar("id").getStatusCode());
        verify(repo).buscarPorSolicitacaoId("id");
    }
    @Test void consultaSomentePortaDeLeitura() {
        PropostaViewRepository repo=mock(PropostaViewRepository.class);
        PropostaView view=new PropostaView("id",true,null,null,null,null,null,null,null,null,null,null);
        when(repo.buscarPorSolicitacaoId("id")).thenReturn(Optional.of(view));
        var resposta=new ConsultaPropostaController(repo).consultar("id");
        assertEquals(HttpStatus.OK,resposta.getStatusCode());
        assertEquals("id",resposta.getBody().solicitacaoId());
    }
}
