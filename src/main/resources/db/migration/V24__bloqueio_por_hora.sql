-- V24: compromisso avulso — bloqueio de um PEDACO do dia ("dentista das 14h as 15h").
-- hora_inicio/hora_fim nulos = dia inteiro (comportamento antigo). Formato "HH:mm".
ALTER TABLE bloqueio ADD COLUMN hora_inicio VARCHAR(5);
ALTER TABLE bloqueio ADD COLUMN hora_fim VARCHAR(5);
