import { chamarBff, mensagemDeErro } from '../api.js';
import { el, aviso, campos, formatarMoeda, formatarTaxa, formatarData, simNao } from '../dom.js';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function cartaoV2(p) {
  return el('div', { className: 'cartao' },
    el('h3', { textContent: 'Proposta (v2)' }),
    campos([
      ['Elegível', simNao(p.elegivel)],
      ['Status do contrato', p.statusContrato ?? '—'],
      ['Valor aprovado', formatarMoeda(p.condicoes?.valorAprovado)],
      ['Taxa', formatarTaxa(p.condicoes?.taxa)],
      ['Parcelas', p.condicoes?.parcelas ?? '—'],
      ['CET', formatarTaxa(p.condicoes?.cet)],
      ['Atualizada em', formatarData(p.atualizadaEm)],
    ]),

    el('h4', { textContent: 'Parecer de risco' }),
    // Degradação: se o Risco não respondeu, o BFF omite o bloco e acende o sinalizador.
    p.parecerIndisponivel
      ? aviso('Parecer de risco indisponível no momento — o restante da proposta segue válido.', 'alerta')
      : campos([
        ['Elegível', simNao(p.parecer?.elegivel)],
        ['Motivo', p.parecer?.motivo ?? '—'],
        ['Data', formatarData(p.parecer?.dataAnalise)],
      ]),

    el('h4', { textContent: 'Antifraude' }),
    p.antifraudeIndisponivel
      ? aviso('Análise antifraude indisponível no momento — o restante da proposta segue válido.', 'alerta')
      : campos([
        ['Aprovada', simNao(p.antifraude?.aprovada)],
        ['Score', p.antifraude?.score ?? '—'],
        ['Motivo', p.antifraude?.motivo ?? '—'],
        ['Data', formatarData(p.antifraude?.dataAnalise)],
      ]),
  );
}

function cartaoV1(r) {
  return el('div', { className: 'cartao depreciado' },
    el('h3', { textContent: 'Mesma proposta na v1 (depreciada)' }),
    campos([
      ['Deprecation', r.headers.get('Deprecation') ?? '—'],
      ['Sunset', r.headers.get('Sunset') ?? '—'],
    ]),
    el('pre', { textContent: JSON.stringify(r.corpo, null, 2) }),
  );
}

export function telaConsultar({ solicitacaoId = '' }) {
  const saida = el('div', { className: 'lado-a-lado' });
  const id = el('input', { type: 'text', id: 'solicitacao', value: solicitacaoId, placeholder: '3f1c9a60-7b4e-4a21-9c0d-5e2a1b8f4d33', required: true });

  function idValido() {
    if (UUID.test(id.value.trim())) return encodeURIComponent(id.value.trim());
    saida.replaceChildren(aviso('Informe o identificador da solicitação (formato UUID).'));
    return null;
  }

  async function consultar(evento) {
    evento?.preventDefault();
    const alvo = idValido();
    if (!alvo) return;
    saida.replaceChildren(aviso('Consultando...', 'info'));

    const r = await chamarBff(`/v2/propostas/${alvo}`);
    if (r.status === 404) {
      // Consistência eventual: a solicitação foi aceita, mas a proposta ainda está sendo montada pelos eventos.
      saida.replaceChildren(aviso('Proposta ainda não disponível — o fluxo é assíncrono. Tente de novo em alguns segundos.', 'info'));
      return;
    }
    saida.replaceChildren(r.ok ? cartaoV2(r.corpo) : aviso(mensagemDeErro(r)));
  }

  async function compararComV1() {
    const alvo = idValido();
    if (!alvo) return;
    const [v2, v1] = await Promise.all([chamarBff(`/v2/propostas/${alvo}`), chamarBff(`/v1/propostas/${alvo}`)]);
    saida.replaceChildren(
      v2.ok ? cartaoV2(v2.corpo) : aviso(`v2: ${mensagemDeErro(v2)}`),
      v1.ok ? cartaoV1(v1) : aviso(`v1: ${mensagemDeErro(v1)}`),
    );
  }

  const secao = el('section', {},
    el('h2', { textContent: 'Consultar proposta' }),
    el('form', { className: 'formulario', onsubmit: consultar },
      el('label', { htmlFor: 'solicitacao', textContent: 'Identificador da solicitação' }),
      id,
      el('button', { type: 'submit', textContent: 'Consultar' }),
      el('button', { type: 'button', className: 'secundario', textContent: 'Comparar v1 × v2', onclick: compararComV1 }),
    ),
    saida,
  );
  if (solicitacaoId) consultar();
  return secao;
}
