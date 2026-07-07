-- V22: lista de espera. Dia lotado -> cliente responde "avisa" e entra na fila;
-- quando um agendamento daquele dia e cancelado/remarcado, o bot chama o primeiro da fila.

CREATE TABLE lista_espera (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID          NOT NULL,
    telefone        VARCHAR(30)   NOT NULL,
    cliente_nome    VARCHAR(255),
    servico         VARCHAR(255),
    profissional_id UUID,
    profissional    VARCHAR(255),
    data            DATE          NOT NULL,
    criado_em       TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_espera_tenant_data ON lista_espera (tenant_id, data, criado_em);

-- Ultima data que o bot informou estar lotada nesta conversa
-- (e o dia a que um "avisa" do cliente se refere).
ALTER TABLE bot_session ADD COLUMN espera_data DATE;
