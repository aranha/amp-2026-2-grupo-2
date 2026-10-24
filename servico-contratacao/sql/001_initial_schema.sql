CREATE TABLE IF NOT EXISTS contrato (
    solicitacao_id UUID PRIMARY KEY,
    valor_aprovado NUMERIC(19, 2) NOT NULL CHECK (valor_aprovado > 0),
    status VARCHAR(32) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS liberacao_credito (
    solicitacao_id UUID PRIMARY KEY REFERENCES contrato (solicitacao_id),
    tentativas INTEGER NOT NULL DEFAULT 0 CHECK (tentativas >= 0),
    status VARCHAR(32) NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
