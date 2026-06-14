-- Iteração 4: Profissional + campos de horário no Tenant + profissional no bot

CREATE TABLE profissional (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID         NOT NULL REFERENCES tenant(id),
    nome       VARCHAR(255) NOT NULL,
    ativo      BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_profissional_tenant ON profissional (tenant_id);

-- Horário de atendimento por tenant (antes hardcoded no BotService)
ALTER TABLE tenant ADD COLUMN horario_abertura   INT NOT NULL DEFAULT 8;
ALTER TABLE tenant ADD COLUMN horario_fechamento INT NOT NULL DEFAULT 18;

-- Profissional no Agendamento
ALTER TABLE agendamento ADD COLUMN profissional_id UUID REFERENCES profissional(id);
ALTER TABLE agendamento ADD COLUMN profissional    VARCHAR(255);

-- Profissional na sessão do bot
ALTER TABLE bot_session ADD COLUMN profissional_id       UUID REFERENCES profissional(id);
ALTER TABLE bot_session ADD COLUMN profissional_escolhido VARCHAR(255);
