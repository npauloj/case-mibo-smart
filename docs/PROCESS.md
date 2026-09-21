# Processo: specs → slices → issues → PRs

Como este repositório é construído, em ordem, e o que cada artefato promete. Escrito para quem
avalia o case ler junto com os PRs: cada decisão de processo tem um "por quê" e um gate.

## 1. Fluxo em uma linha

`docs/specs/SPEC.md` (critérios EARS/Gherkin com nome de teste) → `/to-issues --publish` (fatias
verticais em ondas, uma Issue por fatia) → gate do ticket-contract → uma branch e um PR por fatia,
CI em três estágios → revisão humana + `/review` nas fatias com regra de negócio → **merge humano**
(nunca automático) → próxima onda.

**Nível de SDD:** *spec-first com a spec ancorada*. A SPEC é escrita antes do código e continua valendo
depois: quando a implementação diverge dela, **a SPEC (ou o ticket) muda primeiro, com um ADR, e o código
depois** — nunca "corrige no código e segue". Não é *spec-as-source*: humanos editam código.

**Para quem é cada artefato:** SPEC, `docs/specs/issues.md` e as Issues são escritos para o agente (e para
o gate). O **corpo do PR é escrito para pessoas** — em português, para a banca.

Ferramentas do pipeline: `/to-issues`, `wave-orchestrate` (modo `pr`), `/review`, `/adr`. Elas rodam a
partir do setup local do autor em `.claude/`, que **não é versionado neste repositório** — é ferramental
de máquina, não entregável do case. O que é versionado, e portanto auditável, é este `PROCESS.md` mais
tudo que as ferramentas produzem: a SPEC, `docs/specs/issues.md`, as Issues, os ADRs, os commits e os
PRs. O orquestrador lê `docs/specs/issues.md` (fonte canônica, passado como `issuesPath`), recalcula o
grafo de dependências, rejeita tickets fora do contrato e **para no gate de merge**.

## 2. Fatias (slices) e ondas

Uma fatia é **vertical** — atravessa `domain → data → app → tela` e entrega algo observável — e cabe
em um PR revisável: **≤ ~400 linhas executáveis** ([ADR-011](adr/ADR-011-pr-budget-measured-in-executable-lines.md)).
Executável = linhas adicionadas menos KDoc/comentários, `import`/`package`, linhas em branco, arquivos de
recurso (`strings.xml` pt/en), `.sq`, build/catálogo e corpos de `@Preview`/`PreviewParameterProvider`.
**Testes contam** — não se compra espaço entregando menos teste. O diff bruto costuma ser 2–3× esse
número num app KMP de quatro módulos; a linha `Size:` de cada ticket declara os dois e o limiar em que o
worker deve **parar e devolver `blocked`**. Fatias da mesma onda são independentes (`[P]`) e podem
ser implementadas em paralelo, cada uma em seu worktree e **em uma sessão de agente própria**; uma onda
só começa quando as dependências da anterior foram mescladas em `main`.

A onda 0 é a exceção deliberada: um *walking skeleton* (Cockburn) — infraestrutura que atravessa as
camadas sem entregar feature — para que arquitetura e funcionalidade evoluam juntas a partir daí. Não
existe "fatia de fundação" depois disso: quem precisa do client HTTP, do leitor de envelope, dos erros
tipados e do cofre do token é a primeira fatia funcional (sessão/token), e é ela que os constrói.

| Onda | Quando | Fatias | Requisitos |
|---|---|---|---|
| 0 | dom 20/09 | walking skeleton: esqueleto KMP, CI, docs (SPEC, ADRs, contrato da API), `:konture-test` com 5 regras literais + teste negativo | — |
| 1 | seg 21/09 manhã | **sessão/token** (tela, validação, expiração) — constrói client Ktor, envelope, erros tipados e cofre Keystore/Keychain por necessidade própria | RF01, RF04, segurança |
| 2 | seg 21/09 tarde → ter 22/09 | `[P]` lista de dispositivos + filtro + paginação + estados · `[P]` vídeo ao vivo (sessão de streaming, player nativo, fallback WebView) · `[P]` fechadura: status, abrir/fechar com máquina de estado, pré-condição remota, volume | RF02, RF03, RF04, RF05, RF06, RF07, RF08 |
| 3 | qua 23/09 | histórico da fechadura · renovação de token/conta · iOS compilando com a lista · módulo Java · R8 no release · `docs/PRODUCT.md` → PDF · `AI-LOG.md` final · tabela RF → PR no README | RF09, ★ Java, entrega |

