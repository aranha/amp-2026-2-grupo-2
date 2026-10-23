from uuid import UUID

import psycopg

from domain.analise_antifraude import AnaliseAntifraude

ESQUEMA = """
create table if not exists analise_antifraude (
    evento_origem_id uuid primary key,
    evento_id uuid not null unique,
    solicitacao_id uuid not null,
    score integer not null check (score between 0 and 100),
    aprovada boolean not null,
    analisada_em timestamptz not null
)
"""


class RepositorioPostgres:
    def __init__(self, url: str) -> None:
        self._url = url

    def criar_esquema(self) -> None:
        with self._conectar() as conexao:
            conexao.execute(ESQUEMA)

    def registrar(self, evento_origem_id: UUID, analise: AnaliseAntifraude) -> AnaliseAntifraude:
        with self._conectar() as conexao:
            conexao.execute(
                """
                insert into analise_antifraude
                    (evento_origem_id, evento_id, solicitacao_id, score, aprovada, analisada_em)
                values (%s, %s, %s, %s, %s, %s)
                on conflict (evento_origem_id) do nothing
                """,
                (evento_origem_id, analise.id, analise.solicitacao_id, analise.score,
                 analise.aprovada, analise.analisada_em),
            )
            linha = conexao.execute(
                """
                select evento_id, solicitacao_id, score, aprovada, analisada_em
                  from analise_antifraude
                 where evento_origem_id = %s
                """,
                (evento_origem_id,),
            ).fetchone()
        return AnaliseAntifraude(*linha)

    def _conectar(self) -> psycopg.Connection:
        return psycopg.connect(self._url, connect_timeout=10)
