package com.agendamento.backend.controller;

import com.agendamento.backend.dto.api.ConfiguracaoRequest;
import com.agendamento.backend.dto.api.ConfiguracaoResponse;
import com.agendamento.backend.entity.Plano;
import com.agendamento.backend.entity.Tenant;
import com.agendamento.backend.repository.TenantRepository;
import com.agendamento.backend.security.TenantContext;
import com.agendamento.backend.service.PlanoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/configuracoes")
@RequiredArgsConstructor
public class ConfiguracaoController {

    private final TenantRepository tenantRepository;
    private final PlanoService planoService;

    @GetMapping
    public ConfiguracaoResponse get() {
        return toDto(buscarTenant());
    }

    @PutMapping
    public ConfiguracaoResponse atualizar(@Valid @RequestBody ConfiguracaoRequest req) {
        if (req.horarioFechamento() <= req.horarioAbertura()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fechamento deve ser depois da abertura.");
        }
        boolean temAlmoco = req.almocoInicio() != null && req.almocoFim() != null;
        if (temAlmoco && req.almocoFim() <= req.almocoInicio()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fim do almoço deve ser depois do início.");
        }

        Tenant t = buscarTenant();
        t.setNome(req.nome());
        t.setHorarioAbertura(req.horarioAbertura());
        t.setHorarioFechamento(req.horarioFechamento());
        t.setIntervaloMinutos(req.intervaloMinutos());
        // Almoço só vale com os dois campos; caso contrário, sem almoço.
        t.setAlmocoInicio(temAlmoco ? req.almocoInicio() : null);
        t.setAlmocoFim(temAlmoco ? req.almocoFim() : null);
        t.setDiasFuncionamento(
                (req.diasFuncionamento() == null || req.diasFuncionamento().isBlank())
                        ? "1,2,3,4,5,6,7" : req.diasFuncionamento());
        t.setAprovacaoManual(req.aprovacaoManual());
        t.setAntecedenciaMinHoras(req.antecedenciaMinHoras());
        t.setResumoDiario(req.resumoDiario());
        t.setFaltasParaAprovacao(req.faltasParaAprovacao());
        t.setPermiteCombo(req.permiteCombo());

        // ── Recursos Diamond (V31–V33): o gate só barra quando LIGA; desligar é sempre permitido ──
        if (req.paginaPublica()) planoService.exigir(t.getId(), Plano.Recurso.PAGINA_PUBLICA);
        if (req.reativacaoDias() > 0) planoService.exigir(t.getId(), Plano.Recurso.REATIVACAO);
        if (req.aniversarioAtivo()) planoService.exigir(t.getId(), Plano.Recurso.ANIVERSARIO);

        String slug = normalizarSlug(req.slug());
        if (req.paginaPublica() && (slug == null || slug.isBlank())) {
            slug = normalizarSlug(t.getNome());   // primeiro liga sem slug → gera do nome
        }
        if (slug != null && !slug.isBlank()) {
            String slugFinal = slug;
            boolean emUso = tenantRepository.findBySlug(slugFinal)
                    .filter(outro -> !outro.getId().equals(t.getId())).isPresent();
            if (emUso) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "O endereço \"" + slugFinal + "\" já está em uso — escolha outro.");
            }
            t.setSlug(slugFinal);
        }
        t.setPaginaPublica(req.paginaPublica());
        t.setReativacaoDias(req.reativacaoDias());
        t.setReativacaoMsg(limpar(req.reativacaoMsg()));
        t.setAniversarioAtivo(req.aniversarioAtivo());
        t.setAniversarioMsg(limpar(req.aniversarioMsg()));

        return toDto(tenantRepository.save(t));
    }

    /** "Barbearia do Zé!" → "barbearia-do-ze" (só a-z, 0-9 e hífen; máx 60). */
    private String normalizarSlug(String s) {
        if (s == null) return null;
        String norm = java.text.Normalizer.normalize(s.trim().toLowerCase(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return norm.length() > 60 ? norm.substring(0, 60) : norm;
    }

    private String limpar(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private ConfiguracaoResponse toDto(Tenant t) {
        return new ConfiguracaoResponse(t.getId(), t.getNome(), t.getTelefoneWhatsapp(),
                t.getHorarioAbertura(), t.getHorarioFechamento(),
                t.getIntervaloMinutos(), t.getAlmocoInicio(), t.getAlmocoFim(), t.getDiasFuncionamento(),
                t.isAprovacaoManual(), t.getAntecedenciaMinHoras(), t.isResumoDiario(),
                t.getFaltasParaAprovacao(), t.isPermiteCombo(),
                t.isPaginaPublica(), t.getSlug(), t.getReativacaoDias(), t.getReativacaoMsg(),
                t.isAniversarioAtivo(), t.getAniversarioMsg(), t.getPlano().getNivel());
    }

    private Tenant buscarTenant() {
        return tenantRepository.findById(TenantContext.get())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
