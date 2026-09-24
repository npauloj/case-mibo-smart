# Guia: como rodar, testar e ver previews

## 1. Ambiente

| Item | Versão / observação |
|---|---|
| JDK | 21 (o wrapper usa o toolchain resolver; um JDK 21 no `PATH` ou `JAVA_HOME` basta) |
| Android Studio | versão com suporte a AGP 9.1 e ao plugin `com.android.kotlin.multiplatform.library` |
| Android SDK | plataforma 36 e build-tools 36; `minSdk` 24 |
| Gradle | wrapper versionado — não instale Gradle; `org.gradle.configuration-cache=true` e `caching=true` já estão ativos |
| Xcode | só para iOS (macOS). O framework Kotlin é gerado pelo Gradle dentro do build do Xcode |

`local.properties` (criado pelo Android Studio, **não versionado**) aponta o SDK:
`sdk.dir=C:\\Android\\Sdk` (Windows) ou `sdk.dir=/Users/<você>/Library/Android/sdk` (macOS).

## 1.1 Configuração local (hosts da API)

O host da API e o host do portal onde o token é gerado **não estão no repositório**: são fornecidos no
e-mail do case e configurados localmente. Copie `local.properties.example` para `local.properties` (ou
acrescente ao que o Android Studio já criou) e preencha:

```properties
sdk.dir=C:\\Android\\Sdk
smarthome.apiHost=<API_HOST>          # host da API (sem esquema), ex.: api.exemplo.com.br
smarthome.portalHost=<PORTAL_HOST>    # host do portal/documentação, ex.: portal.exemplo.com.br
```

Os valores chegam ao app pelo `BuildConfig` do build Android (iOS: a confirmar na onda 1). O CI usa um
host fictício (`example.invalid`) — nenhum job fala com a API real; os testes de contrato usam o
`MockEngine` do Ktor. Se as chaves estiverem ausentes, o build falha com uma mensagem clara em vez de
embutir um host padrão.

## 2. Comandos

```bash
# Build Android (debug). APK em androidApp/build/outputs/apk/debug/androidApp-debug.apk
./gradlew :androidApp:assembleDebug

# Lint Android
./gradlew :androidApp:lintDebug

# Testes de regra de negócio — rodam na JVM ("host tests" dos módulos KMP), em segundos
./gradlew :shared:domain:testAndroidHostTest :shared:data:testAndroidHostTest :shared:app:testAndroidHostTest

# Testes de arquitetura (Konture) — disponível a partir da onda 0 (módulo :konture-test)
./gradlew :konture-test:test

# Testes no simulador iOS (só em macOS; o CI faz isso no job "ios")
./gradlew :shared:domain:iosSimulatorArm64Test :shared:data:iosSimulatorArm64Test :shared:app:iosSimulatorArm64Test

# Framework iOS (só em macOS)
./gradlew :shared:app:linkDebugFrameworkIosSimulatorArm64

# Compilar os alvos iOS a partir de Windows/Linux — obrigatório em qualquer fatia que mexa em
# expect/actual ou iosMain (ADR-013). Compila, não executa: prova que o actual existe e tipa.
./gradlew :shared:data:compileKotlinIosSimulatorArm64   -Pkotlin.native.enableKlibsCrossCompilation=true   -Pkotlin.native.ignoreDisabledTargets=false
```

No Windows, use `gradlew.bat` no lugar de `./gradlew` (ou `./gradlew` no Git Bash).

Primeiro build: 3–5 minutos a frio (download de dependências). Depois, dezenas de segundos.

## 3. Rodar o app

- **Android:** Android Studio → configuração `androidApp` → Run num emulador (API 24+) ou dispositivo.
  Na tela inicial, cole o token temporário ([como obter](token.md)).
- **iOS (macOS):** abra `iosApp/iosApp.xcodeproj`, selecione o scheme `iosApp` e um simulador, Run. O
  build phase chama `./gradlew :shared:app:embedAndSignAppleFrameworkForXcode`. Assinatura não é
  necessária no simulador. `Config.xcconfig` deve continuar sem `TEAM_ID`.
- **iOS sem Mac:** GitHub → *Actions* → workflow *CI* → *Run workflow* (job `ios` em `macos-latest`:
  testes no simulador, framework e `xcodebuild` do app). O job também roda a cada push em `main`.

## 4. Previews Compose no Android Studio

