# Guia: rodar a demonstração

Operacional, não documento de produto. O roteiro do que se **diz** está no §12 do
[`PRODUCT.md`](../PRODUCT.md); aqui está o que precisa estar certo **antes** de falar, e o que fazer
quando algo falhar ao vivo.

## 1. O orçamento é o risco real

A conta de teste tem um limite de requisições, e ele **não se renova**. Medido em 23/09/2026 às 17 h:
**84 restantes**. Cada chamada conta — inclusive a validação do token.

Custo de uma execução completa, derivado do código (não medido, para não gastar medindo):

| Passo | Requisições |
|---|---|
| Validar o token | 1 |
| Abrir a lista | 1 |
| Trocar os dois chips de filtro | 2 |
| Abrir uma câmera | 3 na primeira vez; 2 depois (`funcoes` fica em cache permanente por câmera) |
| Abrir uma fechadura | 3 (estado da porta, abertura remota, volume) |
| Comandar e verificar | 2 — **só se a escrita estiver ligada** |
| Histórico de aberturas | 1 |
| Renovar o token | 1 |
| **Total** | **~14** |

**~6 execuções completas cabem em 84, e uma delas é a ao vivo.** Não sobra para experimentar. Se
precisar conferir algo da API, use o `MockEngine` dos testes, não o aparelho.

Conferir o saldo custa **zero** requisições do app (o endpoint de cota é do portal e não entra no
mesmo balde): abra a tela de Conta, que mostra o contador em build de debug (ADR-006).

## 2. Antes de apresentar

- [ ] **Token novo.** Vale 2 h. Gere no portal pouco antes, não de manhã. A tela inicial agora tem o
      link e a navegação ("Contas → Token Temporário").
- [ ] **Build atual instalado.** `./gradlew :androidApp:assembleDebug` e
      `adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk`. O build precisa incluir
      o link do portal (PR #86) e a leitura parcial da fechadura (PR #81) — sem este, cinco das seis
      fechaduras da conta abrem a tela em erro.
- [ ] **`local.properties` com os dois hosts.** `smarthome.apiHost` **e** `smarthome.portalHost`. O
      segundo não é decorativo: sem ele o vídeo vai para o host que responde `200` e nunca transmite
      (ADR-025).
- [ ] **Nenhuma sessão de streaming aberta.** Elas consomem enquanto existem.
- [ ] **Aparelho no Wi-Fi certo**, brilho alto, não perturbe ligado, rotação travada em retrato.
- [ ] **Decidir a fechadura** — ver §3.

## 3. A decisão pendente: escrita na fechadura

`smarthome.lockWritesEnabled` tem padrão **`false`**, e o padrão é o oposto do vídeo de propósito:
`mudar-volume` e `habilitar-abrir-remoto` terminam numa porta física de uma conta que várias pessoas
usam. Desligado, o seletor de volume fica inerte e "Habilitar abertura remota" só explica o que faria.

As duas escolhas se defendem, e a escolha muda o roteiro:

- **Deixar desligada** — mostre a tela como está e diga por que o padrão é esse. É uma decisão de
  produto registrada, não uma limitação: *escrita que chega a hardware compartilhado é opt-in*. Os
  estados de comando aparecem pelas previews e pelos testes, não ao vivo. Risco zero.
- **Ligar** — `smarthome.lockWritesEnabled=true` no `local.properties`, **recompilar** (vai para o
  `BuildConfig`) e reinstalar. Aí o `CommandSent → CommandExpired` pode ser mostrado ao vivo, que é o
  estado mais forte do app. Custa 2 requisições por comando e mexe numa porta real.

Se ligar, **ensaie antes**: descobrir ao vivo que a fechadura não obedece é pior que não demonstrar.

## 4. Ordem da demonstração

1. **Token.** Cole um token truncado de propósito: o campo recusa **sem gastar requisição** (S1.2), e o
   contador `31/35` mostra por quê. Depois o token bom. É o primeiro ponto de arquitetura da
   apresentação disfarçado de detalhe de UI.
2. **Lista.** Ela aparece **antes** da rede responder, do cache (U2). Troque um chip de filtro. Repare
   na ordenação: câmeras e fechaduras online primeiro (U8), e "visto pela última vez há X" nos offline
   (U3).
3. **Câmera.** Dois toques desde a lista, sem tela intermediária. As etapas são nomeadas e há um teto
   de 20 s — nunca um "97 %" eterno, que é a reclamação nº 1 dos usuários reais do app oficial.
4. **Fechadura.** As três leituras saem juntas. Se o volume vier em branco com um aviso âmbar, **isso é
   o conserto do PR #81 funcionando**: o `volume` responde `500` em cinco das seis fechaduras da conta,
   e antes disso a tela inteira virava erro escondendo o estado da porta, que leu certo.
5. **Histórico.** Quem e quando, hora relativa e absoluta.
6. **Sessão expirada.** Se der tempo: a expiração explica o motivo e volta para onde o usuário estava.

## 5. Quando falhar ao vivo

- **Token recusado** → "Erro desconhecido" do parceiro traduzido; é a contradição nº 3 do contrato,
  tratada no caso de uso e não no client. Boa deixa para o §6.1 do `PRODUCT.md`.
- **Vídeo não abre** → não improvise. O diagnóstico inteiro está no ADR-025 e no §11 do `PRODUCT.md`:
  contrato medido, os dois hosts, as 27 sessões vazadas. É material mais forte que o vídeo em si.
- **Fechadura em `CommandExpired`** → é o estado funcionando, não um bug. O comando saiu e a porta não
  respondeu; o app não afirma que abriu. Ninguém consegue distinguir "recusou" de "não respondeu" daqui,
  e fingir que consegue seria o erro.
- **Orçamento acabou** → a API passa a recusar. Não há recuperação ao vivo; é por isso que o §1 existe.
