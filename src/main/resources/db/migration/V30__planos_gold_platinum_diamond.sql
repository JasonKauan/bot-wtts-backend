-- V30: planos renomeados e reprecificados — BASICO->GOLD (39,90), PRO->PLATINUM (79,90),
-- PLUS->DIAMOND (119,90). Clientes existentes so mudam de nome (datas intactas) e passam
-- a pagar MENOS na renovacao — ninguem e prejudicado.

UPDATE tenant SET plano = 'GOLD'     WHERE plano = 'BASICO';
UPDATE tenant SET plano = 'PLATINUM' WHERE plano = 'PRO';
UPDATE tenant SET plano = 'DIAMOND'  WHERE plano = 'PLUS';

UPDATE pagamento SET plano = 'GOLD'     WHERE plano = 'BASICO';
UPDATE pagamento SET plano = 'PLATINUM' WHERE plano = 'PRO';
UPDATE pagamento SET plano = 'DIAMOND'  WHERE plano = 'PLUS';

UPDATE venda SET plano = 'GOLD'     WHERE plano = 'BASICO';
UPDATE venda SET plano = 'PLATINUM' WHERE plano = 'PRO';
UPDATE venda SET plano = 'DIAMOND'  WHERE plano = 'PLUS';
