# Chadbot — Backend

API, bot de WhatsApp, jobs agendados e regras de negócio do [Chadbot](https://claude.ai/code/artifact/3ee685ea-080a-4888-9709-e2cb17564f7a) —
SaaS multi-tenant de agendamento por WhatsApp.

**Spring Boot 3.3 · Java 21 · PostgreSQL · Flyway · JWT**

---

## Rodando

O jeito recomendado é pelo script da raiz do projeto, que carrega o `.env` e sobe as dependências:

```powershell
.\up.ps1
```

`mvn spring-boot:run` puro **falha no boot**: `JWT_SECRET` e `DB_PASSWORD` são obrigatórios e não têm
valor padrão — é proposital, para nunca subir com segredo fraco.

```bash
mvn -q -DskipTests compile   # verificação rápida antes de commitar
mvn -DskipTests package      # gera o jar
```

---

## Variáveis de ambiente

| Variável | Para que serve |
|---|---|
| `SPRING_DATASOURCE_URL` | JDBC do Postgres — **sem** usuário e senha embutidos |
| `DB_USERNAME` · `DB_PASSWORD` | credenciais do banco (obrigatórias) |
| `JWT_SECRET` | segredo HS256, mínimo 32 bytes (obrigatório) |
| `JWT_EXPIRATION` | validade do token em ms |
| `EVOLUTION_URL` · `EVOLUTION_KEY` | endereço e chave da Evolution API |
| `APP_BACKEND_URL` | URL pública deste serviço, usada no webhook da Evolution |
| `APP_CORS_ORIGINS` | origens liberadas, separadas por vírgula |
| `AI_API_URL` · `AI_API_KEY` · `AI_MODEL` | provedor de IA compatível com OpenAI |
| `MP_ACCESS_TOKEN` · `MP_NOTIFICATION_URL` | Mercado Pago (vazio = modo simulado) |
| `SUPERADMIN_EMAIL` · `SUPERADMIN_PASSWORD` | cria o primeiro admin no boot (idempotente) |

> **Atenção na URL JDBC:** o driver não aceita `usuario:senha@host` — as credenciais vão separadas em
> `DB_USERNAME` e `DB_PASSWORD`. A Evolution API, por outro lado, usa URI no formato libpq **com** as
> credenciais dentro e precisa de `?schema=evolution`.

---

## Estrutura

```
com.agendamento.backend
├── config/       SecurityConfig (CORS, filtros, papéis) e RestTemplate com timeout
├── controller/   20 controllers REST — painel, bot, público e back-office
├── dto/          objetos de entrada e saída, agrupados por área
├── entity/       18 entidades JPA + o enum Plano com os recursos por nível
├── exception/    tratamento global que devolve JSON legível
├── repository/   Spring Data JPA
├── security/     JwtAuthFilter, JwtService e o TenantContext
└── service/      19 serviços — regra de negócio e jobs agendados
```

**Serviços centrais:**

- `BotService` — a conversa do WhatsApp: preenchimento por slots, menu, cancelamento, remarcação
- `DisponibilidadeService` — **fonte única** de horário livre e conflito, ciente de duração e da grade
  de cada profissional
- `AiService` — camada de IA isolada; qualquer falha devolve nulo e o bot segue pelo menu
- `EvolutionApiService` — instâncias, QR code e envio de mensagens
- `LembreteService`, `RecorrenciaService`, `CampanhaService` — os jobs agendados
- `PlanoService` — o portão dos recursos por plano

---

## Banco de dados

Flyway em `src/main/resources/db/migration`, com `ddl-auto: none`. O Hibernate nunca altera o schema.

**Migration aplicada não se edita** — mudança de schema é sempre um arquivo novo, com o próximo
número da sequência.

---

## Regras que não se quebram

- Toda consulta por telefone de cliente **filtra por `tenantId`**. Sem isso, um cliente que frequenta
  dois estabelecimentos do sistema enxerga o agendamento do outro.
- Disponibilidade e conflito passam **só** pelo `DisponibilidadeService`.
- Entidade Lombok com `@Builder` ignora valor padrão de campo — anote com `@Builder.Default`.
- O webhook de pagamento nunca confia no que chega: o status real é reconsultado na API antes de
  liberar qualquer coisa.

---

## Deploy

Push na `main` dispara o deploy automático no Render (Docker). Antes de empurrar, `mvn -q -DskipTests
compile` precisa sair limpo.