- Anotação: `androidx.compose.ui.tooling.preview.Preview` (unificada para código comum desde
  Compose Multiplatform 1.10; a `org.jetbrains.compose.ui.tooling.preview.Preview` está depreciada).
- Onde: ao lado de cada tela, em `commonMain` — `XScreenPreviews.kt` com um `PreviewParameterProvider`
  cobrindo todos os estados e `@PreviewLightDark`. As previews sempre apontam para o composable
  `XScreenContent(state, on…)` sem ViewModel.
- Dependências já configuradas: `org.jetbrains.compose.ui:ui-tooling-preview` em `commonMain` e
  `androidRuntimeClasspath("…:ui-tooling")` (forma exigida pelo plugin KMP do AGP 9 — não usa
  `debugImplementation`).
- Como ver: abra o arquivo de previews no Android Studio → painel *Split*/*Design* → *Build & Refresh*.
  As previews renderizam via Android (Layoutlib); não existe preview nativa iOS.
- Se uma preview não renderizar: confirme que o composable não recebe `ViewModel`, `NavController` nem
  faz rede/arquivo; use `LocalInspectionMode.current` para trocar player e imagens por placeholders.

## 4.1 Gerar o `PRODUCT.pdf`

O PDF de entrega é derivado de `docs/PRODUCT.md` — os dois têm de ser regerados juntos, senão o
PDF entrega uma versão anterior do documento sem ninguém perceber.

```bash
bash tools/product-pdf.sh
```

O script acha o Chrome sozinho (ou respeita `CHROME=<caminho>`), aplica
`tools/product-pdf.css` e escreve `docs/PRODUCT.pdf`. Três coisas nele não são óbvias:

- **Passa por HTML de propósito.** `pandoc -o x.pdf` exigiria um motor TeX, que não é dependência
  deste projeto e não está instalado. pandoc e Chrome são ferramenta de máquina, não do build —
  nenhuma dependência do Gradle muda por causa disto.
- **A primeira linha do `PRODUCT.md` é removida** antes da conversão: é o H1 do documento, e o bloco
  de título do pandoc o substitui. Sem isso o título aparece duas vezes na primeira página. O script
  falha se essa primeira linha deixar de ser um H1, em vez de cortar a linha errada em silêncio.
- **A folha de estilo é do repositório**, não improvisada a cada vez: paleta do próprio app
  (`AppTheme`), verde da marca nos títulos, âmbar do papel `waiting` nas citações, mono para dado.

**Confira o resultado abrindo o arquivo.** Não há verificação automática de aparência aqui, e o
número de páginas muda a cada edição do documento.
## 5. Estágios do CI

| Job | Quando | O quê |
|---|---|---|
| `verify` | toda atualização de PR e push em `main` | `:konture-test:test` + host tests dos três módulos |
| `android` | após `verify` | `assembleDebug`, `lintDebug`, APK como artefato |
| `ios` | push em `main` ou disparo manual | testes no simulador, framework, `xcodebuild` |

Relatórios de teste ficam como artefatos do run (`shared/*/build/reports/tests/`).

## 6. Troubleshooting

| Sintoma | Causa provável | O que fazer |
|---|---|---|
| `Unsupported class file major version` / erro de toolchain | JDK diferente de 21 | apontar `JAVA_HOME` para o JDK 21 ou deixar o toolchain resolver baixar |
| `SDK location not found` | `local.properties` ausente | abrir o projeto no Android Studio (ele cria) ou criar com `sdk.dir=` |
| Build falha pedindo `smarthome.apiHost` / `smarthome.portalHost` | hosts não configurados | preencher em `local.properties` a partir de `local.properties.example` (§1.1) — os valores estão no e-mail do case |
| Gradle "trava" ou build inconsistente | daemon antigo / cache de configuração | `./gradlew --stop` e repetir; em último caso `--no-configuration-cache` |
| Preview em branco no player | renderização em Layoutlib | esperado; o player usa `LocalInspectionMode` para mostrar placeholder |
| App responde "token inválido" logo após colar | token expirado (2 h) ou copiado incompleto | gerar novo token; conferir formato `Ot_` + 32 hex |
| Muitas requisições falhando | orçamento da conta de teste | ver `streaming/cota-disponivel`; usar `MockEngine` nos testes |
| Job `ios` falhando por runtime de simulador ausente | imagem `macos-latest` sem o runtime esperado | fixar a versão do Xcode/simulador no workflow (a confirmar quando acontecer) |
