-- Preço do serviço (exibido pro cliente no bot e no painel) e duração gravada no agendamento
-- (serviço de 60min passa a ocupar os slots certos da grade, não só um).
ALTER TABLE servico     ADD COLUMN preco           NUMERIC(10,2);
ALTER TABLE agendamento ADD COLUMN duracao_minutos INT;
