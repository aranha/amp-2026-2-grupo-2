import { el, campos } from '../dom.js';

export function telaToken(payload) {
  return el('section', {},
    el('h2', { textContent: 'Meu token' }),
    el('p', { className: 'nota', textContent: 'O payload do JWT é só base64url: qualquer um que tenha o token consegue ler. Por isso ele não carrega CPF.' }),
    campos([
      ['Emissor (iss)', payload.iss],
      ['Audience (aud)', [].concat(payload.aud).join(', ')],
      ['Client (azp)', payload.azp],
      ['Expira às', new Date(payload.exp * 1000).toLocaleTimeString('pt-BR')],
    ]),
    el('pre', { textContent: JSON.stringify(payload, null, 2) }),
  );
}
