-- V35: 1 trial por numero de WhatsApp, pra sempre (anti-farm de contas gratis).
-- Todo numero que consome trial fica registrado aqui (mesmo que a conta suma).
-- Cadastro com numero ja usado -> conta nasce VENCIDA (cai direto na tela de assinatura).

CREATE TABLE trial_registro (
    id        UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    telefone  VARCHAR(30) NOT NULL UNIQUE,
    tenant_id UUID,
    criado_em TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- Seed: os numeros dos tenants atuais ja contam como trial consumido
INSERT INTO trial_registro (telefone, tenant_id)
SELECT DISTINCT ON (regexp_replace(telefone_whatsapp, '[^0-9]', '', 'g'))
       regexp_replace(telefone_whatsapp, '[^0-9]', '', 'g'), id
FROM tenant
WHERE telefone_whatsapp IS NOT NULL
  AND regexp_replace(telefone_whatsapp, '[^0-9]', '', 'g') <> '';
