package com.agendamento.backend.controller;

import com.agendamento.backend.entity.Agendamento;
import com.agendamento.backend.entity.Plano;
import com.agendamento.backend.entity.Profissional;
import com.agendamento.backend.entity.Servico;
import com.agendamento.backend.entity.Tenant;
import com.agendamento.backend.repository.ProfissionalRepository;
import com.agendamento.backend.repository.AgendamentoRepository;
import com.agendamento.backend.repository.ServicoRepository;
import com.agendamento.backend.repository.TenantRepository;
import com.agendamento.backend.service.DisponibilidadeService;
import com.agendamento.backend.service.EvolutionApiService;
import com.agendamento.backend.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Página pública de agendamento (V31, Diamond): o cliente agenda pelo navegador
 * (link na bio do Instagram), sem WhatsApp e sem login. Tudo determinístico e
 * validado contra a mesma DisponibilidadeService do bot. Rate-limit por IP no POST.
 */
@RestController
@RequestMapping("/api/publico")
@RequiredArgsConstructor
@Slf4j
public class PublicoController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm");

    private final TenantRepository tenantRepository;
    private final ServicoRepository servicoRepository;
    private final ProfissionalRepository profissionalRepository;
    private final DisponibilidadeService disponibilidadeService;
    private final AgendamentoRepository agendamentoRepository;
    private final EvolutionApiService evolutionApiService;
    private final RateLimiterService rateLimiter;

    public record ServicoPub(UUID id, String nome, int duracaoMinutos, BigDecimal preco) {}
    public record ProfissionalPub(UUID id, String nome) {}
    public record InfoPublica(String nome, List<ServicoPub> servicos, List<ProfissionalPub> profissionais) {}
    public record AgendarPublicoRequest(
            @NotNull UUID servicoId,
            UUID profissionalId,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @NotBlank @Pattern(regexp = "\\d{2}:\\d{2}") String hora,
            @NotBlank String clienteNome,
            @NotBlank String clienteTelefone) {}

    @GetMapping("/{slug}")
    public InfoPublica info(@PathVariable String slug) {
        Tenant t = tenantPublico(slug);
        return new InfoPublica(t.getNome(),
                servicoRepository.findByTenantIdAndAtivoTrue(t.getId()).stream()
                        .map(s -> new ServicoPub(s.getId(), s.getNome(), s.getDuracaoMinutos(), s.getPreco())).toList(),
                profissionalRepository.findByTenantIdAndAtivoTrue(t.getId()).stream()
                        .map(p -> new ProfissionalPub(p.getId(), p.getNome())).toList());
    }

    @GetMapping("/{slug}/horarios")
    public List<String> horarios(@PathVariable String slug,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
                                 @RequestParam UUID servicoId,
                                 @RequestParam(required = false) UUID profissionalId) {
        Tenant t = tenantPublico(slug);
        validarData(data);
        Servico servico = servicoDoTenant(t, servicoId);
        return disponibilidadeService.horariosDisponiveis(t, data, profissionalId,
                Math.max(1, servico.getDuracaoMinutos()));
    }

    @PostMapping("/{slug}/agendar")
    public java.util.Map<String, String> agendar(@PathVariable String slug,
                                                 @Valid @RequestBody AgendarPublicoRequest req,
                                                 HttpServletRequest http) {
        if (!rateLimiter.permitir("publico:" + clientIp(http))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Muitas tentativas. Aguarde alguns minutos.");
        }
        Tenant t = tenantPublico(slug);
        validarData(req.data());
        Servico servico = servicoDoTenant(t, req.servicoId());

        Profissional prof = null;
        if (req.profissionalId() != null) {
            prof = profissionalRepository.findById(req.profissionalId())
                    .filter(p -> p.getTenantId().equals(t.getId()) && p.isAtivo())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Profissional inválido."));
        }

        String telefone = req.clienteTelefone().replaceAll("\\D", "");
        if (telefone.length() < 10 || telefone.length() > 15) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "WhatsApp inválido — use DDD + número (ex.: 11999998888).");
        }

        // Anti-flood: um telefone não pode acumular muitos horários futuros nesta agenda
        // (barra bot enchendo a agenda com reservas falsas pela página pública).
        long futurosDoTelefone = agendamentoRepository
                .findByTenantIdAndClienteTelefoneAndStatusInAndDataHoraAfterOrderByDataHora(
                        t.getId(), telefone, List.of("CONFIRMADO", "PENDENTE"), LocalDateTime.now()).size();
        if (futurosDoTelefone >= 3) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esse número já tem horários marcados aqui. Fale com o estabelecimento pra marcar mais.");
        }

        int dur = Math.max(1, servico.getDuracaoMinutos());
        // O horário TEM que estar na grade livre (cobre grade, folga, conflito e passado).
        if (!disponibilidadeService.horariosDisponiveis(t, req.data(),
                prof != null ? prof.getId() : null, dur).contains(req.hora())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esse horário acabou de ser ocupado. Escolha outro.");
        }

        boolean fila = t.isAprovacaoManual() && t.getPlano().permite(Plano.Recurso.FILA_APROVACAO);
        LocalDateTime dataHora = LocalDateTime.of(req.data(), LocalTime.parse(req.hora()));
        Agendamento ag = Agendamento.builder()
                .tenantId(t.getId())
                .clienteNome(req.clienteNome().trim())
                .clienteTelefone(telefone)
                .servico(servico.getNome())
                .profissional(prof != null ? prof.getNome() : null)
                .profissionalId(prof != null ? prof.getId() : null)
                .duracaoMinutos(dur)
                .dataHora(dataHora)
                .origem("SITE")
                .status(fila ? "PENDENTE" : "CONFIRMADO")
                .build();
        agendamentoRepository.save(ag);
        log.info("[Publico] Agendamento via página pública: tenant {} — {} em {}", t.getId(), telefone, dataHora);

        notificar(t, telefone, fila
                ? "📝 Recebi seu pedido de *" + servico.getNome() + "* pra *" + dataHora.format(FMT)
                    + "* (pela página da " + t.getNome() + ")! Vou confirmar e te aviso por aqui 👍"
                : "✅ Agendado! *" + servico.getNome() + "* em *" + dataHora.format(FMT)
                    + "* na " + t.getNome() + ". Eu te lembro no dia 😉");
        if (t.getTelefoneWhatsapp() != null && !t.getTelefoneWhatsapp().isBlank()) {
            notificar(t, t.getTelefoneWhatsapp(), "🌐 *Agendamento pela página pública!*\n\n🙋 "
                    + req.clienteNome().trim() + "\n✂️ " + servico.getNome()
                    + (prof != null ? "\n👤 " + prof.getNome() : "")
                    + "\n📅 " + dataHora.format(FMT)
                    + (fila ? "\n\nEstá na aba *Solicitações* aguardando você." : ""));
        }

        return java.util.Map.of("status", ag.getStatus());
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /** Só serve a página se o tenant existe, está ativo, ligou a página e o plano cobre. */
    private Tenant tenantPublico(String slug) {
        return tenantRepository.findBySlug(slug)
                .filter(t -> t.isAtivo() && t.isPaginaPublica() && !t.isAssinaturaVencida()
                        && t.getPlano().permite(Plano.Recurso.PAGINA_PUBLICA))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Página não encontrada."));
    }

    private Servico servicoDoTenant(Tenant t, UUID servicoId) {
        return servicoRepository.findById(servicoId)
                .filter(s -> s.getTenantId().equals(t.getId()) && s.isAtivo())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Serviço inválido."));
    }

    private void validarData(LocalDate data) {
        if (data.isBefore(LocalDate.now()) || data.isAfter(LocalDate.now().plusDays(30))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha uma data entre hoje e 30 dias.");
        }
    }

    /** Best-effort: aviso por WhatsApp nunca derruba o agendamento. */
    private void notificar(Tenant t, String telefone, String texto) {
        try {
            evolutionApiService.enviarMensagemNaInstancia(t.getId().toString(), telefone, texto);
        } catch (Exception e) {
            log.warn("[Publico] Falha ao notificar {}: {}", telefone, e.getMessage());
        }
    }

    /** Última posição do X-Forwarded-For (as primeiras são forjáveis); sem header, IP direto. */
    private String clientIp(HttpServletRequest http) {
        String xff = http.getHeader("X-Forwarded-For");
        if (xff == null || xff.isBlank()) return http.getRemoteAddr();
        String[] partes = xff.split(",");
        return partes[partes.length - 1].trim();
    }
}
