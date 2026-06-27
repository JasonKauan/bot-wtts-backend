-- Fase 1 do painel admin: papel SUPERADMIN (vendedor / back-office).
-- O SUPERADMIN nao pertence a nenhum tenant, entao tenant_id deixa de ser obrigatorio.
ALTER TABLE usuario ALTER COLUMN tenant_id DROP NOT NULL;

-- Busca de admins por papel (login /admin, seed idempotente).
CREATE INDEX IF NOT EXISTS idx_usuario_role ON usuario (role);
