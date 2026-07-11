-- V32 (Diamond): reativacao de clientes sumidos. Cliente sem visita ha N dias e sem
-- horario futuro recebe um "sentimos sua falta" (1x a cada 60 dias, max 10/dia por tenant).
ALTER TABLE tenant ADD COLUMN reativacao_dias INT NOT NULL DEFAULT 0;   -- 0 = desligado
ALTER TABLE tenant ADD COLUMN reativacao_msg TEXT;                      -- nulo = template padrao

CREATE TABLE reativacao (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID        NOT NULL,
    telefone   VARCHAR(30) NOT NULL,
    enviado_em TIMESTAMP   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_reativacao_tenant ON reativacao (tenant_id, telefone, enviado_em DESC);
