-- V26: cliente fixo (recorrente). O dono cadastra "toda quinta as 19h" e um job diario
-- gera o agendamento com ~7 dias de antecedencia. Opt-in por cliente, com ativar/pausar.

CREATE TABLE recorrencia (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID          NOT NULL,
    cliente_nome     VARCHAR(255)  NOT NULL,
    cliente_telefone VARCHAR(30),
    servico          VARCHAR(255)  NOT NULL,
    profissional_id  UUID,
    profissional     VARCHAR(255),
    frequencia_dias  INT           NOT NULL,     -- 7 semanal, 14 quinzenal, 28 mensal
    hora             VARCHAR(5)    NOT NULL,     -- "19:00"
    proxima_data     DATE          NOT NULL,     -- proxima ocorrencia a gerar
    ativo            BOOLEAN       NOT NULL DEFAULT TRUE,
    criado_em        TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recorrencia_tenant  ON recorrencia (tenant_id);
CREATE INDEX idx_recorrencia_proxima ON recorrencia (ativo, proxima_data);
