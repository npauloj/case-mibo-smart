# Case Mibo Smart — Documento de produto e arquitetura

> Documento de entrega, fechado em 23/09/2026. Os números desta versão foram medidos na data;
> o que ficou em aberto está nomeado no §11, com o motivo.
> Referências: `docs/adr/` (decisões), `docs/specs/SPEC.md` (critérios de aceite),
> `docs/api-contract.md` (contrato observado), `AI-LOG.md` (uso de IA),
> guias práticos em `docs/guides/` ([token](guides/token.md), [leitura do Swagger](guides/swagger.md),
> [como rodar](guides/running.md)).

## 1. Contexto e objetivo

- Case técnico da plataforma Open Casa Inteligente para a vaga de Analista de Desenvolvimento de Produto II — Android.
- Entregar um app Kotlin Multiplatform (Android principal, iOS como prova do compartilhamento) que
  consome a Open Casa Inteligente: token de acesso, lista de dispositivos, vídeo ao vivo de câmeras,
  controle de fechadura.
- O enunciado pede "como você pensa, prioriza, comunica e codifica" — este documento é a parte
  "pensa e comunica"; o repositório (Issues → PRs → ADRs) é a trilha do "prioriza e codifica".

## 2. Leitura do problema

- App conectado a hardware real: os estados que importam não são só loading/erro, mas "comando enviado
  e não confirmado", "câmera offline", "abertura remota desabilitada", "token expirou no meio da sessão",
  "cota de streaming esgotada".
- O contrato da API tem particularidades que definem a arquitetura (ver §6): HTTP sempre 200, dois
  formatos de envelope, token rejeitado indistinguível de erro genérico, orçamento finito de requisições.
- Três entregáveis: repositório versionado, este documento, log do uso de IA.

## 3. Escopo e priorização

| Onda | Escopo | Requisitos | Racional |
|---|---|---|---|
| 0 | *Walking skeleton*: repositório, CI, esqueleto de módulos, specs, ADRs, testes de arquitetura | — | a trilha começa antes do código |
| 1 | Sessão/token (tela, validação, expiração) — constrói client HTTP, envelope, erros tipados e cofre por necessidade própria | RF01, RF04 | primeira fatia vertical; não existe "fatia de fundação" |
| 2 | Em paralelo: lista + filtro + paginação + estados · vídeo ao vivo · fechadura (status, abrir/fechar, pré-condição remota, volume) | RF02, RF03, RF04, RF05, RF06, RF07, RF08 | as três não dependem entre si; vídeo e fechadura são o "hardware real" |
| 3 | Histórico da fechadura, renovação de token, iOS com a lista, módulo Java, R8 no release, este PDF | RF09, ★ Java | só após o núcleo funcionar no Android |

- Circuit breaker: must-have para a apresentação são as ondas 0–2. Se o prazo apertar, a onda 3 encolhe
  nesta ordem: módulo Java (ADR-007) → histórico (RF09) → fallback WebView do vídeo → R8. Vídeo e
  fechadura não são cortáveis.
- Fora de escopo por decisão: lâmpadas, sensores, gravações, senhas de fechadura, criação de conta.

### 3.1 Visão de produto: o que os usuários reais pedem

Antes de escrever as issues, li o que quem usa o app oficial da plataforma diz nas lojas
(`docs/research/user-feedback.md`: 250 avaliações recentes da App Store, 20 da Play, Reclame Aqui e o
fórum oficial; leitura em 20/09/2026). Nota agregada 4,8★ nas duas lojas, mas as avaliações recentes
*com texto* são majoritariamente 1★ — a leitura é sobre o texto.

| O que mais dói (≈ menções em 270) | O que o case faz a respeito |
|---|---|
| Vídeo ao vivo trava em "9x %" sem erro nomeado (≈50) | Etapas visíveis, **timeout de 20 s com mensagem**, retry e fallback web — nunca um spinner infinito (SPEC U1, V3–V5) |
| Propaganda e push de marketing num app de segurança (≈45) | Zero banner, pesquisa, interstitial ou notificação; abre direto na lista, câmera em dois toques (U2, U7) |
| Câmera "offline do nada", sem explicação (≈20) | Indicador + "visto pela última vez há X" na lista e na fechadura (U3) |
| Fechadura: histórico chega atrasado e sem nome; volume que "sumiu" (≈10) | Comando → confirmação explícita; histórico com quem e quando; volume com rótulos (L3–L7, U4) |
| Sessão que "perde a senha" e obriga a reinstalar (≈6) | Expiração explicada e retorno à tela anterior após o novo token (U5) |
| Erros genéricos ("Erro desconhecido") | Toda mensagem diz a causa e oferece uma ação; nada de código HTTP (U6) |

