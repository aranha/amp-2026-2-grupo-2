# Diagramas C4

Dono: **Paulo** (N1, N2, N3 do BFF) · **Vinicius** (N3 do crédito)

| Nível | Diagrama | O que mostra | Estado |
|---|---|---|---|
| 1 — Contexto | [`n1-contexto`](README-n1-contexto.md) | Cliente, Analista, o sistema, Keycloak e Core Bancário | ✅ |
| 2 — Container | [`n2-container`](README-n2-container.md) | SPA, BFF, 4 serviços, Kafka, Postgres, Keycloak, stack OTel | ✅ |
| 3 — Componente | [`n3-componente-bff`](README-n3-bff.md) | Camadas do BFF: segurança, controllers, agregador e clients com Resilience4j | ✅ |
| 3 — Componente | [`n3-componente-credito`](README-n3-credito.md) | Clean Architecture do crédito: domínio, aplicação, adaptadores | ✅ |

Cada diagrama tem três arquivos: o `.drawio` editável em [diagrams.net](https://app.diagrams.net),
o `.png` para os slides, e um `README-*.md` que explica o desenho e renderiza o Mermaid no GitHub.

Checklist B7, B8 e B9.

> **Antifraude e risco não são sistemas externos.** São contextos internos — aparecem como
> containers no N2, não como caixas de terceiros no N1. O desenho do N1 reflete o que foi
> construído: os únicos sistemas de fora são o Keycloak e o mock do core bancário.

## Regerar os PNG

O Mermaid é a fonte do PNG e mora no `README-*.md` do diagrama, para renderizar aqui no GitHub.
Para regerar a imagem, extraia o bloco e passe para o renderizador:

```bash
sed -n '/^```mermaid/,/^```$/p' README-n1-contexto.md | sed '1d;$d' > /tmp/n1.mmd
npx @mermaid-js/mermaid-cli -i /tmp/n1.mmd -o n1-contexto.png -b white -s 3
```

Quem preferir editar no diagrams.net mexe no `.drawio` e exporta o PNG por lá. Mexendo em um dos
dois, atualize o outro: o `.drawio` e o Mermaid descrevem o mesmo desenho.
