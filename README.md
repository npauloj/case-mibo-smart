# Mibo Smart — case técnico de casa inteligente (Kotlin Multiplatform)

Aplicativo Kotlin Multiplatform (Android + iOS) que consome a API pública **Open Casa Inteligente**:
entrada do token de acesso, listagem de dispositivos com paginação e filtro por origem, vídeo ao vivo
das câmeras e gerenciamento de fechadura (abrir/fechar, status, volume, histórico).

> Entregável de um case técnico para a vaga de Analista de Desenvolvimento de Produto II — Android.
> Este README é o ponto de entrada; os detalhes vivem em [`docs/`](docs/) (índice no fim da página).
> **Nenhum token ou credencial é versionado neste repositório** — ver [Como obter o token](#como-obter-o-token-de-acesso).

## Sobre

O app é a resposta a nove requisitos funcionais do enunciado (RF01–RF04 obrigatórios, RF05–RF09
desejados). O escopo é implementado em ondas (fatias verticais, uma Issue e um PR por fatia — ver
[`docs/PROCESS.md`](docs/PROCESS.md)):

| RF | O que pede | Fatias | PRs mesclados |
|---|---|---|---|
| RF01 | Tela inicial com campo para o token de acesso | S-01a · S-01b · S-01c · S-02a · S-02b · S-03 | [#26](../../pull/26) · [#37](../../pull/37) · [#29](../../pull/29) · [#46](../../pull/46) · [#50](../../pull/50) · [#56](../../pull/56) |
| RF02 | Após submissão, listar os dispositivos retornados pela API | D-01a · D-01b · D-02 · D-03 · D-04 · D-05 | [#38](../../pull/38) · [#47](../../pull/47) · [#51](../../pull/51) · [#62](../../pull/62) · [#66](../../pull/66) · [#70](../../pull/70) |
| RF03 | Acessar câmeras a partir da lista e ver o vídeo ao vivo | V-01a · V-01b · V-02 | [#43](../../pull/43) · [#52](../../pull/52) · [#58](../../pull/58) |
| RF04 | Erros amigáveis: token inválido, expirado, falha de rede, lista vazia | transversal — toda fatia mapeia suas falhas para um resultado selado (ADR-002, SPEC E2) | ver as demais linhas |
| RF05 | Fechadura: abrir, fechar e verificar status | L-01a · L-02, alcançável por D-03 · D-04 | [#39](../../pull/39) · [#59](../../pull/59) · [#62](../../pull/62) · [#66](../../pull/66) |
| RF06 | Fechadura: ver e mudar o volume | L-01b | [#53](../../pull/53) |
| RF07 | Filtrar por origem (vinculados, compartilhados, todos) | D-02 | [#51](../../pull/51) |
| RF08 | Paginação com `pagina` e `tamanhoPagina` | D-02 | [#51](../../pull/51) |
| RF09 | Histórico de abertura da fechadura | L-03 | [#60](../../pull/60) |
| ★ | Interoperabilidade Java (`:legacy-catalog`) | P-01 · D-05 | [#68](../../pull/68) · [#70](../../pull/70) |

Fora de escopo por decisão: lâmpadas, sensores, gravações de câmera, senhas de fechadura, criação de
conta na plataforma.

**Como ler a coluna de fatias.** O mapeamento RF → fatia vem dos rótulos `rf:*` das Issues, não da
memória de quem escreveu o README. O RF04 não tem linha própria de PR porque não é uma tela: é a regra
de que toda fatia converte falha de transporte e de API em resultado selado, com um ramo por categoria
(ADR-002) — quem o verifica é o compilador, em cada `when` sem `else`.

O PR [#64](../../pull/64) não aparece acima por não conter código: ele consolidou em `main` quatro
fatias que um merge de pilha mal-feito havia deixado numa branch lateral (ver ADR-019).

## Pré-requisitos

| Para | Precisa de |
|---|---|
| Build Android e testes | JDK 21 · Android Studio recente com suporte a AGP 9.1 · Android SDK 36 (`compileSdk`/`targetSdk`), `minSdk` 24 |
| Emulador/dispositivo | Android 7.0+ (API 24) |
| iOS | macOS com Xcode (o framework Kotlin é gerado pelo Gradle no build do Xcode). Sem Mac, o CI compila o app iOS por `workflow_dispatch` |
| Testes de arquitetura | nada além do JDK — rodam como testes JVM (módulo `:konture-test`, onda 0) |

O repositório usa o Gradle Wrapper (`./gradlew` / `gradlew.bat`); não instale Gradle à parte. O SDK
é localizado por `local.properties` (`sdk.dir=…`), que o Android Studio cria e que **não** é versionado.

## Como obter o token de acesso

O app não tem login próprio: ele usa um **token temporário** gerado pelo usuário na plataforma Open
Casa Inteligente e digitado na tela inicial (RF01). Passo a passo:

1. Entre no portal da plataforma (`https://<PORTAL_HOST>` — o endereço vem no e-mail do case e não é
   versionado) com a conta de gestão (GDI) fornecida para o case.
2. Menu **Contas → Adicionar Conta**: cadastre a conta do aplicativo Mibo (a conta que possui os
   dispositivos). É ela que os tokens representam.
3. Menu **Contas → Token Temporário**: gere um token com um rótulo que identifique você (ex.: "Case —
   seu nome") e copie o valor. O formato é `Ot_` seguido de 32 caracteres alfanuméricos.
4. Cole o token na tela inicial do app. Ele é guardado no cofre nativo do sistema (Android Keystore /
   iOS Keychain) e nunca em preferências simples, logs ou na interface (só o sufixo é exibido).

Regras que importam:

- **Validade máxima de 2 horas.** Depois disso a API devolve a mensagem genérica de erro
  (`"Erro desconhecido, por favor tente novamente mais tarde"`), que o app trata como token rejeitado
  (RF04). A plataforma oferece `renovarToken` para obter um novo token com duração estendida — o app
  planeja usar isso na onda 3 (critério S10 da [SPEC](docs/specs/SPEC.md)).
- **Nunca cole um token em Issue, PR, commit, print ou screenshot.** O CI falha se o padrão `Ot_…`
  aparecer em qualquer arquivo versionado. O `.gitignore` já ignora `*.token` e `secrets.properties`.
- A conta de teste tem um **orçamento de requisições** (~300 no momento da leitura do contrato, 20/09/2026). O app
  foi desenhado para economizar: cache da lista, sem polling, retry só em falha de rede
  ([ADR-006](docs/adr/ADR-006-local-persistence-and-request-budget.md)).

Guia completo, com o que acontece em cada estado (válido, expirando, expirado, rejeitado):
[`docs/guides/token.md`](docs/guides/token.md).

## Como rodar

```bash
# Android — APK de debug em androidApp/build/outputs/apk/debug/
./gradlew :androidApp:assembleDebug

# Testes das regras de negócio (JVM, segundos)
./gradlew :shared:domain:testAndroidHostTest :shared:data:testAndroidHostTest :shared:app:testAndroidHostTest

# Testes de arquitetura (a partir da onda 0)
./gradlew :konture-test:test

# Lint Android
./gradlew :androidApp:lintDebug
```

- **iOS (Mac):** abra `iosApp/iosApp.xcodeproj` no Xcode e rode o scheme `iosApp` num simulador; o build
  phase executa `./gradlew :shared:app:embedAndSignAppleFrameworkForXcode`. Sem Mac: dispare o job
  `ios` do CI manualmente (aba *Actions* → *CI* → *Run workflow*).
- **Host da API:** o host da API e o do portal são fornecidos no e-mail do case e configurados
  localmente em `local.properties` (`smarthome.apiHost`, `smarthome.portalHost`, modelo em
  `local.properties.example`) — nunca versionados; o CI usa um host fictício e os testes usam `MockEngine`.
  O host é configurável também porque o contrato cita dois hosts equivalentes
  ([ADR-004](docs/adr/ADR-004-partner-agnostic-domain.md), [contrato §1](docs/api-contract.md)).
  Detalhes em [`docs/guides/running.md`](docs/guides/running.md), "Configuração local".
- Comandos, previews no Android Studio e troubleshooting: [`docs/guides/running.md`](docs/guides/running.md).

## Como ler o contrato da API

A documentação da plataforma fica em `<PORTAL_HOST>/docs/` e o contrato formal (Swagger 2.0,
"Gerenciador de APIs Mibo") em `<PORTAL_HOST>/swagger/schema.json` — o host real não é versionado (ver
"Como rodar"). Três coisas que todo mundo precisa saber antes de tocar na camada de dados:

1. **O Swagger documenta só as requisições.** Com exceção de `criar-fluxo-video`, toda resposta é
   descrita apenas como "Operação completada". Os formatos reais foram descobertos com chamadas de
   leitura feitas com o token do case e estão registrados em [`docs/api-contract.md`](docs/api-contract.md).
2. **HTTP é sempre 200**, inclusive em erro, e existem **dois formatos de envelope**
   (`{statusCode, body:{status, data}}` na maioria dos endpoints; `{status, data|msg}` "plano" em
   streaming e em toda falha de autenticação). O leitor de envelope aceita os dois
   ([ADR-002](docs/adr/ADR-002-errors-as-values.md)).
3. **O contrato tem contradições** (exemplo Python da página usa `GET`, o Swagger e o cURL oficial usam
   `POST`; `volume/v1` exige `productId` mas o campo se chama `idProduto`; dois hosts) — todas isoladas
   em `:shared:data` para não vazarem para o domínio ([ADR-004](docs/adr/ADR-004-partner-agnostic-domain.md)).

Como o contrato foi lido, endpoint a endpoint, e como cada surpresa virou regra no código:
[`docs/guides/swagger.md`](docs/guides/swagger.md).

## Arquitetura

```
:shared:domain   modelos, contratos e erros — não depende de nada
:shared:data     implementação do parceiro: client Ktor, DTOs, mapeamento → domínio, persistência local, expect/actual
:shared:app      casos de uso, ViewModels e UI Compose Multiplatform por feature (gera o framework iOS "Shared")
:androidApp      app Android (Activity, player Media3, Keystore)
iosApp           app iOS (SwiftUI host, player, Keychain)
```

As dependências apontam sempre para dentro; o domínio não conhece Ktor, Koin, Compose nem SQLDelight,
e isso é verificado por testes de arquitetura ([ADR-009](docs/adr/ADR-009-architecture-tests.md)).
Trocar de parceiro ou de versão de API significa trocar `:shared:data`. Todas as decisões, com
alternativas consideradas e a regra que as verifica: [`docs/adr/`](docs/adr/README.md).

## Processo e rastreabilidade

Spec test-first → fatias verticais → uma Issue por fatia → um PR por Issue, com o "por quê" e a
evidência no corpo do PR → merge humano. Cada linha de código tem um caminho de volta:
`PR → Issue → critério na SPEC → ADR → AI-LOG`.

- [`docs/PROCESS.md`](docs/PROCESS.md) — fluxo, ondas, commits, issues, PRs, circuit breaker.
- [`docs/specs/SPEC.md`](docs/specs/SPEC.md) — critérios de aceite (EARS/Gherkin) com o teste que os prova.
- [`AI-LOG.md`](AI-LOG.md) — como a IA foi usada, o que errou e como foi corrigido.
- [`.github/ISSUE_TEMPLATE/slice.md`](.github/ISSUE_TEMPLATE/slice.md) e
  [`.github/PULL_REQUEST_TEMPLATE.md`](.github/PULL_REQUEST_TEMPLATE.md) — o contrato de cada Issue e PR.

## Documentos

| Arquivo | O que é |
|---|---|
| [`docs/PRODUCT.md`](docs/PRODUCT.md) | Documento de produto e arquitetura (base do PDF de entrega) |
| [`docs/api-contract.md`](docs/api-contract.md) | Contrato da API como observado: envelopes, endpoints, respostas reais, contradições |
| [`docs/specs/SPEC.md`](docs/specs/SPEC.md) | Especificação test-first por feature, mapeada a RF01–RF09 |
| [`docs/adr/`](docs/adr/README.md) | ADR-001..009 — decisões de arquitetura e o guardrail que as verifica |
| [`docs/PROCESS.md`](docs/PROCESS.md) | Processo de desenvolvimento e rastreabilidade |
| [`docs/guides/token.md`](docs/guides/token.md) | Token de acesso: obter, validade, armazenamento, expiração, renovação |
| [`docs/guides/swagger.md`](docs/guides/swagger.md) | Como o contrato foi lido e o que ele não conta |
| [`docs/guides/running.md`](docs/guides/running.md) | Build, testes, previews, iOS, troubleshooting |
| [`AI-LOG.md`](AI-LOG.md) | Registro do uso de IA |