Fora do escopo do case, mas registrado como próximos passos de produto: gravações/timeline/SD
(segundo maior cluster), notificações de dispositivo com granularidade, um app e uma conta para todas
as linhas, e um relato de segurança da fechadura (senha excluída continuando ativa) que merece
tratamento de incidente. O detalhe está no §5–6 do documento de pesquisa.

## 4. Arquitetura

```
:shared:domain   modelos, contratos (DeviceRepository, LockRepository, StreamingRepository,
                 SecureTokenStore), erros e resultados por caso de uso — sem dependências
:shared:data     implementação do parceiro: Ktor, DTOs, EnvelopeReader, mapeadores, SQLDelight;
                 cofre do token em `platform.vault` (Keystore no androidMain, Keychain no iosMain)
:shared:app      casos de uso, ViewModels, UI Compose Multiplatform por feature, módulos Koin;
                 superfície de vídeo `camera.platform.LiveVideoPlayer` (Media3 no androidMain,
                 WKWebView no iosMain)
:androidApp      Activity + Application (só plumbing, nenhum `actual`)
iosApp           host SwiftUI (Swift não hospeda `actual` Kotlin)
:legacy-catalog  módulo Java pequeno (SDK legado simulado) — onda 2, cortável
```

- `expect/actual` só existe em pacotes chamados `platform` dentro do módulo dono da abstração
  (regra 8 dos testes de arquitetura). O player é superfície de UI, não contrato de domínio (ADR-005).

- Dependências apontam para dentro; `:shared:domain` não conhece Ktor, Koin, Compose nem SQLDelight
  (ADR-001). "Suporte a múltiplos parceiros" = um segundo módulo `data` implementando os mesmos contratos
  (ADR-004).
- Apresentação: MVVM com um estado imutável por tela (`StateFlow`), ações como funções suspensas,
  estados de hardware modelados como tipos selados (ADR-003).
- Erro é valor: a camada de dados lança exceções tipadas; cada caso de uso devolve o resultado da sua
  intenção (`Loaded | Empty | TokenRejected | Offline | Failure`) (ADR-002).
**Vídeo ao vivo** — a janela de 15 s é do parceiro, não nossa: a url expira se nenhum player a abrir
nesse prazo, e é por isso que o caso de uso **publica** estados em vez de devolver um (ADR-005).

```
usuário    ViewModel          parceiro (portal)        player (Media3)
   │          │                      │                       │
  toque ─────▶│                      │                       │
   │          │ criar-fluxo-video ──▶│                       │
   │          │◀── url, session_id, monitor_url, quota_gb     │   ◀── medido 23/09: só
   │          │                      │                       │       no host do portal
   │          │ Creating ────────────────────────────────────▶ prepare(url)
   │          │                      │                       │
   │          │           ┌─ 1º quadro em ≤ 20 s ────────────┤  [ASSUMED]
   │          │           │  Live                            │
   │          │           └─ nada  → Reconnecting 1s/3s/7s ──┤  teto de 2 sessões novas
   │          │                                 └─ Failed + "Abrir no player web"
   │          │                      │                       │
  sair ──────▶│ encerrar-sessao ────▶│   (NonCancellable)    │ release()
```

**Fechadura** — o comando e a confirmação são coisas separadas, e é essa separação que o app não
esconde: nenhuma tela afirma que a porta abriu antes de a leitura concordar (ADR-021).

