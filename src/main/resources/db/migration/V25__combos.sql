-- V25: combos no bot ("corte e barba" = um agendamento so, com as duracoes somadas).
-- permite_combo e configuravel por tenant; duracao_minutos na sessao guarda a soma do combo.
ALTER TABLE tenant ADD COLUMN permite_combo BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE bot_session ADD COLUMN duracao_minutos INT;
