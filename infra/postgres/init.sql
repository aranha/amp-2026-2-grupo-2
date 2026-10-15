-- Database per Service, versão lógica: um schema e um usuário por serviço,
-- cada um com GRANT só no próprio schema. Nenhum JOIN cruza fronteira.
--
-- Demonstrável ao vivo:
--   \c amp credito_user
--   SELECT * FROM risco.analise;  -->  ERROR: permission denied for schema risco

CREATE SCHEMA IF NOT EXISTS credito;
CREATE SCHEMA IF NOT EXISTS risco;
CREATE SCHEMA IF NOT EXISTS antifraude;
CREATE SCHEMA IF NOT EXISTS contratacao;

CREATE USER credito_user     WITH PASSWORD 'credito';
CREATE USER risco_user       WITH PASSWORD 'risco';
CREATE USER antifraude_user  WITH PASSWORD 'antifraude';
CREATE USER contratacao_user WITH PASSWORD 'contratacao';

-- Cada usuário é dono do seu schema e não enxerga os outros.
ALTER SCHEMA credito     OWNER TO credito_user;
ALTER SCHEMA risco       OWNER TO risco_user;
ALTER SCHEMA antifraude  OWNER TO antifraude_user;
ALTER SCHEMA contratacao OWNER TO contratacao_user;

-- Revoga o acesso implícito do schema public.
REVOKE ALL ON SCHEMA public FROM PUBLIC;

ALTER ROLE credito_user     SET search_path TO credito;
ALTER ROLE risco_user       SET search_path TO risco;
ALTER ROLE antifraude_user  SET search_path TO antifraude;
ALTER ROLE contratacao_user SET search_path TO contratacao;
