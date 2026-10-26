import { el, campos } from '../dom.js';

export function telaToken(payload, accessToken) {
  const situacao = el('span', { className: 'nota' });
  const bruto = el('textarea', { id: 'token-bruto', readOnly: true, rows: 4, value: accessToken, hidden: true });

  // Para o plano B da demo (curl com TOKEN_ANA / TOKEN_MARIA). O token já é do próprio usuário;
  // o botão só evita garimpar o sessionStorage no DevTools.
  async function copiar() {
    try {
      await navigator.clipboard.writeText(accessToken);
      situacao.textContent = 'Copiado. Vale até o horário de expiração acima.';
    } catch {
      bruto.hidden = false;
      bruto.select();
      situacao.textContent = 'Não deu para copiar automaticamente: selecione o texto abaixo e copie.';
    }
  }

  return el('section', {},
    el('h2', { textContent: 'Meu token' }),
    el('p', { className: 'nota', textContent: 'O payload do JWT é só base64url: qualquer um que tenha o token consegue ler. Por isso ele não carrega CPF nem email.' }),
    campos([
      ['Emissor (iss)', payload.iss],
      ['Audience (aud)', [].concat(payload.aud).join(', ')],
      ['Client (azp)', payload.azp],
      ['Expira às', new Date(payload.exp * 1000).toLocaleTimeString('pt-BR')],
    ]),
    el('div', { className: 'acoes' },
      el('button', { className: 'secundario', textContent: 'Copiar access token', onclick: copiar }),
      situacao,
    ),
    bruto,
    el('pre', { textContent: JSON.stringify(payload, null, 2) }),
  );
}
