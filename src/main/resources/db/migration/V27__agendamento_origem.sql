-- V27: origem do agendamento (BOT | MANUAL | FIXO) — base do card de ROI na dashboard
-- ("o bot agendou N horarios = R$ X"). Historico antigo vira MANUAL.
ALTER TABLE agendamento ADD COLUMN origem VARCHAR(10) NOT NULL DEFAULT 'MANUAL';
CREATE INDEX idx_agendamento_origem ON agendamento (tenant_id, origem, criado_em DESC);
