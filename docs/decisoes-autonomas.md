# Decisões tomadas sem consulta

Registro das decisões que eu (o agente) tomei enquanto o autor esteve ausente, sob a instrução
*"caso haja alguma definição, escolha a opção recomendada, consultando os especialistas pertinentes e
documente num documento à parte"* (2026-09-21, ~14h30 BRT).

**Por que este documento existe e não é o `AI-LOG.md`:** o AI-LOG registra onde a IA **errou** e quem
corrigiu. Este registra onde a IA **decidiu** sem ninguém para confirmar. São coisas diferentes, e
misturá-las tornaria os dois ilegíveis. Uma decisão aqui que depois se revele errada ganha *também* uma
linha no AI-LOG.

**Como ler:** cada entrada diz o que estava em aberto, quem foi consultado, o que foi decidido, e — o
mais importante — **o que teria que ser verdade para a decisão estar errada**. Nenhuma é irreversível;
todas podem ser revistas em uma sessão.

---

## D1 — O `completed` do orquestrador passa a significar "está na minha base", não "mesclado"

**Em aberto.** O ADR-019 fez as fatias restantes saírem umas das outras. Mas o `/orchestrate` manda,
literalmente: *"Never guess a slice is merged. A wrong `completed` entry releases a wave too early."*
Para despachar a V-02 eu preciso declarar a S-03 como `completed` — e ela não estará mesclada, porque
o merge é humano e o autor está ausente.

**Consultado.** Ninguém: é interpretação de regra do próprio processo, não questão técnica.

**Decisão.** Em modo empilhado, `completed` significa **"o trabalho desta fatia está presente na branch
base da próxima"**. Para a V-02, cuja base é `slice/s-03`, a S-03 está satisfeita por construção.

A regra original protege contra uma coisa específica: cortar uma branch de `main` quando a dependência
ainda não chegou em `main`. No stack esse risco não existe — a dependência está literalmente no commit
de onde a nova branch sai. O guarda muda de "mesclado" para "presente na base", e o `baseBranch` é o que
o torna verificável.

**Estaria errado se:** o autor mesclar o stack fora de ordem, ou descartar um PR do meio. Aí as fatias
acima dele carregam trabalho de um PR que não existe mais. Mitigação: o merge é **de baixo para cima**,
está escrito no ADR-019 e no corpo de cada PR, e rejeitar um PR do meio exige refazer os de cima — o que
já era verdade em qualquer stack.

---

## D2 — Trabalho de entrega fica para depois da cadeia, não em paralelo a ela

**Em aberto.** Sobram entregáveis da onda 3 que não são fatias: ligar o R8 no release, atualizar a tabela
RF → PR do README, gerar o PDF do `PRODUCT.md`, fechar os épicos. Nada disso depende do código das
fatias. Fazer em paralelo economizaria relógio.

**Consultado.** Ninguém: é disciplina de processo, e a evidência já está medida.

**Decisão.** Tudo isso entra **depois** da cadeia terminar, numa branch cortada de `main`.

O motivo é o último conflito desta sessão: o PR #54 saiu de `main` enquanto a #53 estava aberta, e
produziu exatamente o conflito de `AI-LOG.md` que ele existia para acabar. Foram **dez merges de `main`
em branch de fatia** nesta sessão, e o ADR-019 só funciona se eu seguir a disciplina também. Duas
branches vivas saindo de `main` recriam o problema.

**Estaria errado se:** o prazo apertasse. Não aperta — são ~14h30 de segunda contra quinta 16h, e a
cadeia toda cabe em ~4 horas.

---

## D3 — O R8 é ligado, e com regras mínimas verificadas pelo build

**Em aberto.** `androidApp/build.gradle.kts` não tem bloco `release { isMinifyEnabled }`. O CI roda
`assembleRelease`, então o APK é gerado — mas sem encolher e sem ofuscar. "R8 no release" é entregável
da onda 3 e sub-critério do eixo de conhecimento técnico.

**Consultado.** A skill `r8-analyzer` deste setup, cuja tese central é que a maioria das regras `-keep`
escritas à mão é redundante ou larga demais, e que as bibliotecas trazem as próprias `consumer rules`.

**Decisão.** Ligar `isMinifyEnabled` e `isShrinkResources` no `release`, **sem escrever regras
especulativas**. Kotlinx Serialization, Ktor, Koin, SQLDelight e Compose trazem consumer rules; o que
sobra é o que o build reclamar. Uma regra só entra com o motivo em comentário, e o critério de aceite é
`assembleRelease` verde no CI mais o tamanho do APK antes/depois no PR.

