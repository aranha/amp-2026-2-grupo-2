-- Executar uma vez como usuario autorizado no schema credito.
-- Idempotente: nao altera tabelas legadas nem a tabela de escrita.
CREATE TABLE IF NOT EXISTS credito.proposta_view (
  solicitacao_id VARCHAR(36) PRIMARY KEY,
  elegivel BOOLEAN,
  valor_aprovado NUMERIC(19,2),
  elegibilidade_em TIMESTAMPTZ,
  antifraude_aprovada BOOLEAN,
  score_antifraude INTEGER,
  antifraude_em TIMESTAMPTZ,
  contrato_id VARCHAR(36),
  proposta_id VARCHAR(36),
  status_contrato VARCHAR(80),
  contrato_em TIMESTAMPTZ,
  atualizada_em TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS credito.proposta_eventos_processados (
  evento_id VARCHAR(100) NOT NULL,
  topico VARCHAR(120) NOT NULL,
  processado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (topico, evento_id)
);
-- GRANT ao usuario do servico, sem alterar o dono do schema.
GRANT SELECT, INSERT, UPDATE ON credito.proposta_view TO credito_user;
GRANT SELECT, INSERT ON credito.proposta_eventos_processados TO credito_user;
