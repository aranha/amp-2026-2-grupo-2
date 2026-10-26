-- Schema risco. Sem prefixo de schema: o search_path do risco_user ja aponta para ele.

create table if not exists evento_processado (
    evento_id varchar(64) primary key,
    processado_em timestamp with time zone not null default current_timestamp
);

-- Uma linha por solicitacao: a chave primaria e a invariante "uma analise por solicitacao".
-- status e a situacao da reserva de limite, herdada da AED: PENDENTE, RESERVADA ou CANCELADA.
create table if not exists analise_credito (
    solicitacao_id varchar(36) primary key,
    cliente_id varchar(80) not null,
    valor_solicitado decimal(19, 2) not null,
    solicitada_em timestamp with time zone not null,
    elegivel boolean not null,
    valor_aprovado decimal(19, 2) not null,
    motivo varchar(200) not null,
    analisada_em timestamp with time zone not null,
    status varchar(30) not null
);

create index if not exists analise_credito_analisada_em on analise_credito (analisada_em desc);
