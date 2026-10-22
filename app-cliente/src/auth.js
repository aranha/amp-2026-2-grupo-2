import { UserManager, WebStorageStateStore } from 'oidc-client-ts';

// Client público: só o client_id, nenhum secret. A prova de posse vem do PKCE,
// que a oidc-client-ts gera sozinha a cada login (code_verifier + code_challenge S256).
export const userManager = new UserManager({
  authority: import.meta.env.VITE_OIDC_AUTHORITY ?? 'http://localhost:8180/realms/credito',
  client_id: 'app-cliente',
  redirect_uri: `${window.location.origin}/callback`,
  post_logout_redirect_uri: `${window.location.origin}/`,
  response_type: 'code',
  scope: 'openid profile email',
  // sessionStorage: o token morre junto com a aba, em vez de ficar guardado no navegador.
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
});

// O payload do JWT é só base64url: qualquer um lê, sem chave nenhuma.
// Por isso o token não carrega CPF nem outro dado sensível.
export function decodificarPayload(jwt) {
  const base64 = jwt.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
  const json = new TextDecoder().decode(Uint8Array.from(atob(base64), (c) => c.charCodeAt(0)));
  return JSON.parse(json);
}
