-- Antecedência mínima (em horas) pra cliente cancelar/remarcar pelo bot. 0 = sem regra.
ALTER TABLE tenant ADD COLUMN antecedencia_min_horas INT NOT NULL DEFAULT 0;
