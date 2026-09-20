# Open Casa Inteligente — API contract as actually observed

> Este contrato foi higienizado para o repositório: hosts, caminhos do Swagger e identificadores da
> conta de teste foram substituídos por placeholders (`<API_HOST>`, `<PORTAL_HOST>`, `<lock-ns>`,
> `<hub-idProduto>`, …); a versão completa fica fora do repositório (`docs/private/`, ignorada pelo git).

Sources: the Swagger 2.0 document served at `<PORTAL_HOST>/swagger/schema.json`
("Gerenciador de APIs Mibo" v1), the "Saiba mais sobre a utilização do token" panel on the docs page,
and ~17 read-only probe calls made on 2026-09-20 with the case's own temporary token. Nothing here
contains a token, a credential, a personal e-mail or a real device identifier.

Only the endpoints the case needs are documented. The Swagger lists 39; the other groups (lamps,
sensors, GDI sub-accounts, lock passwords, camera recordings) are out of scope.

## 1. Transport conventions

| Aspect | Contract |
|---|---|
| Method | `POST` with a JSON body for every endpoint used here (`GET` exists only for `/gdi/subcontas/v1`) |
| Headers | `Content-Type: application/json`, `Authorization: Bearer <token>` |
| Host | Descriptions and the case brief say `https://<API_HOST>`; the Swagger `host` and the docs playground use `https://<PORTAL_HOST>`, which serves the same paths. From a browser the `<API_HOST>` fails with CORS; a native app has no CORS, so `<API_HOST>` is expected to work. **The base URL is configured locally (`local.properties` → `smarthome.apiHost`), never versioned; confirm on the first app call.** |
| HTTP status | **Always `200`, even on errors.** The outcome lives inside the body. |
| Token lifetime | Max **2 hours**. `renovarToken` issues a new one "with extended duration". |

### 1.1 Response envelopes — two shapes coexist

Shape A (most endpoints):

```json
{ "statusCode": 200, "body": { "status": "sucesso", "data": ... } }
{ "statusCode": 404, "body": { "status": "erro",    "msg": "Dispositivo não encontrado" } }
```

Shape B — flat, no wrapper (streaming endpoints, the documented `criar-fluxo-video` response, and
**every authentication failure**):

```json
{ "status": "sucesso", "data": ... }
{ "status": "erro", "msg": "Token não está presente na requisição" }
{ "status": "erro", "msg": "Erro desconhecido, por favor tente novamente mais tarde" }
```

The parser must accept both: unwrap `body` when present, then read `status`/`data`/`msg`.

### 1.2 Authentication failures

| Situation | Observed body (HTTP 200) |
|---|---|
| No `Authorization` header | `{ "status": "erro", "msg": "Token não está presente na requisição" }` |
| Invalid token | `{ "status": "erro", "msg": "Erro desconhecido, por favor tente novamente mais tarde" }` |
| Expired token | Not probed; assumed identical to "invalid" (same generic message). `[ASSUMED]` |

There is no dedicated 401/403. The only signal that a token was rejected is the generic
"Erro desconhecido" on an otherwise well-formed request. Business rule (see ADR-002): a flat-shape
`status: "erro"` whose `msg` starts with "Erro desconhecido" on a request whose body is known to be
valid is treated as **token rejected**.

### 1.3 Account quota — every call counts

`POST /streaming/cota-disponivel/v1` with body `{}` returned:

```json
{ "status": "sucesso",
  "data": { "streaming_gb": { "base": 1000, "custom": 1000, "total": 2000 },
            "requests":     { "base": 0,    "custom": <n>,  "total": <n> } } }
```

The test account had **~300 requests** available at probe time (value read on 2026-09-20; 2 GB of streaming). Each API call
consumes one. Design consequences: cache the device list, no polling loops, classify devices with as
few calls as possible, never retry blindly (ADR-006).

## 2. Authentication

### POST /autenticacao/renovarToken
Swagger path; the description says the real endpoint is `/autenticacao/renovar-token/v1` on `<API_HOST>`. `[NEEDS CLARIFICATION: which path the api- host accepts]`

Request: `{ "token": "<current token>" }` — Response: `200` "Token Gerado" (shape not documented; not probed because it would rotate the working token).

## 3. Devices

### POST /produtos/listar-dispositivos/v1

Request (required: `tamanhoPagina`, `pagina`):

```json
{ "tamanhoPagina": 20, "pagina": 1, "origem": "todos" }
```

