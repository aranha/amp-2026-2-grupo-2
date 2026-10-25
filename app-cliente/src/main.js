import { userManager, decodificarPayload } from './auth.js';
import { el } from './dom.js';
import { telaSolicitar } from './telas/solicitar.js';
import { telaConsultar } from './telas/consultar.js';
import { telaAnalises } from './telas/analises.js';
import { telaToken } from './telas/token.js';

const app = document.getElementById('app');
const ROLES_DE_NEGOCIO = ['cliente', 'analista', 'operador'];

// Todas as abas aparecem para todos. Esconder menu é conveniência, não segurança: quem decide
// é o BFF — e a demo do 403 precisa que a Maria consiga tentar abrir as análises.
const ABAS = [
  { id: 'solicitar', rotulo: 'Solicitar crédito' },
  { id: 'consultar', rotulo: 'Consultar proposta' },
  { id: 'analises', rotulo: 'Análises' },
  { id: 'token', rotulo: 'Meu token' },
];

function telaDeslogado() {
  app.replaceChildren(
    el('h1', { textContent: 'Concessão de Crédito' }),
    el('p', { textContent: 'Você não está autenticado.' }),
    el('button', { textContent: 'Entrar', onclick: () => userManager.signinRedirect() }),
  );
}

function telaLogado(user) {
  const payload = decodificarPayload(user.access_token);
  const roles = (payload.realm_access?.roles ?? []).filter((r) => ROLES_DE_NEGOCIO.includes(r));
  const conteudo = el('div');
  const botoes = new Map();

  function abrir(aba, opcoes = {}) {
    botoes.forEach((botao, id) => botao.setAttribute('aria-selected', String(id === aba)));
    const telas = {
      solicitar: () => telaSolicitar({ sub: payload.sub, acompanhar: (id) => abrir('consultar', { solicitacaoId: id }) }),
      consultar: () => telaConsultar(opcoes),
      analises: () => telaAnalises(),
      token: () => telaToken(payload),
    };
    conteudo.replaceChildren(telas[aba]());
  }

  for (const aba of ABAS) {
    botoes.set(aba.id, el('button', { className: 'aba', role: 'tab', textContent: aba.rotulo, onclick: () => abrir(aba.id) }));
  }

  app.replaceChildren(
    el('header', { className: 'topo' },
      el('h1', { textContent: 'Concessão de Crédito' }),
      el('div', { className: 'usuario' },
        el('span', { textContent: payload.name ?? payload.preferred_username }),
        ...roles.map((r) => el('span', { className: 'role', textContent: r })),
        el('button', { textContent: 'Sair', className: 'secundario', onclick: () => userManager.signoutRedirect() }),
      ),
    ),
    el('nav', { className: 'abas', role: 'tablist' }, ...botoes.values()),
    conteudo,
  );
  abrir(roles.includes('analista') ? 'analises' : 'solicitar');
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
