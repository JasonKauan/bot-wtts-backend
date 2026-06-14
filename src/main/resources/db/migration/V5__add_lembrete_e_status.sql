-- Iteração 5: lembrete automático 24h antes + status "NAO_COMPARECEU"
ALTER TABLE agendamento ADD COLUMN lembrete_enviado BOOLEAN NOT NULL DEFAULT FALSE;

-- Índices para performance de consultas frequentes
CREATE INDEX IF NOT EXISTS idx_agendamento_tenant_datahora ON agendamento(tenant_id, data_hora);
CREATE INDEX IF NOT EXISTS idx_agendamento_telefone ON agendamento(cliente_telefone);
CREATE INDEX IF NOT EXISTS idx_bot_session_telefone_tenant ON bot_session(telefone, tenant_id);
