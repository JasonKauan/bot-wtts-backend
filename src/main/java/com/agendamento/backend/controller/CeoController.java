package com.agendamento.backend.controller;

import com.agendamento.backend.dto.admin.CeoResumoDto;
import com.agendamento.backend.dto.admin.RankingVendedorDto;
import com.agendamento.backend.dto.admin.VendaLinhaDto;
import com.agendamento.backend.entity.Usuario;
import com.agendamento.backend.entity.Venda;
import com.agendamento.backend.repository.UsuarioRepository;
import com.agendamento.backend.repository.VendaRepository;
import com.agendamento.backend.service.AdminAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Painel do CEO: receita, vendas, ranking de vendedores e comissões. Só SUPERADMIN. */
@RestController
@RequestMapping("/api/admin/ceo")
@RequiredArgsConstructor
public class CeoController {

    private final VendaRepository vendaRepository;
    private final UsuarioRepository usuarioRepository;

    @GetMapping("/resumo")
    public CeoResumoDto resumo() {
        LocalDateTime inicioMes = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime inicioMesAnterior = inicioMes.minusMonths(1);

        List<Venda> desdeMesPassado = vendaRepository
                .findByCriadoEmGreaterThanEqualOrderByCriadoEmDesc(inicioMesAnterior);

        List<Venda> doMes = desdeMesPassado.stream()
                .filter(v -> !v.getCriadoEm().isBefore(inicioMes)).toList();
        List<Venda> doMesAnterior = desdeMesPassado.stream()
                .filter(v -> v.getCriadoEm().isBefore(inicioMes)).toList();

        BigDecimal receitaMes = soma(doMes, Venda::getValor);
        BigDecimal comissoesMes = soma(doMes, Venda::getComissaoValor);
        BigDecimal receitaMesAnterior = soma(doMesAnterior, Venda::getValor);

        // Nome bonito no ranking (email é o fallback)
        Map<UUID, String> nomes = usuarioRepository
                .findByRoleOrderByCriadoEmDesc(AdminAuthService.ROLE_VENDEDOR).stream()
                .collect(Collectors.toMap(Usuario::getId,
                        u -> u.getNome() != null && !u.getNome().isBlank() ? u.getNome() : u.getEmail()));

        List<RankingVendedorDto> ranking = doMes.stream()
                .collect(Collectors.groupingBy(v -> rotuloVendedor(v, nomes)))
                .entrySet().stream()
                .map(e -> new RankingVendedorDto(
                        e.getKey(),
                        e.getValue().size(),
                        soma(e.getValue(), Venda::getValor),
                        soma(e.getValue(), Venda::getComissaoValor)))
                .sorted(Comparator.comparing(RankingVendedorDto::receita).reversed())
                .toList();

        List<VendaLinhaDto> recentes = desdeMesPassado.stream()
                .limit(20)
                .map(v -> toLinha(v, nomes))
                .toList();

        return new CeoResumoDto(receitaMes, doMes.size(), comissoesMes,
                receitaMesAnterior, doMesAnterior.size(), ranking, recentes);
    }

    private String rotuloVendedor(Venda v, Map<UUID, String> nomes) {
        if (v.getVendedorId() == null) return "Casa";
        return nomes.getOrDefault(v.getVendedorId(),
                v.getVendedorEmail() != null ? v.getVendedorEmail() : "Vendedor removido");
    }

    private VendaLinhaDto toLinha(Venda v, Map<UUID, String> nomes) {
        return new VendaLinhaDto(v.getTenantNome(), rotuloVendedor(v, nomes),
                v.getPlano().name(), v.getValor(), v.getComissaoValor(), v.getOrigem(), v.getCriadoEm());
    }

    private BigDecimal soma(List<Venda> vendas, Function<Venda, BigDecimal> campo) {
        return vendas.stream().map(campo).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
