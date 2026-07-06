-- V19: acerto PARCIAL de comissoes + historico de acertos.

-- Quanto da comissao desta venda ja foi pago em acertos parciais (a venda so vira
-- pago=true quando a comissao inteira for quitada).
ALTER TABLE venda ADD COLUMN comissao_paga_parcial NUMERIC(10,2) NOT NULL DEFAULT 0;

-- Cada "marcar como pago" (total ou parcial) vira um registro de historico.
CREATE TABLE acerto_comissao (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    vendedor_id     UUID          NOT NULL,
    vendedor_nome   VARCHAR(255),                        -- snapshot (nome ou email na hora)
    valor           NUMERIC(10,2) NOT NULL,              -- quanto foi pago neste acerto
    vendas_quitadas INT           NOT NULL DEFAULT 0,    -- vendas cuja comissao foi quitada por inteiro
    pendente_apos   NUMERIC(10,2) NOT NULL DEFAULT 0,    -- quanto ainda ficou devendo
    criado_em       TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_acerto_vendedor ON acerto_comissao (vendedor_id, criado_em DESC);
CREATE INDEX idx_acerto_criado   ON acerto_comissao (criado_em DESC);