**Estaria errado se:** algo quebrar só em runtime no release — reflexão que o R8 não enxerga. É risco
real e não coberto por teste unitário; por isso o PR vai declarar que o APK de release **não foi
executado num aparelho**, e a verificação manual fica nomeada como passo do autor.

---

## D4 — Uma sessão sem `monitor_url` não oferece o fallback web

**Em aberto.** O ADR-005 (V-01b) decidiu que, no iOS, uma sessão com `monitor_url` nulo reporta
`DecodeError` imediatamente. O SPEC V5 manda `DecodeError` oferecer o fallback — que carrega
`monitor_url`. Nulo fecha o ciclo em si mesmo: o app ofereceria um botão que não tem para onde ir.
`StreamSession.monitorUrl` é `String?` e **nenhuma sondagem viu um valor real** — criar sessão gasta
cota de streaming, então a presença do campo nunca foi verificada.

**Consultado.** Ninguém; é leitura cruzada de ADR-005 + SPEC V5/V9/V10, toda ela no repositório.

**Decisão.** A ação "Abrir no player web" só aparece quando `monitorUrl != null`. Com nulo, o estado
`Failed` mostra apenas "Tentar novamente". Escrito no ticket da V-02 com critério EARS e teste nomeado
(`failedWithoutMonitorUrlOffersRetryOnly`).

No Android isso não é perda: o player nativo é o Media3 e pode funcionar sem a página do parceiro. No
iOS é perda real — o player **é** a WebView na `monitor_url` (V10), então nulo significa que não há o
que tocar nem para onde cair. A tela diz isso, em vez de esconder atrás de um botão morto.

**Estaria errado se:** a API sempre devolver `monitor_url` preenchido. Aí o ramo nulo é código que
nunca executa — custo de um `if` e um teste, e continua correto. O inverso (assumir que sempre vem)
custaria um botão quebrado na frente da banca.

---

## D5 — O teto de criação de sessão por visita é **três**, e está escrito

**Em aberto.** O SPEC V8 cita "the 2-creations-per-visit cap of V4", mas a V4 não enuncia teto nenhum:
ela descreve três tentativas, a primeira re-preparando a mesma URL. Somando, o número de chamadas
`criar-fluxo-video` por visita é 1 (abertura) + 2 (degraus 2 e 3) = 3 — mas isso exigia que cada leitor
fizesse a conta, e a conta interessa ao orçamento de ~300 requisições (ADR-006).

**Consultado.** Ninguém; é aritmética sobre o texto do próprio SPEC.

**Decisão.** O ticket da V-02 declara **no máximo três** `criar-fluxo-video` por visita à tela de
vídeo, e que o retorno ao primeiro plano (`ON_START`, V8) gasta dessa mesma cota em vez de zerá-la.
Vira critério EARS com teste que conta chamadas no repositório falso.

Não mudei o SPEC: a V4 e a V8 continuam corretas, só eram ambíguas juntas. O ticket é o lugar de
resolver, porque é ele que o agente lê como prompt.

**Estaria errado se:** o teto certo fosse por sessão de app e não por visita — aí uma câmera ruim
reaberta cinco vezes gastaria quinze criações. É risco aceito: o `LiveVideoSwitch` já é o corte de
emergência para gasto de streaming, e o uso é uma demonstração, não produção.

---

## D6 — O defeito de insets entra na branch de entrega, não numa fatia nova

**Em aberto.** Testando no aparelho (Galaxy A53, Android 16) apareceu um defeito real: o
`MainActivity` chama `enableEdgeToEdge()` e **nenhuma tela trata insets** — `safeDrawingPadding`,
`systemBarsPadding` e `WindowInsets` não aparecem em `shared/app` nem em `androidApp`. Em paisagem os
controles ficam sob a barra de navegação, em todas as telas. Não havia ticket para isso.

**Consultado.** Ninguém; o defeito foi observado diretamente e a ausência confirmada por busca.

**Decisão.** Vai para a branch de entrega (onda 3), junto com R8 e README, e **não** vira fatia nova.

Uma fatia nova alongaria a cadeia empilhada e, pior, tocaria a raiz Compose de todas as telas que a
V-02, a L-02 e a L-03 ainda estão reescrevendo — conflito garantido, exatamente o que o ADR-019
existe para evitar. É uma correção única na raiz; aplicada uma vez, com as telas já finais, custa
poucas linhas.

