package com.agendamento.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Camada de IA (OpenRouter, API compatível com OpenAI) usada como fallback de
 * linguagem natural no bot. Determinístico vem primeiro; isto só entra quando o
 * parse falha. Qualquer erro/timeout devolve "sem resposta" e o bot degrada para
 * o fluxo de menu — nunca quebra.
 */
@Service
@Slf4j
public class AiService {

    private final RestTemplate http;

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    @Value("${ai.api.url:https://generativelanguage.googleapis.com/v1beta/openai/chat/completions}")
    private String url;

    @Value("${ai.api.key:}")
    private String apiKey;

    @Value("${ai.api.model:gemini-2.0-flash}")
    private String model;

    public AiService(RestTemplateBuilder builder,
                     @Value("${ai.api.timeout-ms:8000}") long timeoutMs) {
        // timeout curto: o bot não pode travar esperando a IA num webhook síncrono.
        this.http = builder
                .setConnectTimeout(Duration.ofMillis(timeoutMs))
                .setReadTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    public boolean ativo() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Chamada de extração (curta, determinística, temp baixa). Devolve o texto, ou null. */
    public String chat(String system, String user) {
        return call(system, user, 24, 0.1);
    }

    /**
     * Gera UMA frase curta e natural para a situação dada (tom de recepcionista no WhatsApp).
     * Não inventa fatos, não faz listas. Devolve null em falha → quem chama usa o texto fixo.
     */
    public String redigir(String situacao) {
        return call(
                "Voce e a recepcionista de um estabelecimento brasileiro atendendo no WhatsApp. " +
                "Fale como gente de verdade: calorosa, informal e direta, com jeitinho brasileiro. " +
                "UMA frase curta (ate ~18 palavras), no maximo 1 emoji. VARIE as palavras a cada vez e " +
                "EVITE cliches como 'Como posso ajudar', 'Em que posso ser util', 'Fico a disposicao'. " +
                "Nao invente informacoes, nao faca listas. Use *texto* para negrito. Responda apenas a frase.",
                situacao, 90, 0.8);
    }

    private String call(String system, String user, int maxTokens, double temperature) {
        if (!ativo()) return null;
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "temperature", temperature,
                    "max_tokens", maxTokens,
                    "messages", List.of(
                            Map.of("role", "system", "content", system),
                            Map.of("role", "user", "content", user)
                    )
            );
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            headers.set("X-Title", "AgendaBot");

            Map<?, ?> resp = http.postForObject(url, new HttpEntity<>(body, headers), Map.class);
            if (resp != null && resp.get("choices") instanceof List<?> choices && !choices.isEmpty()
                    && choices.get(0) instanceof Map<?, ?> c && c.get("message") instanceof Map<?, ?> m
                    && m.get("content") != null) {
                return String.valueOf(m.get("content")).trim();
            }
            return null;
        } catch (Exception e) {
            log.warn("IA indisponível: {}", e.getMessage());
            return null;
        }
    }

    /** Índice 1-based da opção que o cliente quis, ou 0 se nenhuma / IA off. */
    public int escolherOpcao(String mensagemCliente, List<String> opcoes) {
        if (opcoes.isEmpty() || !ativo()) return 0;
        StringBuilder lista = new StringBuilder();
        for (int i = 0; i < opcoes.size(); i++) {
            lista.append(i + 1).append(". ").append(opcoes.get(i)).append("\n");
        }
        String resp = chat(
                "Voce e um atendente de agendamento. A partir da mensagem do cliente, identifique qual opcao da lista ele quer. " +
                "Responda SOMENTE com o numero da opcao (1, 2, 3...). Se nenhuma corresponder, responda 0.",
                "Opcoes:\n" + lista + "\nMensagem do cliente: " + mensagemCliente);
        Integer n = primeiroNumero(resp);
        return (n != null && n >= 1 && n <= opcoes.size()) ? n : 0;
    }

    /** Interpreta uma data em linguagem natural, ou null. */
    public LocalDate interpretarData(String mensagemCliente, LocalDate hoje) {
        if (!ativo()) return null;
        String resp = chat(
                "Voce extrai a data que o cliente quer agendar. Hoje e " + hoje + " (" + diaDaSemanaPt(hoje)
                + ", formato AAAA-MM-DD). Entenda girias: hj=hoje, amnh/amanha=amanha, 'dps de amanha'=hoje+2, " +
                "seg/ter/qua/qui/sex/sab/dom = dias da semana (proxima ocorrencia). " +
                "Responda SOMENTE com a data no formato AAAA-MM-DD. Se nao houver data clara, responda NENHUMA.",
                "Mensagem do cliente: " + mensagemCliente);
        if (resp == null) return null;
        Matcher m = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})").matcher(resp);
        if (m.find()) {
            try { return LocalDate.parse(m.group()); } catch (Exception e) { return null; }
        }
        return null;
    }

    /** Extrai o horário que o cliente pediu, no formato HH:mm (mesmo que indisponível). Null se não houver. */
    public String interpretarHorario(String msg) {
        if (!ativo()) return null;
        String resp = chat(
                "Extraia o horario que o cliente quer agendar, no formato HH:MM (24h). " +
                "Ex: 'as 3 da tarde' -> 15:00; 'umas 9 da manha' -> 09:00; '2hr'/'2 hrs' -> 14:00 se contexto tarde; " +
                "'meio dia' -> 12:00; 'meia noite' -> 00:00. Se nao houver horario claro, responda NENHUM.",
                "Mensagem: " + msg);
        if (resp == null) return null;
        Matcher m = Pattern.compile("([01]?\\d|2[0-3]):([0-5]\\d)").matcher(resp);
        if (m.find()) {
            String[] p = m.group().split(":");
            return String.format("%02d:%02d", Integer.parseInt(p[0]), Integer.parseInt(p[1]));
        }
        return null;
    }

    /** Slot-filling: lê a mensagem inteira e extrai serviço, profissional, data e hora de uma vez. Null se IA off/falha. */
    public Extracao extrair(String mensagem, List<String> servicos, List<String> profissionais, LocalDate hoje) {
        if (!ativo()) return null;
        String sys = "Voce extrai dados de um pedido de agendamento por WhatsApp brasileiro. " +
                "Servicos disponiveis: " + servicos + ". " +
                "Profissionais: " + (profissionais.isEmpty() ? "nenhum" : profissionais) + ". " +
                "Hoje e " + hoje + " (" + diaDaSemanaPt(hoje) + ", formato AAAA-MM-DD). " +
                "O cliente usa girias e abreviacoes do zap: hj/hje=hoje, amnh/amanha/amn=amanha, " +
                "'dps de amanha'/'depois de amanha'=hoje+2, hr/hrs=hora, " +
                "seg=segunda, ter=terca, qua=quarta, qui=quinta, sex=sexta, sab=sabado, dom=domingo, " +
                "'meio dia'=12:00, 'meia noite'=00:00, 'umas 3 da tarde'=15:00. Resolva-as para data/hora reais. " +
                "Responda SOMENTE um JSON valido, sem texto extra: " +
                "{\"servico\": <nome EXATO da lista ou null>, \"profissional\": <nome EXATO da lista ou null>, " +
                "\"data\": <\"AAAA-MM-DD\" ou null>, \"hora\": <\"HH:MM\" ou null>}. " +
                "Preencha somente o que o cliente realmente mencionou; o resto deixe null. Nunca invente.";
        String resp = call(sys, "Mensagem do cliente: " + mensagem, 120, 0.0);
        if (resp == null) return null;
        try {
            int a = resp.indexOf('{'), b = resp.lastIndexOf('}');
            if (a < 0 || b <= a) return null;
            Map<?, ?> m = JSON.readValue(resp.substring(a, b + 1), Map.class);
            Extracao e = new Extracao();
            e.servico = texto(m.get("servico"));
            e.profissional = texto(m.get("profissional"));
            String d = texto(m.get("data"));
            if (d != null) try { e.data = LocalDate.parse(d); } catch (Exception ignored) {}
            String h = texto(m.get("hora"));
            if (h != null) {
                Matcher mm = Pattern.compile("([01]?\\d|2[0-3]):([0-5]\\d)").matcher(h);
                if (mm.find()) {
                    String[] p = mm.group().split(":");
                    e.hora = String.format("%02d:%02d", Integer.parseInt(p[0]), Integer.parseInt(p[1]));
                }
            }
            return e;
        } catch (Exception ex) {
            log.warn("Falha ao interpretar extração da IA: {}", ex.getMessage());
            return null;
        }
    }

    private String diaDaSemanaPt(LocalDate d) {
        return switch (d.getDayOfWeek()) {
            case MONDAY -> "segunda-feira";
            case TUESDAY -> "terca-feira";
            case WEDNESDAY -> "quarta-feira";
            case THURSDAY -> "quinta-feira";
            case FRIDAY -> "sexta-feira";
            case SATURDAY -> "sabado";
            case SUNDAY -> "domingo";
        };
    }

    private String texto(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return (s.isEmpty() || s.equalsIgnoreCase("null")) ? null : s;
    }

    /** Campos extraídos de uma mensagem (qualquer um pode ser null). */
    public static class Extracao {
        public String servico;
        public String profissional;
        public LocalDate data;
        public String hora;
    }

    private Integer primeiroNumero(String s) {
        if (s == null) return null;
        Matcher m = Pattern.compile("\\d+").matcher(s);
        return m.find() ? Integer.valueOf(m.group()) : null;
    }
}