```
usuário    ViewModel                    parceiro
   │          │                            │
 "Abrir" ────▶│ controle-fechadura ───────▶│
   │          │◀── aceito                  │        CommandSent — todo controle morto
   │          │                            │
   │          │ status-abertura ──────────▶│        uma leitura, sem polling (ADR-006)
   │          │◀── aberto: true            │        concorda  → Ready(destrancada)
   │          │◀── aberto: false           │        discorda  → CommandExpired
   │          │                            │                    (âmbar, com "Verificar")
   │          │◀── erro                    │        → CommandFailed, e a porta fica como estava
```

O `CommandExpired` é o estado mais importante das duas telas: ele diz *"o comando saiu e a porta não
respondeu"*, que não é a mesma coisa que *"a porta recusou"* — e ninguém consegue distinguir as duas
daqui.

## 5. Stack e justificativas

| Camada | Escolha | Por quê (resumo) |
|---|---|---|
| Linguagem/UI | Kotlin 2.4 · Compose Multiplatform 1.11 | estado da arte JetBrains; Compose iOS estável |
| Rede | Ktor 3.5 | cliente multiplataforma oficial |
| Persistência | SQLDelight 2.3 | maduro em KMP; Room 2.8 seria equivalente; Room 3.0 ainda alpha |
| DI | Koin 4.2 | modularização por camada; Metro 1.0 (compile-time) citado como fronteira |
| Concorrência | Coroutines + Flow/StateFlow | structured concurrency; cancelamento propagado |
| Vídeo | Media3 (Android) · WKWebView em `monitor_url` (iOS) · WebView fallback no Android | stream é fMP4 sobre HTTP; AVPlayer não reproduz sem HLS; VLCKit descartado (tamanho + interop fora do prazo) |
| Segurança | Keystore / Keychain via interop direto | controle fino de acessibilidade do segredo |
| Testes | Mokkery · kotlinx-coroutines-test · Turbine | mocks em Kotlin/Native; `state.value` + `advanceUntilIdle()` para o estado da tela, Turbine só para eventos one-shot |

## 6. O contrato da API e como ele moldou o código

- HTTP sempre 200 → o "status" real é lido do corpo; `EnvelopeReader` aceita os dois formatos.
- Token inválido devolve "Erro desconhecido…" → regra de negócio no caso de uso, não no client.
- Orçamento de ~300 requisições (valor lido em 20/09/2026) → cache local da lista, classificação por `modelo` antes de `funcoes`,
  zero polling, retry só para falha de rede (ADR-006).
- Fechadura é sub-dispositivo: `ns` composto `lock_hub_idProdutoHub`; abertura remota precisa estar
  habilitada; `volume/v1` exige `productId` mas o campo é `idProduto` (enviado em dobro).
- Stream expira em 15 s sem player; sessão encerrada ao sair, inclusive sob cancelamento.
### 6.1 Cada contradição e onde ela é defendida

As oito estão listadas no §7 do `docs/api-contract.md`. Nenhuma virou um `if` espalhado: cada uma tem
um lugar só, e um teste que falha se esse lugar mudar.

| # | Contradição do contrato | Onde é tratada | Teste que a prova |
|---|---|---|---|
| 1 | HTTP é sempre `200`; o Swagger documenta `402/404/500` como código HTTP | `EnvelopeReader` lê o status do **corpo** | `EnvelopeReaderTest.unknownErrorOnOkIsJustAnApiError` |
| 2 | Dois formatos de envelope (A embrulhado, B plano) para a mesma API | `EnvelopeReader` aceita os dois | `EnvelopeReaderTest.wrappedSuccess` · `.flatSuccess` |
| 3 | Token recusado é indistinguível de erro genérico, exceto pelo texto | regra de negócio no caso de uso, **não** no client (ADR-002) | `EnvelopeReaderTest.forbiddenIsTokenExpiredWithServerMessage` · `.forbiddenWithUnparseableBodyStillExpires` |
| 4 | `volume/v1` exige `productId`, mas a propriedade é `idProduto` | `LockRequests.volume` manda os dois, e a quarentena fica nele | `LockRequestsTest.volumeRequestCarriesBothIds` |
| 5 | A página de docs mostra `GET` com `{ns, idProduto}`; o Swagger, `POST` com `{tamanhoPagina, pagina, origem}` | vale o Swagger, verificado na prática | `ListDevicesTest.firstPageUsesDefaults` |
| 6 | `renovarToken` tem caminho diferente no Swagger e na descrição | `SmartHomeApi.RENEW_TOKEN_PATH` fixa o que responde | `RenewTokenTest.exactRequest` |
| 7 | **Dois hosts** — API nas descrições, portal no `host:` do Swagger | `SmartHomeApi.streamingBaseUrl`: streaming no portal, o resto na API (ADR-025) | `WatchLiveVideoTest.streamingCallsGoToThePortalHostAndTheRestDoesNot` |
| 8 | Filtro usa plural `vinculados`; o campo do dispositivo usa singular `vinculado` | `OriginFilter` traduz numa direção só | `OriginFilterTest.mapsToWireValues` |

