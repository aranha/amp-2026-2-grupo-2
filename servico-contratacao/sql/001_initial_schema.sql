CREATE TABLE IF NOT EXISTS contrato (
    solicitacao_id UUID PRIMARY KEY,
    proposta_id UUID NOT NULL,
    contrato_id UUID NOT NULL UNIQUE,
    valor_aprovado NUMERIC(19, 2) NOT NULL CHECK (valor_aprovado > 0),
    status VARCHAR(32) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS liberacao_credito (
    solicitacao_id UUID PRIMARY KEY REFERENCES contrato (solicitacao_id),
    contrato_id UUID NOT NULL,
    valor_liberado NUMERIC(19, 2) NOT NULL CHECK (valor_liberado > 0),
    tentativas INTEGER NOT NULL DEFAULT 0 CHECK (tentativas >= 0),
    status VARCHAR(32) NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS evento_processado (
    evento_id UUID PRIMARY KEY,
    processado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