`origem` ∈ `"vinculados"` | `"compartilhados"` | `"todos"` (default `"todos"`).

Response `data` is a **plain array** — no total count, no page count. Pagination is therefore blind:
request pages until one comes back empty or shorter than `tamanhoPagina`. Verified: page 2 with
size 5 returned items 6–10 of the same list. `origem: "compartilhados"` returned `data: []` with
`status: "sucesso"` — that is the "empty list" case.

Device object:

```json
{
  "ns": "<lock-ns>",
  "modelo": "IOT-MFR1001-IB",
  "nome": "MFR 1001",
  "status": "online",
  "versao": "1.1.0",
  "subdispositivo": true,
  "idProduto": "<lock-idProduto>",
  "ultimaVezOnline": "20260918T132704Z",
  "origem": "vinculado",
  "atualizacaoDisponivel": false,
  "dispositivoPai": "<hub-ns>",
  "idProdutoDispositivoPai": "<hub-idProduto>"
}
```

(Real values live in the local copy of the contract, outside the repository. The device `nome` as
returned by the API carries a serial suffix, e.g. `"<modelo>-<last 4 of ns>"`.)

Notes: `status` ∈ `"online"` | `"offline"`; `idProduto` may be `""` (seen on two cameras);
`ultimaVezOnline` is compact ISO-8601 in UTC; `dispositivoPai` / `idProdutoDispositivoPai` appear only
when `subdispositivo` is `true`; `origem` on a device is singular (`"vinculado"`), unlike the request
filter (`"vinculados"`).

### POST /produtos/funcoes/v1

Request `{ "ns": "<serial>" }`. Response `data: { "funcoes": "<comma-separated capability codes>" }`.

- Camera (iM7 3M Full Color): `"BreathingLight,…,AudioTalk,…,RTSV2,RTSV1,…,CloudStorage,…"` — **`RTSV*` = real-time streaming**.
- Lock (composite ns, see §5) and hub: `"funcoes": ""`.
- Lock with its plain ns: shape A `statusCode: 404`, `msg: "Dispositivo não encontrado"`.

### POST /produtos/online/v1 — `{ "ns" }` → online/offline (invalid devices report offline). Not probed.
### POST /produtos/buscar-dispositivo/v1 — `{ "ns" }` (plain or composite) → device data. Not probed.

## 4. Device classification (request-frugal rule)

Decide by `modelo` first, confirm with `funcoes` only when about to open video:

| Kind | Rule | Test account (by type; identifiers kept out of the repository) |
|---|---|---|
| Camera | `modelo` starts with `iM`; `funcoes` contains `RTSV` | 3 cameras (models iM7 3M Full Color, iM7-FC, iM3-C) — 2 online, 1 offline; `idProduto` was `""` on two of them |
| Lock | `modelo` contains `MFR`, `subdispositivo: true` | 5 locks (models MFR 1001, MFR 2020 V, MFR 2040, MFR 7001 V, MFR 2030) — 2 online, each hanging off one of the hubs |
| Hub | `modelo` `IOT-ZG2-IB`, `nome` "MCA 1002", `subdispositivo: false` | 2 hubs, both online |
| Other | everything else (sensors, switches, video door phone) | 7 devices (models MSM 1001, MFD 2020 D, MSI 1001, MFV 7000, EFLS 6030, ECZ 1002, MTU 1001), mostly offline |

The test account lists 17 devices on page 1 (`tamanhoPagina: 20`). Serials and `idProduto` values are
in the local copy of the contract, outside the repository.

## 5. Locks

Every lock endpoint addresses the lock as a sub-device of its hub:

- `ns` = `<lock-ns>_<hub-ns>_<hub-idProduto>` (three real identifiers joined by `_`; concrete example in the local copy of the contract)
- `idProduto` = the lock's own `<lock-idProduto>`

| Endpoint | Request body | Observed `data` |
|---|---|---|
| `POST /fechaduras/controle-fechadura/v1` | `{ ns, idProduto, aberto: true\|false }` (true = open, false = lock) | not probed (changes state) |
| `POST /fechaduras/status-abertura/v1` | `{ ns, idProduto }` | `{ "aberto": true }` |
| `POST /fechaduras/volume/v1` | `{ ns, idProduto }` — **contract bug:** Swagger `required` lists `productId` but the property is `idProduto`; sending both worked | `{ "volume": 1 }` (0..3) |
| `POST /fechaduras/mudar-volume/v1` | `{ ns, idProduto, volume: 0..3 }` | not probed |
| `POST /fechaduras/status-abrir-remoto/v1` | `{ ns, idProduto }` | `{ "habilitado": true }` |
| `POST /fechaduras/habilitar-abrir-remoto/v1` | `{ ns, idProduto, habilitar: true\|false }` | not probed — **precondition** for remote open |
| `POST /fechaduras/historico-abertura/v1` | `{ ns, quantidade }` (default 50, no `idProduto`, not paginated) | `[ { "tempoLocal": "20260918T102735", "nome": "APP", "tipo": "usuarioRemoto" }, { "tempoLocal": "20260918T102429", "nome": "", "tipo": "interno" } ]` |

