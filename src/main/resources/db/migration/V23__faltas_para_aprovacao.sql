-- V23: escudo anti-faltao. Cliente com N ou mais faltas (NAO_COMPARECEU) nos ultimos
-- 90 dias cai na fila de aprovacao mesmo com a fila global desligada. 0 = desligado.
ALTER TABLE tenant ADD COLUMN faltas_para_aprovacao INT NOT NULL DEFAULT 0;
