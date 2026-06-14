-- Iteração 3: adicionar tenant_id nas tabelas existentes

-- Remove o unique antigo de telefone (agora é por tenant)
ALTER TABLE bot_session DROP CONSTRAINT IF EXISTS bot_session_telefone_key;

ALTER TABLE agendamento ADD COLUMN tenant_id UUID NOT NULL REFERENCES tenant(id);
ALTER TABLE bot_session  ADD COLUMN tenant_id UUID NOT NULL REFERENCES tenant(id);

ALTER TABLE bot_session
    ADD CONSTRAINT bot_session_telefone_tenant_uq UNIQUE (telefone, tenant_id);

CREATE INDEX idx_agendamento_tenant ON agendamento (tenant_id);
CREATE INDEX idx_bot_session_tenant ON bot_session  (tenant_id);
