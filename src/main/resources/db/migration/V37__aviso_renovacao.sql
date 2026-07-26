-- V37: regua de cobranca. O sistema avisa o dono ANTES de vencer (5, 3 e 1 dia antes,
-- e no dia), em vez de so pausar o bot depois. Vale pro trial tambem — la e conversao.
-- Guarda o MENOR marco ja avisado do ciclo atual (99 = nenhum aviso ainda);
-- ao renovar/trocar de plano, volta pra 99 e o ciclo recomeca.
ALTER TABLE tenant ADD COLUMN aviso_renovacao_marco INT NOT NULL DEFAULT 99;
