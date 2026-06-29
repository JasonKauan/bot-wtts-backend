-- Folgas/feriados: o estabelecimento bloqueia datas (avulsas ou um período) e o bot deixa de oferecê-las.
CREATE TABLE bloqueio (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL REFERENCES tenant(id),
    data_inicio DATE         NOT NULL,
    data_fim    DATE         NOT NULL,
    descricao   VARCHAR(200),
    criado_em   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bloqueio_tenant ON bloqueio (tenant_id, data_inicio);
