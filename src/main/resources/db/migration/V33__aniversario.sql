-- V33 (Diamond): aniversario do cliente (dd/mm, cadastrado pelo dono no CRM) + mensagem
-- automatica de parabens 1x por ano.
ALTER TABLE tenant ADD COLUMN aniversario_ativo BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tenant ADD COLUMN aniversario_msg TEXT;   -- nulo = template padrao

CREATE TABLE aniversario (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID        NOT NULL,
    telefone     VARCHAR(30) NOT NULL,
    nome         VARCHAR(255),
    dia          INT         NOT NULL,
    mes          INT         NOT NULL,
    ultimo_envio DATE,
    criado_em    TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_aniversario UNIQUE (tenant_id, telefone)
);
CREATE INDEX idx_aniversario_data ON aniversario (mes, dia);
