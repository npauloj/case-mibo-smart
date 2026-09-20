# Guia: como o contrato da API foi lido

Este guia explica **de onde veio** o que está em [`docs/api-contract.md`](../api-contract.md) e como
cada surpresa do contrato virou uma regra na camada de dados. A fonte completa, endpoint por endpoint,
é o próprio `api-contract.md`; aqui está o método.

## 1. Onde está a documentação

| O quê | Onde |
|---|---|
| Página de documentação (playground) | `https://<PORTAL_HOST>/docs/` |
| Contrato formal | `https://<PORTAL_HOST>/swagger/schema.json` — Swagger 2.0 (YAML), "Gerenciador de APIs Mibo" v1, 39 endpoints |
| Painel "Saiba mais sobre a utilização do token" | na própria página `<PORTAL_HOST>/docs/`: formato do cabeçalho, exemplo cURL/Python, validade de 2 h e renovação |
| Enunciado do case | não versionado (confidencial); cita o host da API (`<API_HOST>`) e o endpoint `produtos/listar-dispositivos/v1` |

Os hosts reais (`<PORTAL_HOST>`, `<API_HOST>`) vêm no e-mail do case e ficam só em `local.properties`
(`smarthome.portalHost`, `smarthome.apiHost`) — ver [running.md](running.md), "Configuração local".

A página `<PORTAL_HOST>/docs/` é uma SPA que lê o `schema.json`; o arquivo pode ser baixado direto (o
nome diz `.json`, o conteúdo é YAML).

## 2. O que o Swagger documenta — e o que não

Documenta bem: método (tudo `POST` com JSON, exceto `GET /gdi/subcontas/v1`), autenticação
(`Bearer` no cabeçalho `Authorization`), e os **corpos de requisição** com campos obrigatórios e defaults
(ex.: `listar-dispositivos` exige `tamanhoPagina` e `pagina`; `origem` aceita `todos | vinculados |
compartilhados`).

Não documenta: **nenhuma resposta** além de "Operação completada" — a única exceção é
`cameras/criar-fluxo-video/v1`, que descreve `status`, `data.url`, `data.monitor_url`, `session_id`,
`quota_gb` e os erros 402/500. Também não documenta como falhas de autenticação são sinalizadas.

Consequência: a camada de dados não podia ser escrita a partir do Swagger. Os formatos foram descobertos
empiricamente.

## 3. Como descobrimos os formatos reais

Cerca de 17 chamadas **somente de leitura**, feitas em 20/09/2026 com o token temporário do case, a partir
da própria página `<PORTAL_HOST>/docs/` (mesma origem, sem CORS):

- `listar-dispositivos` (páginas 1 e 2, tamanhos 20 e 5, `origem` `todos` e `compartilhados`)
- `funcoes` para uma câmera, uma fechadura e um hub
- `status-abertura`, `volume`, `status-abrir-remoto`, `historico-abertura` da fechadura online
- `streaming/cota-disponivel`
- duas chamadas propositalmente erradas: sem cabeçalho `Authorization` e com token inválido

Não foram chamados endpoints que mudam estado ou gastam cota: `controle-fechadura`, `mudar-volume`,
`habilitar-abrir-remoto`, `criar-fluxo-video`, `encerrar-sessao`, `renovarToken`. Nenhum token aparece
nos registros; os números de série e `idProduto` dos dispositivos da conta de teste ficam fora do
repositório (placeholders `<lock-ns>`, `<hub-idProduto>` etc. nos docs versionados).

## 4. O que encontramos

