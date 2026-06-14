-- Iteração 2: schema inicial com UUID e criado_em
-- Iteração 3: adicionar tenant_id (FK) nas duas tabelas

CREATE TABLE agendamento (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    cliente_nome     VARCHAR(255),
    cliente_telefone VARCHAR(50)  NOT NULL,
    servico          VARCHAR(255) NOT NULL,
    data_hora        TIMESTAMP    NOT NULL,
    status           VARCHAR(50)  NOT NULL,
    criado_em        TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE bot_session (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    telefone          VARCHAR(50)  NOT NULL UNIQUE,
    etapa             VARCHAR(50)  NOT NULL,
    servico_escolhido VARCHAR(255),
    data_escolhida    DATE,
    hora_escolhida    VARCHAR(10),
    -- Conta inputs inválidos por etapa; reseta ao avançar de etapa.
    -- Máximo 3 tentativas antes de encerrar a sessão (Iteração 2).
    tentativas        INTEGER      NOT NULL DEFAULT 0,
    ultima_interacao  TIMESTAMP    NOT NULL,
    criado_em         TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_agendamento_data_hora ON agendamento (data_hora);
CREATE INDEX idx_agendamento_telefone  ON agendamento (cliente_telefone);
CREATE INDEX idx_bot_session_telefone  ON bot_session (telefone);
