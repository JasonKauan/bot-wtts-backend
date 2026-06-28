-- Iteração 7: grade horária configurável por estabelecimento (tira o HORARIOS hardcoded do bot).
-- intervalo entre slots, janela de almoço (opcional) e dias da semana de funcionamento.
ALTER TABLE tenant ADD COLUMN intervalo_minutos  INT         NOT NULL DEFAULT 60;
ALTER TABLE tenant ADD COLUMN almoco_inicio       INT;          -- hora (0-24); nulo = sem almoço
ALTER TABLE tenant ADD COLUMN almoco_fim          INT;
-- dias ISO (1=segunda ... 7=domingo), separados por vírgula. Default: todos (preserva o comportamento atual).
ALTER TABLE tenant ADD COLUMN dias_funcionamento  VARCHAR(20) NOT NULL DEFAULT '1,2,3,4,5,6,7';
