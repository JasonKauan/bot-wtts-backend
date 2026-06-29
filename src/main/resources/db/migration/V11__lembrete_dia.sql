-- Lembrete extra no dia do atendimento (além do de 24h). Flag separada pra não duplicar.
ALTER TABLE agendamento ADD COLUMN lembrete_dia_enviado BOOLEAN NOT NULL DEFAULT false;
