-- Acerto de comissões: CEO marca as comissões do vendedor como pagas (controle mensal).
ALTER TABLE venda ADD COLUMN pago    BOOLEAN   NOT NULL DEFAULT false;
ALTER TABLE venda ADD COLUMN pago_em TIMESTAMP;

CREATE INDEX idx_venda_pago ON venda (pago, vendedor_id);
