package com.agendamento.backend.repository;

import com.agendamento.backend.entity.TrialRegistro;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TrialRegistroRepository extends JpaRepository<TrialRegistro, UUID> {

    /** Telefone normalizado (só dígitos). */
    Optional<TrialRegistro> findByTelefone(String telefone);
}