**Estaria errado se:** a banca rodar o app em paisagem antes de a entrega fechar. Risco aceito porque
a ordem é minha: a cadeia termina antes da entrega, e a entrega é anterior à apresentação. Se o prazo
apertar, esta correção sobe na fila — é barata e visível, melhor relação custo/impressão da lista.

---

## D7 — O bug do banner vira fatia própria (S-04), e fica no fim da pilha

**Em aberto.** O worker da S-03 encontrou um bug e, corretamente, **não** o corrigiu: o
`AppUiState.expiringSoon` é calculado uma vez no `onStart()` e nunca recalculado, então depois de
renovar o token o banner continua dizendo "Token expira em breve" sobre uma sessão de 2 h. Ele recusou
porque os arquivos estão fora do ticket dele e a superfície de roteamento é da S-02b — recusa certa,
e recomendou um ticket de acompanhamento. Restava decidir **onde**.

**Consultado.** O próprio relatório do worker (que já trazia o desenho da correção) e o código do
`AppViewModel` na branch `slice/s-03`, que confirma o diagnóstico: o `onStart()` recria o
`AppUiState` inteiro e reposiciona o `destination`.

**Decisão.** Fatia própria, **S-04**, Issue #57, última da pilha (base `slice/p-01`).

Fatia e não item de entrega — ao contrário do D6 — porque isto é comportamento testável, com
critérios EARS e um teste que existe só para impedir a correção errada
(`renewalDoesNotChangeDestination` falha se alguém "resolver" chamando `onStart()` de novo). Insetos
não têm esse teste; isto tem.

Última da pilha porque mexe no `App.kt`, que é exatamente o arquivo que V-02, L-02, L-03 e P-01 ainda
vão editar. Reescrevê-lo por baixo delas é a colisão que o ADR-019 existe para evitar.

**Estaria errado se:** a cadeia não chegar ao fim antes do prazo — aí o bug sobrevive até a
apresentação. Por isso está escrito no ticket que a S-04 **não entra na lista de corte**: cortar
restaura um bug visível na demonstração. Se o tempo apertar, ela sobe à frente da P-01, que é o
primeiro item cortável.

---

## D8 — O botão "Tentar novamente" reinicia a cota de sessões; meu critério é que estava errado

**Em aberto.** Eu escrevi no ticket da V-02 "no máximo três sessões por visita", liso. O worker
implementou o teto para as tentativas automáticas mas fez o toque em "Tentar novamente" reiniciar a
cota, documentou o desvio no ADR-005 e no corpo do PR, e cobriu com teste. Cabia a mim julgar se
aceitava ou mandava voltar.

**Consultado.** O próprio código já mesclado: o KDoc do `ListDevices`, escrito na D-01a, diz que *"the
only retry the app performs is the one the user asks for from the error state"*.

**Decisão.** Aceito, e **corrigi o ticket** em vez do código — que é o que o CLAUDE.md manda quando a
implementação diverge do ticket.

A regra da casa já estava escrita e mesclada desde a D-01a: o app orça as retentativas *dele*; o toque
humano não é uma delas. Meu critério absoluto teria transformado o "Tentar novamente" que a SPEC V4
exige num botão morto assim que a escada gastasse a cota — exatamente a ação morta que a SPEC V6
proíbe. Cada toque reinicia uma cota de 3, então o gasto continua limitado por toque, não ilimitado.

**Estaria errado se:** alguém martelar o botão. Dez toques = até 30 criações, contra um orçamento de
~300 requisições e 2 GB de streaming. É risco real, mas exige intenção humana repetida diante de uma
tela que já falhou quatro vezes — e o `LiveVideoSwitch` continua sendo o corte de emergência.

---

## D9 — `StreamState.Expired` ficou órfão; a limpeza vai para a entrega, com a SPEC junto

**Em aberto.** Com a escada da V-02, todo evento de falha do player passa a virar `Reconnecting` ou
`Failed`. O `StreamState.Expired` ficou **inalcançável** — estado, string e preview. O worker não o
removeu, e por um motivo certo: a SPEC §3 ainda lista "expired" entre os estados da tela, e o
CLAUDE.md proíbe "consertar no código" o que diverge da SPEC. Deixou um KDoc dizendo que está órfão.

**Consultado.** Ninguém; é a regra explícita do CLAUDE.md sobre divergência SPEC × código.

