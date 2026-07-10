-- V29: excluir profissional dava 500 — as FKs da V4 (agendamento.profissional_id e
-- bot_session.profissional_id) nao tinham regra de delecao, entao o Postgres barrava a
-- exclusao de qualquer profissional com historico. ON DELETE SET NULL: o profissional sai,
-- o historico fica (o nome ja e desnormalizado no agendamento) e o vinculo vira nulo.
-- O DROP e dinamico porque essas FKs foram criadas inline (nome autogerado pelo PG).

DO $$
DECLARE c RECORD;
BEGIN
    FOR c IN
        SELECT con.conname, rel.relname AS tabela
        FROM pg_constraint con
        JOIN pg_class rel  ON rel.oid  = con.conrelid
        JOIN pg_class frel ON frel.oid = con.confrelid
        WHERE con.contype = 'f'
          AND frel.relname = 'profissional'
          AND rel.relname IN ('agendamento', 'bot_session')
    LOOP
        EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', c.tabela, c.conname);
    END LOOP;
END $$;

ALTER TABLE agendamento ADD CONSTRAINT agendamento_profissional_id_fkey
    FOREIGN KEY (profissional_id) REFERENCES profissional(id) ON DELETE SET NULL;

ALTER TABLE bot_session ADD CONSTRAINT bot_session_profissional_id_fkey
    FOREIGN KEY (profissional_id) REFERENCES profissional(id) ON DELETE SET NULL;
