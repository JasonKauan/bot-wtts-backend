-- Painel CEO: vendedores com comissão, atribuição de carteira e registro de vendas.
ALTER TABLE usuario ADD COLUMN nome         VARCHAR(120);
ALTER TABLE usuario ADD COLUMN comissao_pct NUMERIC(5,2);   -- % do vendedor (nulo p/ OWNER/SUPERADMIN)
ALTER TABLE tenant  ADD COLUMN vendedor_id  UUID;           -- quem trouxe o cliente (nulo = "da casa")

-- Cada ativação/renovação de plano pago vira uma VENDA (manual pelo painel ou PIX do cliente).
CREATE TABLE venda (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID          NOT NULL,
    tenant_nome     VARCHAR(255),
    vendedor_id     UUID,                        -- nulo = venda da casa (sem comissão)
    vendedor_email  VARCHAR(255),
    plano           VARCHAR(20)   NOT NULL,
    valor           NUMERIC(10,2) NOT NULL,
    comissao_pct    NUMERIC(5,2)  NOT NULL DEFAULT 0,   -- snapshot da % na hora da venda
    comissao_valor  NUMERIC(10,2) NOT NULL DEFAULT 0,
    origem          VARCHAR(10)   NOT NULL,      -- MANUAL | PIX
    criado_em       TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_venda_criado   ON venda (criado_em DESC);
CREATE INDEX idx_venda_vendedor ON venda (vendedor_id, criado_em DESC);
