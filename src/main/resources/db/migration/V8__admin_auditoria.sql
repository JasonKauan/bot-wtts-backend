-- Fase 3 do painel admin: trilha de auditoria das ações do back-office (vendedor).
CREATE TABLE admin_auditoria (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id    UUID,                       -- usuario.id do SUPERADMIN que agiu
    admin_email VARCHAR(255),               -- denormalizado, pra mostrar sem join
    acao        VARCHAR(40)  NOT NULL,       -- CRIAR_CLIENTE | ALTERAR_PLANO | RESETAR_SENHA | SUSPENDER | REATIVAR
    tenant_id   UUID,                        -- cliente alvo (pode ser nulo)
    tenant_nome VARCHAR(255),                -- denormalizado
    detalhe     VARCHAR(500),                -- texto livre (ex.: "plano=PRO modo=meses meses=3")
    criado_em   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_admin_auditoria_criado ON admin_auditoria (criado_em DESC);