| Achado | Onde vive no código |
|---|---|
| **HTTP é sempre 200**, inclusive em erro; o resultado está no corpo | leitor de envelope em `:shared:data` ([ADR-002](../adr/ADR-002-errors-as-values.md)) |
| **Dois envelopes**: `{statusCode, body:{status, data|msg}}` na maioria; `{status, data|msg}` plano em streaming e em toda falha de autenticação | o leitor desembrulha `body` quando existe e lê `status/data/msg` |
| Token inválido → `{"status":"erro","msg":"Erro desconhecido, por favor tente novamente mais tarde"}`; sem token → `"Token não está presente na requisição"` | regra de negócio no caso de uso: "Erro desconhecido" em requisição válida = token rejeitado (E3) |
| `data` de `listar-dispositivos` é um **array sem metadados** de total/páginas | paginação "cega": para quando a página vem vazia ou curta (D6–D8) |
| Campos do dispositivo: `ns`, `modelo`, `nome`, `status` (`online|offline`), `idProduto` (pode ser vazio), `subdispositivo`, `dispositivoPai`, `idProdutoDispositivoPai`, `origem`, `ultimaVezOnline` (ISO compacto, UTC) | DTOs com `@SerialName` em `:shared:data`; domínio só com nomes em inglês ([ADR-004](../adr/ADR-004-partner-agnostic-domain.md)) |
| Câmera = `funcoes` contém `RTSV`; fechadura = `modelo` começa com `MFR` e é sub-dispositivo; hub = `IOT-ZG2` | classificação por `modelo` primeiro, `funcoes` só para confirmar streaming (economiza requisições) |
| Fechadura usa `ns` composto `<lock>_<hub>_<idProdutoHub>` + `idProduto`; `volume/v1` exige `productId` no `required` mas o campo é `idProduto` | enviado em dobro pela camada de dados; o domínio nunca vê isso |
| Abertura remota precisa estar habilitada (`status-abrir-remoto`) antes de `controle-fechadura` | estado `RemoteOpenDisabled` na máquina de estado da fechadura (L-series) |
| Stream de vídeo é **MP4 fragmentado sobre HTTP** (não RTSP/HLS); expira em 15 s sem player; cota 402; `monitor_url` com player HTML | player nativo via `expect/actual` + fallback WebView ([ADR-005](../adr/ADR-005-live-video-native-players.md)) |
| Conta com **orçamento de requisições** (`requests.total` ≈ 300, lido em 20/09/2026) | cache, zero polling, retry só em rede ([ADR-006](../adr/ADR-006-local-persistence-and-request-budget.md)) |
| Token dura no máximo 2 h; `renovarToken` emite outro | S7/S10; ver [token.md](token.md) |

## 5. Contradições do contrato

1. O exemplo Python da página `<PORTAL_HOST>/docs/` usa `requests.get` com `{ns, idProduto}` para
   `listar-dispositivos`; o Swagger e o cURL oficial do painel usam `POST` com
   `{tamanhoPagina, pagina, origem}`. Verificado: o `POST` funciona. O Swagger é a fonte da verdade.
2. `/fechaduras/volume/v1`: `required: [ns, productId]` mas a propriedade declarada é `idProduto`.
3. Dois hosts: `host:` do Swagger e a página usam `<PORTAL_HOST>`; as descrições e o enunciado
   usam `<API_HOST>`. Do navegador só o primeiro responde (CORS); no app o segundo deve funcionar
   — o host fica em `local.properties` (nunca versionado) e é confirmado na primeira chamada real do app
   (a confirmar na onda 1).
4. Dois formatos de envelope para o mesmo "sucesso"/"erro" (§4).
5. Sucesso de streaming documentado como envelope plano, diferente do resto.

Cada uma virou linha de teste na camada de dados; nenhuma vaza para o domínio ou para a UI.

## 6. Perguntas ainda abertas (marcadas na SPEC como assunções)

- Resposta exata de `renovarToken` (só será chamada na onda 3, porque rotaciona o token).
- Lista completa de `tipo` no histórico (vistos: `usuarioRemoto`, `interno`; desconhecidos são exibidos crus).
- Se `<API_HOST>` aceita os mesmos caminhos que `<PORTAL_HOST>` (a confirmar no app).
- Se a página `monitor_url` funciona dentro de um WebView (a confirmar na onda 2).
