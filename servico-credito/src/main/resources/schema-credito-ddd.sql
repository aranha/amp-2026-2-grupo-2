-- Executar como owner do schema credito, antes de iniciar o servico.
-- Este script fica separado para nao modificar as tabelas legadas.
CREATE TABLE IF NOT EXISTS credito.solicitacao (
    id VARCHAR(36) PRIMARY KEY,
    valor NUMERIC(19,2) NOT NULL CHECK (valor > 0),
    estado VARCHAR(12) NOT NULL DEFAULT 'CRIADA' CHECK (estado IN ('CRIADA', 'ACEITA')),
    aceita_em TIMESTAMPTZ NULL,
    CONSTRAINT aceita_em_coerente CHECK (
      (estado = 'CRIADA' AND aceita_em IS NULL) OR
      (estado = 'ACEITA' AND aceita_em IS NOT NULL)
    )
);
