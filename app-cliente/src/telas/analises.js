import { chamarBff, mensagemDeErro } from '../api.js';
import { el, aviso, formatarMoeda, formatarData, simNao } from '../dom.js';

function tabela(analises) {
  return el('table', {},
    el('thead', {}, el('tr', {},
      ...['Solicitação', 'Elegível', 'Valor aprovado', 'Motivo', 'Data'].map((t) => el('th', { textContent: t })),
    )),
    el('tbody', {}, ...analises.map((a) => el('tr', {},
      el('td', { textContent: a.solicitacaoId }),
      el('td', { textContent: simNao(a.elegivel) }),
      el('td', { textContent: formatarMoeda(a.valorAprovado) }),
      el('td', { textContent: a.motivo ?? '—' }),
      el('td', { textContent: formatarData(a.dataAnalise) }),
    ))),
  );
}

export function telaAnalises() {
  const saida = el('div');

  async function carregar(comToken) {
    saida.replaceChildren(aviso('Consultando...', 'info'));
    const r = await chamarBff('/v1/analises', { comToken });
    if (!r.ok) {
      saida.replaceChildren(aviso(mensagemDeErro(r), r.status === 403 ? 'alerta' : 'erro'));
      return;
    }
    saida.replaceChildren(
      // consultadoPor vem do token validado pelo BFF, não de algo que a tela enviou.
      aviso(`200 — consultado por ${r.corpo.consultadoPor}`, 'sucesso'),
      r.corpo.analises.length ? tabela(r.corpo.analises) : aviso('Nenhuma análise.', 'info'),
    );
  }

  return el('section', {},
    el('h2', { textContent: 'Análises de risco' }),
    el('p', { className: 'nota', textContent: 'Rota restrita à role analista. Com outra role o BFF responde 403; sem token, 401.' }),
    el('div', { className: 'acoes' },
      el('button', { textContent: 'Carregar análises', onclick: () => carregar(true) }),
      el('button', { className: 'secundario', textContent: 'Chamar sem token (demo 401)', onclick: () => carregar(false) }),
    ),
    saida,
  );
}
