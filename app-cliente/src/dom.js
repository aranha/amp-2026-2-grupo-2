// Cria elementos com textContent, nunca innerHTML: nada vindo do token ou do BFF
// é interpretado como HTML (evita XSS, que poderia roubar o token).
export function el(tag, props = {}, ...filhos) {
  const node = Object.assign(document.createElement(tag), props);
  node.append(...filhos.filter((f) => f != null));
  return node;
}

export function aviso(texto, tipo = 'erro') {
  return el('p', { className: `aviso ${tipo}`, textContent: texto });
}

// Lista de rótulo → valor, usada nos cartões de proposta e de token.
export function campos(pares) {
  return el('dl', { className: 'campos' },
    ...pares.flatMap(([rotulo, valor]) => [el('dt', { textContent: rotulo }), el('dd', { textContent: valor })]),
  );
}

const moeda = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const percentual = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

export const formatarMoeda = (v) => (v == null ? '—' : moeda.format(v));
export const formatarTaxa = (v) => (v == null ? '—' : `${percentual.format(v * 100)}% a.m.`);
export const formatarData = (iso) => (iso ? new Date(iso).toLocaleString('pt-BR') : '—');
export const simNao = (b) => (b == null ? '—' : b ? 'Sim' : 'Não');