A número **7 merece destaque**, porque foi a única que escapou: estava documentada desde o começo e
mesmo assim o app chamou `criar-fluxo-video` no host errado desde o commit 0. Os dois hosts respondem
`200`, devolvem uma `url` plausível e nenhum reporta erro — nada dentro do app podia distingui-los, e
nenhum teste com `MockEngine` também, porque as fixtures foram escritas a partir do Swagger. O custo
não foi a imagem que faltava: o host errado não devolve `session_id`, então o encerramento de sessão
era código morto, e **27 sessões ficaram abertas numa conta compartilhada**. Está inteiro no ADR-025 e
nas linhas do `AI-LOG.md` de 23/09.

## 7. Estados de hardware tratados

- Fechadura: trancada · destrancada · comando em andamento · abertura remota desabilitada · offline ·
  estado não confirmado (com ação "Verificar", sem polling).
- Câmera: criando sessão · ao vivo · reconectando (n/3) · expirada · cota esgotada · offline · falha
  com fallback web.
- Sessão: sem token · válida (expira em …) · expirada (rota para a tela de token com mensagem específica).
**As capturas existem como teste, não como anexo neste documento.** São 42 goldens — um por estado das
seis telas — gravados por `ScreenshotTest` (Roborazzi + Robolectric, ADR-024) e publicados como
artefato do job `verify`. Não estão versionadas de propósito: a rasterização de fonte difere entre
sistemas operacionais, então uma imagem gravada numa máquina Windows diverge da do runner Linux em
cada pixel de antialiasing, por motivo que nada tem a ver com a UI. O `.gitignore` carrega essa
exclusão com data de validade escrita: ela sai no dia em que uma baseline gravada pelo CI for
commitada, e aí o conjunto vira porta de regressão em vez de figuras anexadas ao PR.

O inventário dos estados não é mantido à mão: cada tela tem um `PreviewParameterProvider` com todos os
seus estados, e o `ScreenshotTest` afirma, por tela, que nomeou **todos** eles. Acrescentar um estado
sem acrescentar a captura quebra o build.

## 8. Segurança

- Token só em Keystore/Keychain (`ThisDeviceOnly`), nunca em preferências simples, logs ou UI (só o sufixo).
- `Authorization` sanitizado no logger HTTP; CI falha se o padrão de token aparecer no repositório.
- Nenhuma credencial versionada (regra do enunciado). Detalhes: ADR-008.

## 9. Qualidade, testes e método

- Metodologia: SDD leve — spec com critérios EARS/Gherkin (`docs/specs/SPEC.md`) → ADRs → Issues no
  GitHub (uma por fatia vertical) → um PR por Issue com o "porquê" na descrição.
- TDD cirúrgico nas regras críticas: paginação/filtro, tradução de erro/token, máquina de estado da
  fechadura, política de retry do vídeo. UI e player fora do TDD.
- Uso de IA documentado em `AI-LOG.md` — inclusive o que a IA errou e como foi corrigido.
### 9.1 Números, medidos em 23/09/2026

| Módulo | Testes | O que eles cobrem |
|---|---|---|
| `:shared:domain` | 19 | modelos, formato do token, ordenação, resultados selados |
| `:shared:data` | 84 | contrato na fiação (`MockEngine`), envelopes, mappers, cache SQLDelight, cofre |
| `:shared:app` | 195 | casos de uso, máquinas de estado das telas, 42 goldens de screenshot |
| `:konture-test` | 11 | as regras de arquitetura, como teste que falha o build |
| `:legacy-catalog` | 5 | a interoperabilidade Java → Kotlin, escrita em Java (ADR-023) |
| **Total** | **314** | |

