-- Remarcar pelo bot: guarda o agendamento que está sendo remarcado durante a conversa.
ALTER TABLE bot_session ADD COLUMN remarcando_id UUID;
