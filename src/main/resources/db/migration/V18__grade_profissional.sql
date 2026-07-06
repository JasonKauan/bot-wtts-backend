-- V18: grade horaria individual por profissional.
-- Todos os campos sao NULOS por padrao = o profissional herda a grade do estabelecimento (tenant).
-- Cada campo preenchido sobrepoe o valor do tenant individualmente.

ALTER TABLE profissional ADD COLUMN horario_abertura INT;
ALTER TABLE profissional ADD COLUMN horario_fechamento INT;
ALTER TABLE profissional ADD COLUMN almoco_inicio INT;
ALTER TABLE profissional ADD COLUMN almoco_fim INT;
ALTER TABLE profissional ADD COLUMN dias_trabalho VARCHAR(20);
