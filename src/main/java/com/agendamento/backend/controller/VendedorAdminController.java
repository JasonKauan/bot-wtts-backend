package com.agendamento.backend.controller;

import com.agendamento.backend.dto.admin.CriarVendedorRequest;
import com.agendamento.backend.dto.admin.EditarVendedorRequest;
import com.agendamento.backend.dto.admin.VendedorDto;
import com.agendamento.backend.entity.Usuario;
import com.agendamento.backend.repository.UsuarioRepository;
import com.agendamento.backend.service.AdminAuthService;
import com.agendamento.backend.service.AuditoriaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/** Gestão de vendedores — só o CEO (SUPERADMIN) acessa (regra no SecurityConfig). */
@RestController
@RequestMapping("/api/admin/vendedores")
@RequiredArgsConstructor
public class VendedorAdminController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    @GetMapping
    public List<VendedorDto> listar() {
        return usuarioRepository.findByRoleOrderByCriadoEmDesc(AdminAuthService.ROLE_VENDEDOR)
                .stream().map(this::toDto).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VendedorDto criar(@Valid @RequestBody CriarVendedorRequest req) {
        if (usuarioRepository.existsByEmail(req.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail já cadastrado.");
        }
        Usuario v = Usuario.builder()
                .tenantId(null)                       // vendedor não pertence a tenant
                .nome(req.getNome().trim())
                .email(req.getEmail())
                .senha(passwordEncoder.encode(req.getSenha()))
                .role(AdminAuthService.ROLE_VENDEDOR)
                .comissaoPct(req.getComissaoPct())
                .ativo(true)
                .build();
        usuarioRepository.save(v);
        auditoriaService.registrar("CRIAR_VENDEDOR", null, v.getEmail(), "comissão " + v.getComissaoPct() + "%");
        return toDto(v);
    }

    /** Edita só os campos enviados: nome, % de comissão, ativo e/ou senha. */
    @PatchMapping("/{id}")
    public VendedorDto editar(@PathVariable UUID id, @Valid @RequestBody EditarVendedorRequest req) {
        Usuario v = usuarioRepository.findById(id)
                .filter(u -> AdminAuthService.ROLE_VENDEDOR.equals(u.getRole()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendedor não encontrado."));

        StringBuilder detalhe = new StringBuilder();
        if (req.getNome() != null && !req.getNome().isBlank()) v.setNome(req.getNome().trim());
        if (req.getComissaoPct() != null) {
            v.setComissaoPct(req.getComissaoPct());
            detalhe.append("comissão ").append(req.getComissaoPct()).append("% ");
        }
        if (req.getAtivo() != null) {
            v.setAtivo(req.getAtivo());
            detalhe.append(req.getAtivo() ? "ativado " : "desativado ");
        }
        if (req.getSenha() != null && !req.getSenha().isBlank()) {
            v.setSenha(passwordEncoder.encode(req.getSenha()));
            detalhe.append("senha trocada ");
        }
        usuarioRepository.save(v);
        auditoriaService.registrar("EDITAR_VENDEDOR", null, v.getEmail(), detalhe.toString().trim());
        return toDto(v);
    }

    private VendedorDto toDto(Usuario u) {
        return new VendedorDto(u.getId(), u.getNome(), u.getEmail(), u.getComissaoPct(),
                u.isAtivo(), u.getCriadoEm());
    }
}
