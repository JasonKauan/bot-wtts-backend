-- V31 (Diamond): pagina publica de agendamento — link pro cliente agendar pelo navegador
-- (bio do Instagram etc.), sem WhatsApp. Desligada por padrao; o dono ativa e ganha um slug.
ALTER TABLE tenant ADD COLUMN slug VARCHAR(60);
ALTER TABLE tenant ADD COLUMN pagina_publica BOOLEAN NOT NULL DEFAULT FALSE;
CREATE UNIQUE INDEX uq_tenant_slug ON tenant (slug) WHERE slug IS NOT NULL;
