package com.agendamento.backend.controller;

import com.agendamento.backend.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Backup completo com 1 clique (só SUPERADMIN — a rota /api/admin/ceo/** já é restrita).
 * Exporta TODAS as tabelas em um JSON pra download. É o seguro contra perder o banco
 * (o Postgres free do Render expira) — guarda os dados; a restauração é assistida.
 */
@RestController
@RequestMapping("/api/admin/ceo/backup")
@RequiredArgsConstructor
public class BackupController {

    private final TenantRepository tenantRepository;
    private final UsuarioRepository usuarioRepository;
    private final ServicoRepository servicoRepository;
    private final ProfissionalRepository profissionalRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final BloqueioRepository bloqueioRepository;
    private final BotSessionRepository botSessionRepository;
    private final PagamentoRepository pagamentoRepository;
    private final VendaRepository vendaRepository;
    private final AcertoComissaoRepository acertoComissaoRepository;
    private final AdminAuditoriaRepository adminAuditoriaRepository;
    private final ListaEsperaRepository listaEsperaRepository;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<byte[]> backup() throws Exception {
        Map<String, Object> dump = new LinkedHashMap<>();
        dump.put("geradoEm", LocalDateTime.now().toString());
        dump.put("formato", "agendabot-backup-v1");
        dump.put("tenant", tenantRepository.findAll());
        dump.put("usuario", usuarioRepository.findAll());
        dump.put("servico", servicoRepository.findAll());
        dump.put("profissional", profissionalRepository.findAll());
        dump.put("agendamento", agendamentoRepository.findAll());
        dump.put("bloqueio", bloqueioRepository.findAll());
        dump.put("bot_session", botSessionRepository.findAll());
        dump.put("pagamento", pagamentoRepository.findAll());
        dump.put("venda", vendaRepository.findAll());
        dump.put("acerto_comissao", acertoComissaoRepository.findAll());
        dump.put("admin_auditoria", adminAuditoriaRepository.findAll());
        dump.put("lista_espera", listaEsperaRepository.findAll());

        byte[] corpo = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(dump).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=backup-agendabot-" + LocalDate.now() + ".json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo);
    }
}
