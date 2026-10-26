import { userManager } from './auth.js';

const BFF_URL = import.meta.env.VITE_BFF_URL ?? 'http://localhost:8080';

// Toda chamada ao BFF passa por aqui: anexa o token e devolve status, cabeçalhos e corpo,
// sem lançar exceção — cada tela decide o que mostrar para cada status.
export async function chamarBff(caminho, { metodo = 'GET', corpo, comToken = true } = {}) {
  const headers = {};
  if (comToken) {
    const user = await userManager.getUser();
    if (user?.access_token) headers.Authorization = `Bearer ${user.access_token}`;
  }
  if (corpo !== undefined) headers['Content-Type'] = 'application/json';

  let resp;
  try {
    resp = await fetch(`${BFF_URL}${caminho}`, {
      method: metodo,
      headers,
      body: corpo === undefined ? undefined : JSON.stringify(corpo),
    });
  } catch {
    return { status: 0, ok: false, headers: new Headers(), corpo: null };
  }

  const texto = await resp.text();
  let json = null;
  try {
    json = texto ? JSON.parse(texto) : null;
  } catch {
    // corpo que não é JSON: fica o texto
  }
  return { status: resp.status, ok: resp.ok, headers: resp.headers, corpo: json ?? texto };
}

export function mensagemDeErro(r) {
  if (r.status === 0) return `BFF indisponível em ${BFF_URL}.`;
  if (r.status === 401) return '401 — não autenticado: token ausente, expirado ou inválido.';
  if (r.status === 403) return '403 — você está autenticado, mas sua role não permite esta operação.';
  const detalhe = r.corpo?.detail ?? (typeof r.corpo === 'string' ? r.corpo : '');
  return `${r.status} — ${detalhe || 'erro inesperado'}`;
}
