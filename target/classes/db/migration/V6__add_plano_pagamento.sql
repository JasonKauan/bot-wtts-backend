-- Iteração 6: cobrança e monetização (Mercado Pago PIX)

ALTER TABLE tenant ADD COLUMN plano                VARCHAR(20) NOT NULL DEFAULT 'TRIAL';
ALTER TABLE tenant ADD COLUMN trial_expira_em      TIMESTAMP;
ALTER TABLE tenant ADD COLUMN assinatura_expira_em TIMESTAMP;

-- Tenants existentes ganham trial de 30 dias contado do cadastro original
UPDATE tenant SET trial_expira_em = criado_em + INTERVAL '30 days';

-- Correção de dados: tenants criados com horário 0-0 (bug do @Builder que ignorava
-- os defaults de campo da entidade; corrigido com @Builder.Default nesta iteração)
UPDATE tenant SET horario_abertura = 8, horario_fechamento = 18
 WHERE horario_abertura = 0 AND horario_fechamento = 0;

CREATE TABLE pagamento (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID          NOT NULL REFERENCES tenant(id),
    mercado_pago_id VARCHAR(64),
    valor           NUMERIC(10,2) NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDENTE',
    plano           VARCHAR(20)   NOT NULL,
    mes_referencia  VARCHAR(7)    NOT NULL,
    criado_em       TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pagamento_tenant ON pagamento (tenant_id);
CREATE INDEX idx_pagamento_mp_id  ON pagamento (mercado_pago_id);
