-- V34 (Diamond): multi-unidade — um dono acessa 2+ estabelecimentos com o mesmo login.
-- O tenant "casa" do usuario continua em usuario.tenant_id; unidades extras viram vinculos.
CREATE TABLE unidade_vinculo (
    id         UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID      NOT NULL,
    tenant_id  UUID      NOT NULL,
    criado_em  TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_unidade_vinculo UNIQUE (usuario_id, tenant_id)
);
CREATE INDEX idx_unidade_usuario ON unidade_vinculo (usuario_id);