**Decisão.** A remoção entra na branch de entrega, **junto com a emenda da SPEC §3**, num único
commit.

Emendar a SPEC agora e remover o código quatro fatias depois deixaria os dois em desacordo durante
toda a cauda da pilha — pior que o estado atual, em que o desacordo está documentado no próprio
KDoc. E a `SPEC.md` é editada pelas fatias (a S-03 editou), então mexer nela fora da pilha é
justamente o conflito que o ADR-019 evita.

**Estaria errado se:** a entrega não acontecer. Aí sobra um estado morto num `sealed interface` —
custo cosmético, visível numa leitura de código pela banca, e já explicado no KDoc.

---

## D10 — As estimativas foram recalibradas pelo que foi medido, não pelo rótulo

**Em aberto.** Três fatias seguidas traíram a estimativa: a S-03 previa ≈120/≈100 e entregou 117/243;
a V-02 previa o mesmo e entregou 211/210; a L-02 previa ≈400/≈350 e entregou 298/186. Duas delas
carregavam o rótulo "narrow slice" — e a L-03 e a P-01 ainda carregavam o mesmo rótulo.

**Consultado.** O `tools/executable-lines.py` sobre as fatias já mescladas; nenhum especialista, porque
a evidência é aritmética.

**Decisão.** L-03 passou a **feature screen** (≈250/≈200) e P-01 a **narrow slice com módulo Gradle**
(≈150/≈120), cada uma com a base da calibração escrita no próprio ticket.

O rótulo era o problema, não o número: a L-03 constrói uma vertical inteira — modelo de domínio, DTO,
mapper, método de repositório, caso de uso, ViewModel, tela e previews — e estava marcada com o mesmo
rótulo de uma fatia que só acrescenta um botão. Pelo ADR-017 a estimativa não é ordem de parada, então
o efeito prático é modesto; o que ela evita é o worker devolver `blocked` ou truncar escopo por achar
que estourou.

**Estaria errado se:** eu estivesse calibrando pelo ruído. Três pontos são poucos, e a L-02 errou para
*baixo* enquanto as outras erraram para cima. Por isso só mexi no rótulo das duas fatias cujo escopo eu
conferi arquivo a arquivo, e deixei a S-04 (~50/≈80) como estava.

---

## D11 — A fechadura estava inalcançável; virou a fatia D-03, à frente da P-01

**Em aberto.** O worker da L-03 anotou de passagem que o `App.kt` nunca define o destino `lock`.
Conferi: `DeviceListScreen` recebe `onOpenLiveVideo` e **não tem equivalente para fechadura**, e o
`LockAddress` só é construído em *fixtures de teste* — nenhum código de produção jamais montou um.
Ou seja: L-01a, L-01b, L-02 e L-03 estão implementadas, testadas e **invisíveis**. RF05, RF06, RF07 e
RF08 não podem ser demonstrados. O KDoc do próprio `App.kt` diz que a aresta "pertence à fatia da
fechadura", mas as quatro fatias de fechadura excluíram `App.kt` e a lista do escopo. Caiu no vão
entre tickets — falha minha no fatiamento, não dos workers.

**Consultado.** O código, em quatro pontos: `App.kt`, `DeviceListScreen`, `LockAddress` e o DTO da
lista. O `idProduto` **já chega** no DTO; é o mapper que o descarta, porque o `Device` do domínio não
tem onde guardá-lo.