**Circuit breaker (Shape Up: o tempo é fixo, o escopo varia).** Must-have para a apresentação: ondas 0–2
inteiras. Se ao fim de terça a onda 2 não estiver mesclada, a onda 3 encolhe nesta ordem, sem discussão:
módulo Java → histórico da fechadura → fallback WebView do vídeo → R8. Vídeo e fechadura não são cortáveis.

**Dois caminhos.** *Fatia* (SDD completo: ticket, gate, `/review`, evidência) para tudo que entrega
requisito. *Chore/bug* (Issue curta, PR curto, triage + leitura humana, sem `/review` adversarial) para
CI, docs, dependências, ajustes de UI sem regra de negócio.

**Definition of ready:** os campos do template `.github/ISSUE_TEMPLATE/slice.md` preenchidos — é a lista
que o gate verifica. Sem isso, a fatia volta antes de ser despachada; o label `agent-ready` é aplicado
**pelo gate**, não na criação.

**Definition of done (por fatia):** critérios da SPEC atendidos e citados no PR; testes das regras de
negócio rodando em `testAndroidHostTest` com saída real; estados de tela existem e têm preview
(a evidência visual deste case; screenshot tests automatizados são onda 3 se sobrar tempo);
`:konture-test` verde; CI verde; desvio de ADR registrado como novo ADR; **se** a IA errou ou tentou
enfraquecer uma verificação, a entrada correspondente no `AI-LOG.md`.

## 3. Branches

- `main` — sempre verde, sempre demonstrável. Nada é commitado direto nela depois do commit 0.
- `slice/<id>` — uma por fatia, criada de `origin/main` atualizado (convenção do orquestrador).
  **Exceção, e só ela** ([ADR-015](adr/ADR-015-stack-slices-that-share-a-ui-surface.md)): fatias que
  editam **a mesma tela ou a mesma superfície de plataforma** empilham — `L-01b` de `main`, `L-02` de
  `slice/l-01b`, `L-03` de `slice/l-02` — porque despachá-las em paralelo garante conflito nos mesmos
  dois arquivos. A aresta empilhada entra no `Depends on:` do ticket dizendo que é acoplamento de
  superfície, não dependência lógica. Fatias de features diferentes **nunca** empilham: continuam
  saindo de `main` e mescláveis em qualquer ordem. O preço está no ADR — dentro de um stack a revisão
  serializa, e o verde da CI é contra o stack, não contra `main`.
- `chore/<tema>` / `docs/<tema>` — trabalho fora de fatia.
- Sem `develop`: GitHub Flow. Branch apagada após o merge.

## 4. Commits

