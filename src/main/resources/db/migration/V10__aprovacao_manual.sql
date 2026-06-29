-- Fila de aprovação: quando ligado, agendamentos do bot entram como PENDENTE e o dono
-- aceita/recusa no painel. Default false = confirma automático (comportamento atual).
ALTER TABLE tenant ADD COLUMN aprovacao_manual BOOLEAN NOT NULL DEFAULT false;
