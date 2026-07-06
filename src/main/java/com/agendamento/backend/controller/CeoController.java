package com.agendamento.backend.controller;

import com.agendamento.backend.dto.admin.AcertarRequest;
import com.agendamento.backend.dto.admin.AcertoDto;
import com.agendamento.backend.dto.admin.AcertoHistoricoDto;
import com.agendamento.backend.dto.admin.CeoResumoDto;
import com.agendamento.backend.dto.admin.RankingVendedorDto;
import com.agendamento.backend.dto.admin.VendaLinhaDto;
import com.agendamento.backend.entity.AcertoComissao;
import com.agendamento.backend.entity.Usuario;
import com.agendamento.backend.entity.Venda;
import com.agendamento.backend.repository.AcertoComissaoRepository;
import com.agendamento.backend.repository.UsuarioRepository;
import com.agendamento.backend.repository.VendaRepository;
import com.agendamento.backend.service.AdminAuthService;
import com.agendamento.backend.service.AuditoriaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final AcertoComissaoRepository acertoComissaoRepository;
    private final AuditoriaService auditoriaService;

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

    /** Comissões pendentes de acerto, agrupadas por vendedor. */
    @GetMapping("/acerto")
    public List<AcertoDto> acerto() {
        Map<UUID, String> nomes = nomesVendedores();
        return vendaRepository.findByPagoFalseAndVendedorIdIsNotNullOrderByCriadoEmDesc().stream()
                .collect(Collectors.groupingBy(Venda::getVendedorId))
                .entrySet().stream()
                .map(e -> new AcertoDto(
                        e.getKey(),
                        nomes.getOrDefault(e.getKey(),
                                e.getValue().get(0).getVendedorEmail() != null
                                        ? e.getValue().get(0).getVendedorEmail() : "Vendedor removido"),
                        e.getValue().size(),
                        soma(e.getValue(), Venda::comissaoDevida)))
                .sorted(Comparator.comparing(AcertoDto::comissaoPendente).reversed())
                .toList();
    }

    /**
     * Acerta as comissões pendentes do vendedor. Sem {@code valor} no body = quita TUDO.
     * Com {@code valor} = acerto PARCIAL: paga da venda mais antiga pra mais nova; a venda
     * que só foi coberta em parte acumula em {@code comissaoPagaParcial}. Gera histórico.
     */
    @PostMapping("/acerto/{vendedorId}")
    @Transactional
    public Map<String, Object> acertar(@PathVariable UUID vendedorId,
                                       @RequestBody(required = false) AcertarRequest req) {
        List<Venda> pendentes = vendaRepository.findByVendedorIdAndPagoFalseOrderByCriadoEmAsc(vendedorId);
        BigDecimal pendenteTotal = soma(pendentes, Venda::comissaoDevida);

        BigDecimal aPagar = (req != null && req.valor() != null) ? req.valor() : pendenteTotal;
        if (aPagar.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor do acerto deve ser maior que zero.");
        }
        if (aPagar.compareTo(pendenteTotal) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Valor maior que o pendente (R$ " + pendenteTotal + ").");
        }

        LocalDateTime agora = LocalDateTime.now();
        BigDecimal restante = aPagar;
        int quitadas = 0;
        for (Venda v : pendentes) {
            if (restante.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal devida = v.comissaoDevida();
            if (restante.compareTo(devida) >= 0) {
                v.setPago(true);
                v.setPagoEm(agora);
                v.setComissaoPagaParcial(BigDecimal.ZERO);
                restante = restante.subtract(devida);
                quitadas++;
            } else {
                v.setComissaoPagaParcial(v.getComissaoPagaParcial().add(restante));
                restante = BigDecimal.ZERO;
            }
        }
        vendaRepository.saveAll(pendentes);

        BigDecimal pendenteApos = pendenteTotal.subtract(aPagar);
        String vendedor = pendentes.isEmpty() ? vendedorId.toString()
                : nomesVendedores().getOrDefault(vendedorId,
                        pendentes.get(0).getVendedorEmail() != null ? pendentes.get(0).getVendedorEmail() : vendedorId.toString());

        acertoComissaoRepository.save(AcertoComissao.builder()
                .vendedorId(vendedorId)
                .vendedorNome(vendedor)
                .valor(aPagar)
                .vendasQuitadas(quitadas)
                .pendenteApos(pendenteApos)
                .build());
        auditoriaService.registrar("ACERTAR_COMISSAO", null, vendedor,
                "R$ " + aPagar + " (" + quitadas + " venda(s) quitada(s), pendente R$ " + pendenteApos + ")");
        return Map.of("vendasAcertadas", quitadas, "total", aPagar, "pendenteRestante", pendenteApos);
    }

    /** Histórico dos últimos acertos de comissão (mais recentes primeiro). */
    @GetMapping("/acertos")
    public List<AcertoHistoricoDto> historicoAcertos() {
        return acertoComissaoRepository.findTop50ByOrderByCriadoEmDesc().stream()
                .map(a -> new AcertoHistoricoDto(a.getVendedorNome(), a.getValor(),
                        a.getVendasQuitadas(), a.getPendenteApos(), a.getCriadoEm()))
                .toList();
    }

    /** Exporta todas as vendas em CSV (separador ; — abre direto no Excel BR). */
    @GetMapping("/vendas.csv")
    public ResponseEntity<byte[]> vendasCsv() {
        Map<UUID, String> nomes = nomesVendedores();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        StringBuilder sb = new StringBuilder("Data;Cliente;Vendedor;Plano;Valor;Comissao %;Comissao R$;Origem;Comissao paga\n");
        for (Venda v : vendaRepository.findAllByOrderByCriadoEmDesc()) {
            sb.append(v.getCriadoEm().format(fmt)).append(';')
              .append(csv(v.getTenantNome())).append(';')
              .append(csv(rotuloVendedor(v, nomes))).append(';')
              .append(v.getPlano().name()).append(';')
              .append(dec(v.getValor())).append(';')
              .append(dec(v.getComissaoPct())).append(';')
              .append(dec(v.getComissaoValor())).append(';')
              .append(v.getOrigem()).append(';')
              .append(v.isPago() ? "SIM" : "NAO").append('\n');
        }
        // BOM pro Excel reconhecer acentos (UTF-8)
        byte[] corpo = ("﻿" + sb).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=vendas.csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(corpo);
    }

    private String csv(String s) {
        if (s == null) return "";
        return s.replace(';', ',').replace('\n', ' ');
    }

    /** Decimal com vírgula (padrão BR do Excel). */
    private String dec(BigDecimal n) {
        return n == null ? "0" : n.toPlainString().replace('.', ',');
    }

    private Map<UUID, String> nomesVendedores() {
        return usuarioRepository.findByRoleOrderByCriadoEmDesc(AdminAuthService.ROLE_VENDEDOR).stream()
                .collect(Collectors.toMap(Usuario::getId,
                        u -> u.getNome() != null && !u.getNome().isBlank() ? u.getNome() : u.getEmail()));
    }

    private String rotuloVendedor(Venda v, Map<UUID, String> nomes) {
        if (v.getVendedorId() == null) return "Casa";
        return nomes.getOrDefault(v.getVendedorId(),
                v.getVendedorEmail() != null ? v.getVendedorEmail() : "Vendedor removido");
    }

    private VendaLinhaDto toLinha(Venda v, Map<UUID, String> nomes) {
        return new VendaLinhaDto(v.getTenantNome(), rotuloVendedor(v, nomes),
                v.getPlano().name(), v.getValor(), v.getComissaoValor(), v.getOrigem(), v.isPago(), v.getCriadoEm());
    }

    private BigDecimal soma(List<Venda> vendas, Function<Venda, BigDecimal> campo) {
        return vendas.stream().map(campo).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
