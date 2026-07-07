-- V21: folga por profissional. Nulo = folga do estabelecimento inteiro (comportamento antigo).
ALTER TABLE bloqueio ADD COLUMN profissional_id UUID;
