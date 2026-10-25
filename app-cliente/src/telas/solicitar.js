import { chamarBff, mensagemDeErro } from '../api.js';
import { el, aviso, campos, formatarMoeda, formatarData } from '../dom.js';

// Solicitações abertas nesta aba, por usuário. Só conveniência de tela: a fonte da verdade é o BFF.
function lerHistorico(sub) {
  try {
    return JSON.parse(sessionStorage.getItem(`solicitacoes:${sub}`)) ?? [];
  } catch {
    return [];
  }
}

function guardarNoHistorico(sub, solicitacao) {
  try {
    const historico = [solicitacao, ...lerHistorico(sub)].slice(0, 10);
    sessionStorage.setItem(`solicitacoes:${sub}`, JSON.stringify(historico));
  } catch {
    // sem storage, a tela só não lembra do histórico
  }
}

export function telaSolicitar({ sub, acompanhar }) {
  const saida = el('div');
  const lista = el('div');

  const valor = el('input', { type: 'number', id: 'valor', min: '0.01', step: '0.01', required: true, placeholder: '18500,00' });
  const enviar = el('button', { type: 'submit', textContent: 'Solicitar' });

  function desenharHistorico() {
    const historico = lerHistorico(sub);
    if (historico.length === 0) {
      lista.replaceChildren();
      return;
    }
    lista.replaceChildren(
      el('h3', { textContent: 'Minhas solicitações nesta sessão' }),
      ...historico.map((s) => el('div', { className: 'linha' },
        el('span', { textContent: `${formatarMoeda(s.valorSolicitado)} · ${formatarData(s.dataSolicitacao)}` }),
        el('button', { className: 'secundario', textContent: 'Acompanhar', onclick: () => acompanhar(s.solicitacaoId) }),
      )),
    );
  }

  async function solicitar(evento) {
    evento.preventDefault();
    // Botão travado durante o envio: criar solicitação não é idempotente,
    // e um duplo clique abriria duas solicitações.
    enviar.disabled = true;
    saida.replaceChildren(aviso('Enviando...', 'info'));

    // clienteId não vai no corpo: o BFF tira do token, para ninguém pedir crédito em nome de outro.
    const r = await chamarBff('/v1/solicitacoes', { metodo: 'POST', corpo: { valorSolicitado: Number(valor.value) } });
    enviar.disabled = false;

    if (r.status !== 202) {
      saida.replaceChildren(aviso(mensagemDeErro(r)));
      return;
    }
    const s = r.corpo;
    guardarNoHistorico(sub, s);
    saida.replaceChildren(
      aviso('Solicitação aceita. A análise acontece por eventos e leva alguns segundos.', 'sucesso'),
      campos([
        ['Solicitação', s.solicitacaoId],
        ['Valor', formatarMoeda(s.valorSolicitado)],
        ['Data', formatarData(s.dataSolicitacao)],
      ]),
      el('button', { textContent: 'Acompanhar proposta', onclick: () => acompanhar(s.solicitacaoId) }),
    );
    valor.value = '';
    desenharHistorico();
  }

  desenharHistorico();
  return el('section', {},
    el('h2', { textContent: 'Solicitar crédito' }),
    el('form', { className: 'formulario', onsubmit: solicitar },
      el('label', { htmlFor: 'valor', textContent: 'Valor solicitado (R$)' }),
      valor,
      enviar,
    ),
    saida,
    lista,
  );
}