`tempoLocal` carries no timezone suffix (device-local time). Known `tipo` values so far:
`usuarioRemoto`, `interno`. `[NEEDS CLARIFICATION: full list of tipo values]`

## 6. Cameras and streaming sessions

### POST /cameras/criar-fluxo-video/v1

Request: `{ "ns": "<camera serial>", "stream_gb": 0.5, "canalVideo": 0, "streamId": 1 }`

- `stream_gb` (float, default 1): bandwidth cap for this session; only real consumption is debited.
- `canalVideo` (int, default 0): lens/channel; single-lens devices always 0.
- `streamId` (int, default 0): 0 = main/high resolution, 1 = secondary/lower bandwidth.

Documented response (shape B):

```json
{ "status": "sucesso",
  "data": { "url": "https://<PORTAL_HOST>/stream/<session>-...",
            "monitor_url": "https://<PORTAL_HOST>/monitor_stream.html?session_id=...",
            "session_id": "...", "quota_gb": 1.0, "warning": "..." } }
```

- `url` is a **fragmented MP4 (fMP4) HTTP stream** meant for MediaSource Extensions — not RTSP, not HLS.
- `monitor_url` is a ready HTML page with a player and consumption stats.
- The stream **expires if not opened within 15 s** of creation and ends automatically at `stream_gb`.
- Errors: `402` "Quota de streaming insuficiente", `500` "Erro desconhecido" (as documented status codes;
  given §1, expect them inside the body rather than as HTTP status). `[ASSUMED]`

Not probed — each call opens a real session and spends quota.

### Session management (body `{}` unless noted)

| Endpoint | Body | Purpose |
|---|---|---|
| `POST /streaming/cota-disponivel/v1` | `{}` | quota (see §1.3) — shape B |
| `POST /streaming/minhas-sessoes/v1` | `{}` | active sessions |
| `POST /streaming/sessao-info/v1` | `{ session_id }` | one session's info |
| `POST /streaming/encerrar-sessao/v1` | `{ session_id }` | end a session — call on player exit to free quota |

## 7. Contract contradictions to defend in code

1. HTTP status is always 200; the Swagger documents 402/404/500 as HTTP codes.
2. Two envelope shapes (A wrapped, B flat) for the same API.
3. Token rejection is indistinguishable from a generic server error except by message text.
4. `/fechaduras/volume/v1`: `required: productId` vs property `idProduto`.
5. Docs page Python sample uses `requests.get` with `{ns, idProduto}` for listar-dispositivos; the
   Swagger and the docs' own cURL sample use `POST` with `{tamanhoPagina, pagina, origem}` — verified
   empirically that the Swagger is right.
6. `renovarToken` path differs between Swagger (`/autenticacao/renovarToken`) and description
   (`/autenticacao/renovar-token/v1`).
7. Two hosts (`<API_HOST>` in descriptions and the case brief vs `<PORTAL_HOST>` in the Swagger `host`).
8. Request filter uses plural `vinculados`; device field uses singular `vinculado`.

## 8. Open questions

| # | Question | Blocking? | How to resolve |
|---|---|---|---|
| 1 | Does `<API_HOST>` accept the same paths from the app? | No (host is configurable) | first app call |
| 2 | Expired-token response — same as invalid? | No | let a token age past 2 h and call listar |
| 3 | `renovarToken` response shape and accepted path | No (RF04 works without renewal) | one call near the end of a session |
| 4 | Body of `controle-fechadura` / `mudar-volume` / `habilitar-abrir-remoto` success responses | No | first controlled call from the app, 1 request each |
| 5 | Exact body of the 402 quota error on `criar-fluxo-video` | No | only if quota runs out |
| 6 | Full list of `historico-abertura.tipo` values | No | observe real history |
| 7 | Does the docs banner "Você ainda não possui um plano ativo" affect the token? | No — probes succeeded with ~300 requests available | — |
