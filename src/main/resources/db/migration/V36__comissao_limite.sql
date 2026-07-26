-- V36: limite de comissao por cliente. O vendedor ganha comissao apenas nas N primeiras
-- mensalidades PAGAS de cada cliente que ele trouxe; dali em diante a mensalidade e 100%
-- da casa. Contagem por par (cliente, vendedor) — cada cliente novo reinicia o contador.
-- 0 = ilimitado (comissao vitalicia, comportamento anterior).

ALTER TABLE usuario ADD COLUMN comissao_meses INT NOT NULL DEFAULT 2;

-- Vendedores que ja existem mantem o combinado atual (vitalicio) ate o CEO ajustar na tela.
UPDATE usuario SET comissao_meses = 0 WHERE role = 'VENDEDOR';
