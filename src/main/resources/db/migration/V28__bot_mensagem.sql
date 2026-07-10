-- V28: historico de conversas do bot. O dono pode auditar o que o bot falou com
-- cada cliente (tela /conversas). Retencao: limpeza diaria apaga > 90 dias.
CREATE TABLE bot_mensagem (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID         NOT NULL,
    telefone     VARCHAR(30)  NOT NULL,
    cliente_nome VARCHAR(255),
    de_cliente   BOOLEAN      NOT NULL,   -- true = cliente mandou; false = o bot respondeu
    texto        TEXT         NOT NULL,
    criado_em    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_botmsg_tenant_fone ON bot_mensagem (tenant_id, telefone, criado_em DESC);
CREATE INDEX idx_botmsg_criado      ON bot_mensagem (criado_em);
