-- Iteração 3: multi-tenant

CREATE TABLE tenant (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    nome              VARCHAR(255) NOT NULL,
    telefone_whatsapp VARCHAR(50),
    webhook_secret    VARCHAR(255) NOT NULL,
    ativo             BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em         TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE usuario (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID         NOT NULL REFERENCES tenant(id),
    email      VARCHAR(255) NOT NULL UNIQUE,
    senha      VARCHAR(255) NOT NULL,
    role       VARCHAR(50)  NOT NULL DEFAULT 'OWNER',
    ativo      BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Substitui a lista hardcoded de serviços da Iteração 1
CREATE TABLE servico (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID         NOT NULL REFERENCES tenant(id),
    nome             VARCHAR(255) NOT NULL,
    duracao_minutos  INTEGER      NOT NULL DEFAULT 30,
    ativo            BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em        TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_usuario_tenant     ON usuario (tenant_id);
CREATE INDEX idx_usuario_email      ON usuario (email);
CREATE INDEX idx_servico_tenant     ON servico (tenant_id);
