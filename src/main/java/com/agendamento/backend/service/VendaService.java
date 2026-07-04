package com.agendamento.backend.service;

import com.agendamento.backend.entity.Plano;
import com.agendamento.backend.entity.Tenant;
import com.agendamento.backend.entity.Usuario;
import com.agendamento.backend.entity.Venda;
import com.agendamento.backend.repository.UsuarioRepository;
import com.agendamento.backend.repository.VendaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Registra vendas (ativação/renovação de plano pago) com comissão calculada.
 * Atribuição: sempre o vendedor da CARTEIRA do cliente (tenant.vendedorId) —
 * vale tanto pra ativação manual quanto pro PIX pago pelo próprio cliente.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VendaService {

    private final VendaRepository vendaRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional
    public void registrar(Tenant tenant, Plano plano, String origem) {
        if (plano == null || plano == Plano.TRIAL) return;   // trial não é venda

        BigDecimal valor = plano.getValorMensal();
        BigDecimal pct = BigDecimal.ZERO;
        String vendedorEmail = null;

        if (tenant.getVendedorId() != null) {
            Usuario vendedor = usuarioRepository.findById(tenant.getVendedorId()).orElse(null);
            if (vendedor != null) {
                vendedorEmail = vendedor.getEmail();
                if (vendedor.getComissaoPct() != null) pct = vendedor.getComissaoPct();
            }
        }

        BigDecimal comissao = valor.multiply(pct)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        vendaRepository.save(Venda.builder()
                .tenantId(tenant.getId())
                .tenantNome(tenant.getNome())
                .vendedorId(tenant.getVendedorId())
                .vendedorEmail(vendedorEmail)
                .plano(plano)
                .valor(valor)
                .comissaoPct(pct)
                .comissaoValor(comissao)
                .origem(origem)
                .build());

        log.info("[venda] tenant {} plano {} R${} origem {} — vendedor {} comissão R${}",
                tenant.getId(), plano, valor, origem, vendedorEmail, comissao);
    }
}
