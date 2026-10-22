import { userManager, decodificarPayload } from './auth.js';

const BFF_URL = import.meta.env.VITE_BFF_URL ?? 'http://localhost:8080';
const app = document.getElementById('app');

// Cria elementos com textContent, nunca innerHTML: nada vindo do token ou do BFF
// é interpretado como HTML (evita XSS, que poderia roubar o token).
function el(tag, props = {}, ...filhos) {
  const node = Object.assign(document.createElement(tag), props);
  node.append(...filhos);
  return node;
}

async function chamarBff(caminho, comToken) {
  const saida = document.getElementById('resposta');
  saida.textContent = '...';
  const headers = {};
  if (comToken) {
    const user = await userManager.getUser();
    headers.Authorization = `Bearer ${user.access_token}`;
  }
  try {
    const resp = await fetch(`${BFF_URL}${caminho}`, { headers });
    const corpo = await resp.text();
    saida.textContent = `GET ${caminho} → ${resp.status} ${resp.statusText}\n\n${corpo}`;
  } catch (erro) {
    saida.textContent = `GET ${caminho} → BFF indisponível em ${BFF_URL} (${erro.message})`;
  }
}

function telaDeslogado() {
  app.replaceChildren(
    el('h1', { textContent: 'Concessão de Crédito' }),
    el('p', { textContent: 'Você não está autenticado.' }),
    el('button', { textContent: 'Entrar', onclick: () => userManager.signinRedirect() }),
  );
}

function telaLogado(user) {
  const payload = decodificarPayload(user.access_token);
  const roles = payload.realm_access?.roles ?? [];
  const expira = new Date(payload.exp * 1000).toLocaleTimeString('pt-BR');

  app.replaceChildren(
    el('h1', { textContent: 'Concessão de Crédito' }),
    el('p', {}, 'Olá, ', el('strong', { textContent: payload.name ?? payload.preferred_username })),
    el('ul', {},
      el('li', { textContent: `Roles: ${roles.filter((r) => ['cliente', 'analista', 'operador'].includes(r)).join(', ') || '—'}` }),
      el('li', { textContent: `Audience: ${[].concat(payload.aud).join(', ')}` }),
      el('li', { textContent: `Token expira às ${expira}` }),
    ),
    el('div', { className: 'acoes' },
      el('button', { textContent: 'GET /v1/analises com token', onclick: () => chamarBff('/v1/analises', true) }),
      el('button', { textContent: 'GET /v1/analises sem token', onclick: () => chamarBff('/v1/analises', false) }),
      el('button', { textContent: 'Sair', className: 'secundario', onclick: () => userManager.signoutRedirect() }),
    ),
    el('pre', { id: 'resposta' }),
    el('details', {},
      el('summary', { textContent: 'Conteúdo do access token' }),
      el('pre', { textContent: JSON.stringify(payload, null, 2) }),
    ),
  );
}

async function iniciar() {
  // Volta do Keycloak: troca o ?code= pelo token, enviando o code_verifier guardado.
  if (window.location.pathname === '/callback') {
    try {
      await userManager.signinRedirectCallback();
    } catch (erro) {
      console.error('Falha no retorno do login', erro);
    }
    window.history.replaceState({}, '', '/');
  }

  const user = await userManager.getUser();
  if (user && !user.expired) telaLogado(user);
  else telaDeslogado();
}

iniciar();
