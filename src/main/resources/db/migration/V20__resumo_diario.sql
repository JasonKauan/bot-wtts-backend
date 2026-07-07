-- V20: resumo diario da agenda enviado ao dono no WhatsApp (toda manha).
-- resumo_enviado_em deduplica o envio: o job roda varias vezes de manha
-- (o Render free dorme e pode acordar tarde) mas so envia 1x por dia.

ALTER TABLE tenant ADD COLUMN resumo_diario BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE tenant ADD COLUMN resumo_enviado_em DATE;