[Conventional Commits](https://www.conventionalcommits.org/), em inglês (linguagem do código):

```
<type>(<scope>): <subject in imperative, ≤ 72 chars>

<why, not what — 1–3 lines when the subject is not enough>

Assisted-by: Claude <model>     ← atribuição de IA, mantida de propósito (transparência)
```

- **type**: `feat` `fix` `test` `refactor` `docs` `chore` `ci` `build` `arch` (regras/testes de arquitetura).
- **scope**: `domain` `data` `app` `session` `devices` `camera` `lock` `android` `ios` `ci` `docs` `adr` `spec`.
- Um commit = uma intenção. Testes vão no mesmo commit da regra que provam.
- **Estrutura ≠ comportamento** (Kent Beck, *Tidy First?*): refatorar código existente é um commit (ou um PR)
  separado do commit que adiciona comportamento. Dentro de uma fatia, a ordem é a do *keystone*
  (Fowler): contrato/domínio/dados com testes → ViewModel → tela por último.
- `Assisted-by:` em vez de `Co-Authored-By:` — coautoria implica responsabilidade compartilhada, que um
  modelo não assume; a atribuição continua, com a semântica certa.
- Nunca `--no-verify`; nunca amend de commit já publicado; nunca token/credencial (o CI faz grep de `Ot_`).
- Autor: identidade pessoal configurada localmente neste repositório, não a global.
- O histórico fino da branch some no squash (é a troca padrão); o que fica em `main` é o título do PR.

Exemplos:

```
feat(lock): confirm open/close command against device status (L3–L6)
fix(data): treat "Erro desconhecido" on a valid request as a rejected token (E3)
arch(konture): forbid feature packages from importing each other (rule 3)
refactor(devices): extract page cursor before adding origin filter
```

## 5. Issues

Uma Issue por fatia, gerada de `docs/specs/issues.md` (ou escrita à mão no mesmo formato):

- **Título:** `<ID>: <título imperativo>` — ex.: `L-02: Open and close the lock with a confirmation state machine`.
- **Corpo:** o template do ticket-contract (`.github/ISSUE_TEMPLATE/slice.md`). Referencia os critérios da
  SPEC por ID em vez de copiá-los; a SPEC continua a fonte da verdade. Chores usam o mesmo template
  com os campos que fizerem sentido.
- **Épicos (estilo Jira):** uma Issue `type:epic` por feature — Sessão e token · Dispositivos · Vídeo ao
  vivo · Fechadura · Fundação e entrega — com task list das fatias e sub-issues ligadas. É onde a banca
  vê progresso por requisito.
- **Labels:** `type:epic|slice|chore` · `rf:RF0x` (requisito do enunciado) · `area:session|devices|camera|lock|platform|docs` ·
  `agent-ready` (aplicado pelo gate).
- **Milestone:** `Wave N` — progresso por onda sem abrir issue por issue. **Project** "Case Mibo Smart":
  colunas Backlog → Ready → In progress → In PR → Done e visualização Roadmap por onda.
- Issue fechada só por PR (`Closes #n`), nunca à mão. Mudança de escopo ganha comentário com o "por quê"
  e, se estrutural, um ADR.

## 6. Pull requests

Um PR por fatia, ≤ ~400 linhas **executáveis** (ADR-011; o diff bruto que o GitHub mostra é maior e não
é o gate). O corpo é o roteiro da apresentação — escrito em **português**, para a banca; **o worker
escreve o corpo no template**, o humano edita, e a seção Evidência declara **as duas contagens**.

- **Título:** o mesmo formato do commit de squash: `feat(lock): open/close with confirmation state machine (L3–L6)`.
- **Corpo:** `.github/PULL_REQUEST_TEMPLATE.md`, cinco seções — contexto e por quê; o que muda (com o que
  ficou de fora); critérios de aceite marcados com o teste que os prova; evidência (saída real dos testes,
  previews, screenshot, tamanho do APK quando relevante); uso de IA (link para a entrada do `AI-LOG.md`
  ou "nenhuma"). Decisões de arquitetura só quando o PR cria ou desvia de um ADR. `Closes #n`.
- **Checklist do autor:** Konture verde · saída real dos testes colada · nenhum token no diff.
- **Revisão:** auto-revisão de todo diff de teste (o único controle que o agente não burla) + `/review`
  adversarial **nas fatias com regra de negócio** (token/expiração, paginação/filtro, máquina de estado da
  fechadura, retry do vídeo); chores: triage + leitura humana.
- **Merge:** *squash and merge* pelo humano, mensagem = título do PR. `main` fica linear: um commit por fatia.
- Um PR nunca toca outro PR ou branch; nunca é mesclado por agente.

## 7. Rastreabilidade (o "log do SDD")

Cada linha de código tem um caminho de volta: `PR → Issue → critério na SPEC → ADR → AI-LOG`.

- SPEC diz **o quê** (EARS, teste nomeado); ADR diz **por quê** (com a regra de arquitetura que o verifica);
  Issue diz **quando e em que ordem**; PR diz **como e com que evidência**; AI-LOG diz **onde a IA errou e
  quem corrigiu**.
- **ADR é a memória do agente entre sessões:** o que foi resolvido por tentativa e erro com IA vai para
  um ADR curto, referenciado no código — não para comentários explicando o histórico.
- Atribuição de IA fica nos commits (`Assisted-by:`) e na seção "Uso de IA" do PR — não escondida.
- Mudança de regra de arquitetura = mudança de ADR no mesmo PR; o teste que a expressa muda junto.

## 8. Por que tanto processo para três dias

Porque o trabalho é feito por um humano orquestrando agentes em paralelo, e o risco dominante não é
tempo — é verificação. Lotes pequenos e `main` sempre verde são a defesa comprovada (DORA 2025: IA
amplifica o que já existe e tende a aumentar o tamanho do lote); merge humano e revisor adversarial
respondem ao *reward hacking* documentado em agentes de código (SpecBench 2026); ticket-contract e
gate são protocolo de despacho de máquina, não ritual de gestão; e a rastraceabilidade existe porque o
enunciado pede um log de uso de IA auditável — é o entregável, não overhead.