Cobertura agregada: **61,5 % de linhas, 61 % de ramos** (Kover). Três leituras que o número sozinho
esconde:

- Ele exclui, **por decisão explícita**, `*.platform*` e `*.ui.*` — as pontes `expect/actual` e a UI
  Compose. Essas são provadas por preview e golden, não por teste unitário, e contá-las inflaria o
  denominador com código que nenhum teste unitário deveria tocar.
- Até 23/09 o agregado **omitia o `:shared:data` inteiro** — o módulo com o contrato, os mappers e o
  cache, e o mais testado dos três. O número publicado teria sido 53,2 %. A omissão foi corrigida ao
  fechar este documento; um número que exclui em silêncio o módulo mais coberto lê como o todo e não é.
- A cobertura **não é porta de merge** (ADR-006). É medida e publicada; o que barra é o teste de
  arquitetura e a suíte.

**CI:** três estágios — `verify` (arquitetura + testes JVM + goldens, em todo PR), `android` (APK e
lint) e `ios` (macOS: testes no simulador, link do framework, `xcodebuild`), este só em push para
`main` ou por disparo manual, porque minuto de macOS custa 10× em repositório privado. Os três
estiveram verdes em `main` pela primeira vez em 22/09. **Em 23/09 o CI está bloqueado por cobrança da
conta do GitHub Actions**, não por código: `"The job was not started because recent account payments
have failed or your spending limit needs to be increased"`. A verificação desta entrega foi rodada
localmente, com a mesma lista de tarefas do workflow.

## 10. Como rodar

- Android: `./gradlew :androidApp:assembleDebug` e instalar; colar o token temporário na tela inicial.
- **iOS (macOS):** abra `iosApp/iosApp.xcodeproj`, escolha o scheme `iosApp` e um simulador, Run. O
  build phase chama `./gradlew :shared:app:embedAndSignAppleFrameworkForXcode`; no simulador não há
  assinatura, e o `Config.xcconfig` deve continuar **sem** `TEAM_ID`.
- **iOS sem Mac:** GitHub → *Actions* → workflow *CI* → *Run workflow*. O job `ios` roda em
  `macos-latest` (testes no simulador, framework e `xcodebuild`) e também a cada push em `main`.
- **Testes:**

  ```bash
  # regras de negócio e contrato — JVM, segundos
  ./gradlew :shared:domain:testAndroidHostTest :shared:data:testAndroidHostTest :shared:app:testAndroidHostTest

  # arquitetura (falha o build, não é aviso)
  ./gradlew :konture-test:test

  # interoperabilidade Java → Kotlin
  ./gradlew :legacy-catalog:test

  # capturas de tela: grava os goldens (pesado; o CI é o lugar dele)
  ./gradlew :shared:app:recordRoborazziAndroidHostTest

  # iOS (só macOS)
  ./gradlew :shared:domain:iosSimulatorArm64Test :shared:data:iosSimulatorArm64Test :shared:app:iosSimulatorArm64Test
  ```

  Antes de rodar qualquer um: copie `local.properties.example` para `local.properties` e preencha
  `smarthome.apiHost` e `smarthome.portalHost`. Sem eles o build falha com mensagem clara, em vez de
  embutir um host padrão — e **nenhum teste fala com a API real**; os de contrato usam `MockEngine`.
- Token: gerado na plataforma Open Casa Inteligente → Contas → Token Temporário (validade 2 h) — ver `docs/guides/token.md`.

## 11. O que ficou de fora e por quê

**O vídeo nunca mostrou um quadro.** É o item mais honesto desta lista e o único que não depende de
prazo. O app estava chamando `criar-fluxo-video` no host errado desde o commit 0 — corrigido, com
ADR-025 e teste de regressão. Com o host certo, a sessão é criada e o `/stream/<id>` do portal
devolve `200 video/mp4 chunked` e **encerra em exatos 15 s com zero byte**, nas duas câmeras, em todos
os canais e perfis, conectando 0,1 s depois de criar. É o transcodificador desistindo do upstream
*dele*. Que já funcionou é medido: dez sessões do mesmo dia consumiram 0,87 Mbit/s constante, e uma
sessão que não recebe nada registra `mb_consumed: 0.0`. O caminho carregou vídeo e parou. Se as 27
sessões que deixamos abertas contribuíram para esse estado, não sei — e é por isso que está escrito no
ADR-025 em vez de omitido.