**Decisão.** Fatia nova **D-03** (Issue #61), despachada **antes** da P-01, com a migração de esquema
no mesmo ticket.

À frente da P-01 por prioridade de risco: a P-01 é o entregável ★ Java e é explicitamente o
**primeiro item da lista de corte**; quatro fatias de fechadura inalcançáveis são um furo de produto.
Se algo tiver de ser cortado, que seja a P-01, não a porta que não abre.

Duas coisas que o ticket decide em vez de deixar o agente adivinhar: (a) o endereço só é montado a
partir de linhas **já carregadas** — se o hub estiver em outra página, a linha da fechadura fica
não-tocável e diz o motivo, porque adivinhar um `ns` endereçaria **outro dispositivo**; (b) a migração
(`2.sqm` + bump do `SchemaVersion`) vai junto, porque o contrato de ticket proíbe esquema sem migração
e um `idProduto` em branco é a mesma classe de bug que um `ns` errado.

**Estaria errado se:** o hub sempre vier na mesma página da fechadura. Aí o ramo "sem hub" é código
que nunca executa — custo de um teste. O inverso seria uma fatia que abre a tela e não consegue
endereçar nada.

---

## E1 — **Erro meu**, pego pelo gate: procurei num source set só

Esta entrada não é uma decisão — é um erro, e está aqui para não se perder. **Deve ser transcrita
para o `AI-LOG.md` na consolidação da entrega**, que é onde o CLAUDE.md manda erros de IA morarem; está
neste documento por enquanto porque o `AI-LOG.md` é editado por toda fatia e a pilha está aberta.

**O que eu fiz.** Ao escrever o ticket da D-03 declarei
`shared/data/src/commonTest/.../DeviceCacheTest.kt` como arquivo **novo**, afirmando que "o cache só
tem um `FakeDeviceCache.kt` hoje". Listei `shared/data/src/commonTest` e não achei nada — e parei aí.

**O que era verdade.** `DeviceCacheTest.kt` existe em
`shared/data/src/androidHostTest/`, no mesmo pacote, já com `roundTripsPage`,
`readsNullBeforeAnythingIsWritten` e `schemaVersionMismatchDropsAndRefetches`, sobre um
`JdbcSqliteDriver` em memória. Está lá por um motivo: `commonTest` **não tem driver SQLite nenhum**, e
`:shared:data:testAndroidHostTest` é a tarefa que o CLAUDE.md manda rodar. Seguir meu ticket teria
redeclarado a classe.

**Quem pegou.** O gate do `ticket-contract`, antes de despachar — custo: um ciclo, zero código errado.

**O que dói.** É exatamente o defeito que eu vinha catando nos tickets das outras fatias na mesma
sessão (`LiveVideoScreenContent.kt`, `LockScreenContent.kt`, `OpeningHistoryContent.kt`, o critério
inexercível da P-01). Cometi a mesma classe de erro enquanto a corrigia nos outros.

**O que muda daqui pra frente.** Verificar caminho de teste varrendo **todos** os source sets do
módulo (`commonTest`, `androidHostTest`, `iosTest`), nunca um só. O comando que eu deveria ter rodado
é `git ls-tree -r --name-only <ref> -- shared/<mod>/src | grep -i test`, e foi o que usei depois.

O gate também apontou um buraco real que eu não tinha visto: o "Technical detail" prometia que um
`productId` em branco nunca chega ao `LockAddress`, e nenhum critério testava isso. Virou critério
EARS com teste nomeado.

---

## E2 — **Erro meu**, pego pelo worker: inventei uma restrição que a API não impõe

Também não é decisão — é erro, e também **deve ir para o `AI-LOG.md`** na consolidação.

**O que eu fiz.** No ticket da D-03 escrevi a regra "o endereço só é montado a partir de linhas já
carregadas; sem a linha do hub, a fechadura fica não-tocável". Justifiquei bem — adivinhar um `ns`
endereçaria outro dispositivo — mas a premissa era falsa: presumi que o `idProduto` do hub só existia
na linha do hub.

**O que era verdade.** O `docs/api-contract.md` §5, escrito a partir da sondagem de 2026-09-21 que eu
mesmo conduzi, registra que a linha do subdispositivo carrega `dispositivoPai` **e**
`idProdutoDispositivoPai`. As quatro partes do `LockAddress` sempre estiveram na própria linha da
fechadura. O `DeviceDto` declara o primeiro campo e **descarta o segundo em silêncio**.

**Quem pegou.** O worker da D-03, numa observação lateral que ele marcou como "não corrigido, fora do
escopo": a fixture mandava `idProdutoDispositivoPai` e nenhum DTO lia. Ele estava certo em não
corrigir — o ticket mandava outra coisa, e o CLAUDE.md proíbe divergir do ticket no código.

**O custo.** Uma fechadura que funciona ficaria **não-tocável** sempre que o hub caísse em outra
página, por uma restrição que a API nunca impôs. Não morde a conta de teste (17 dispositivos numa
página de 20, hub incluso) — o que é exatamente o que torna o defeito perigoso: passaria despercebido.

**A correção.** Fatia **D-04** (Issue #63), que é uma **deleção líquida**: some o estado
`HubNotLoaded`, a busca pelo hub, a string nos dois idiomas e a preview. Fica uma função de um
`Device` só.

**O padrão que os dois erros formam.** E1 e E2 são o mesmo vício: **afirmei sobre o repositório sem
varrer o repositório**. Num caso olhei um source set de três; no outro, presumi um campo em vez de
reler o contrato que eu próprio tinha medido. Quando um ticket afirma o que existe ou o que a API
manda, a afirmação tem de vir de um comando executado, não de memória.

---

## E3 — A pilha não chegou em `main`, e o ADR-019 não dizia como mesclá-la

Erro de **processo**, não de código. Vai para o `AI-LOG.md` na consolidação, e o **ADR-019 precisa ser
emendado** na branch de entrega.

**O que aconteceu.** Os cinco PRs foram mesclados de baixo para cima, como o ADR-019 manda. Mas cada um
tinha base explícita na branch anterior (`slice/v-02` → `slice/s-03`, etc.), e eles **não** foram
registrados pela API de stacks do GitHub — diferente da onda 0, que usou `POST /repos/{o}/{r}/stacks`
e onde o GitHub reaponta sozinho. Sem esse registro, mesclar o #58 levou a V-02 para dentro da
`slice/s-03`, que já tinha ido para `main` minutos antes. As quatro fatias de cima ficaram numa branch
lateral e o `main` ficou só com a S-03.

**Como foi detectado.** Conferi `origin/main` após o aviso de merge e o `git log` mostrava apenas o
#56. A verificação que resolveu foi procurar um arquivo-assinatura de cada fatia em cada branch.

**Nada se perdeu.** `slice/d-03`, `slice/l-02` e `slice/l-03` são idênticas em conteúdo (`git diff`
vazio entre as três) e contêm as cinco fatias. Consolidado no PR #64, `slice/d-03` → `main`.

**A lacuna no ADR-019.** Ele diz "mesclar de baixo para cima" e não diz o que fazer com a base do PR
seguinte. Duas formas corretas, e a emenda precisa escolher uma: (a) registrar a pilha pela API de
stacks, que reaponta sozinha; ou (b) após mesclar o de baixo, **reapontar o próximo PR para `main`**
(`gh pr edit <n> --base main`) antes de mesclá-lo. A (b) é a mais simples e não depende de API em
preview — recomendo essa.

**Efeito prático daqui pra frente.** D-04, P-01 e S-04 abrem PR **contra `main`**: assim que o #64
entrar, `main` contém tudo de que elas dependem, e a pilha deixa de existir.

---

## D12 / E4 — O catálogo Java ficou invisível na tela principal; é a terceira vez que um ticket meu promete sem testar

Metade decisão, metade erro — e o erro é de um padrão que já se repetiu.

**O que aconteceu.** A P-01 entregou o módulo Java, o bridge, a regra de guarda do Konture e os
rótulos do histórico da fechadura. Não ligou os **nomes de modelo na lista de dispositivos**. O worker
explicou por quê, e estava certo: a linha "expected behaviour" do ticket prometia isso, mas **nenhum
critério de aceite cobria** e **nenhum arquivo de `devices` estava na lista de arquivos**. Sair do
ticket teria sido a divergência que o CLAUDE.md proíbe.

**Por que importa.** Conferi: `DeviceListScreen` renderiza `"${kind.label} · ${row.model}"`, então
cada linha mostra um código cru como `IOT-MFR1001-IB`. E o catálogo já sabe traduzir código de modelo
— `ModelCatalogAdapterTest.namesModelCodesFromTheSameTable` está verde. Ou seja: o entregável ★ Java
funciona, está testado, e está invisível **na primeira tela depois do login**, que é onde a banca
mais olha.

**Decisão.** Fatia **D-05** (Issue #69), pequena, com os critérios EARS que faltavam — inclusive o
fallback para código cru quando a exceção verificada do Java é lançada, regra que a própria P-01 já
estabeleceu. Fatia e não item de entrega porque é comportamento testável.

**O erro, nomeado.** É a **terceira** vez que escrevo um ticket afirmando algo que não verifiquei:
E1 (caminho de teste em um source set de três), E2 (premissa sobre um campo da API que o contrato
medido contradizia), e agora E4 — uma "expected behaviour" prometendo comportamento que **nenhum
critério media**. Os três têm a mesma forma: a seção em prosa do ticket afirma mais do que a seção
executável garante.

**A regra que tiro disso:** toda frase da "expected behaviour" precisa ter um critério EARS
correspondente, ou sair da frase. Prosa que nenhum teste cobre não é requisito — é intenção, e o
agente, corretamente, não implementa intenção.

**Estaria errado se:** o código de modelo não aparecesse na tela. Conferi a linha antes de escrever a
fatia, em vez de presumir — que é exatamente o que faltou nas outras duas vezes.

---