**Números `[ASSUMED]` do vídeo.** Os atrasos de 1/3/7 s e o orçamento de 20 s para o primeiro quadro
estão implementados e provados em relógio virtual — o **mecanismo** é testado, os **números** não foram
observados contra hardware. Seguem marcados como tal no ADR-005 e na SPEC.

**Renovação de token (S10):** entregou. O endpoint foi sondado em 21/09 e o comportamento surpreendeu
de um jeito útil — renovar **acrescenta** uma credencial em vez de substituir, e o token anterior
continua valendo. Está no ADR-020.

**Baseline de goldens não commitada.** Ela só pode nascer no runner Linux (ADR-024) e o CI está
bloqueado por cobrança. Enquanto isso as capturas são artefato do PR, não porta de regressão.

**Fora do escopo do case, e registrado como próximo passo de produto** (detalhe no §5–6 de
`docs/research/user-feedback.md`): gravações, timeline e cartão SD — o maior cluster de reclamação
depois de vídeo e propaganda; notificações de dispositivo com granularidade, que **não** contradizem a
U7 (o que os usuários odeiam é push de marketing, o que pedem é alerta de segurança — são coisas
opostas); um app e uma conta para todas as linhas; pareamento resiliente a Wi-Fi + dados móveis.

**E um achado que não é backlog de produto:** um relato de 2026-03-06 descreve senha excluída na
fechadura continuando a abrir a porta, com o histórico sem identificar qual senha foi usada. Isso pede
tratamento de incidente, não card de feature, e está assim classificado na pesquisa.

## 12. Roteiro da apresentação (20 min)

O que se **diz**. O que precisa estar certo antes de falar — token, build, orçamento de requisições,
a chave de escrita da fechadura — está em [`docs/guides/demo.md`](guides/demo.md).

**3 min · o problema, lido antes de escrito.** Antes de abrir uma issue, li 270 avaliações do app
oficial da plataforma (`docs/research/user-feedback.md`). Os critérios de UX U1–U8 da SPEC nascem
daí, cada um com o teste que o prova — não de suposição sobre o que seria bom.

**5 min · demonstração.** Token → lista → câmera → fechadura → histórico. Quatro momentos que não
são detalhes de UI:

- um token truncado é recusado **sem gastar requisição** (a conta tem orçamento finito, ADR-006);
- a lista aparece **antes** da rede responder, do cache — dois toques até a imagem (U2);
- o vídeo tem etapas nomeadas e teto de 20 s: nunca um "97 %" eterno, que é a reclamação nº 1 dos
  usuários reais;
- a fechadura separa **comando** de **confirmação**, e o app nunca afirma que a porta abriu antes de
  a leitura concordar.

**7 min · arquitetura.** ADR-001 a 005: módulos com dependência só para dentro, erro como valor, um
estado imutável por tela, domínio agnóstico de parceiro. O que o compilador garante e o que o teste
de arquitetura garante — `:konture-test` falha o build, não emite aviso.

**3 min · o contrato, e o que ele forçou.** As oito contradições do §6.1 e onde cada uma é defendida.
Fecha na número 7 — **dois hosts** —, que é a que escapou: documentada desde o começo, e mesmo assim
o app chamou o host errado desde o commit 0, porque os dois respondem `200` e nada dentro do app
podia distingui-los. O custo não foi a imagem que faltava; foi o `session_id` que não vinha, que
deixou 27 sessões abertas numa conta compartilhada. ADR-025.

**2 min · método e uso de IA.** O `AI-LOG.md` tem os erros da IA, não os acertos: afrouxar um DTO
para calar um alarme que estava certo, escrever "a documentação está errada" a partir de uma medição
com um parâmetro não variado, implementar a correção descrita num ticket antes de verificar a
premissa dele. Os três foram pegos medindo, e a regra que saiu deles está escrita: **premissa em
ticket é hipótese até ser medida** — inclusive em ticket que a IA mesma escreveu.
