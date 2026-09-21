# Guia: o token de acesso

Tudo que o app faz depende de um token temporário emitido pela plataforma Open Casa Inteligente. Este
guia cobre o ciclo inteiro: obter, digitar, guardar, expirar, renovar — e o que o app faz em cada caso.
As referências entre parênteses apontam para os critérios da [SPEC](../specs/SPEC.md) e para os ADRs.

## 1. Onde gerar

1. Acesse o portal da plataforma (`https://<PORTAL_HOST>` — endereço fornecido no e-mail do case, nunca
   versionado) com a conta de gestão (GDI) fornecida para o case.
2. **Contas → Adicionar Conta**: vincule a conta do aplicativo Mibo (a que possui câmeras e fechaduras).
   Os tokens são emitidos em nome dessa conta e enxergam os dispositivos dela.
3. **Contas → Token Temporário**: gere um token com um rótulo que identifique você e copie o valor.

A página de documentação da plataforma (`<PORTAL_HOST>/docs/`) tem um seletor de tokens da conta GDI — ele lista
tokens de outras pessoas que usam a mesma conta de gestão. Use apenas o seu.

## 2. Formato e validade

- Formato: `Ot_` + 32 caracteres hexadecimais — 35 no total. O app valida esse formato **localmente**,
  antes de chamar a API, e só habilita "Validar" quando ele bate (S1.2). Um token truncado na colagem
  falha no campo, sem custar requisição.
- No campo, o token aparece mascarado com o prefixo `Ot_` e os **4 últimos** caracteres em claro, mais
  um contador de caracteres (S1.1) — o suficiente para conferir uma colagem sem expor o segredo. Não
  existe controle de "revelar" em lugar nenhum do app (S9).
- Cabeçalho: `Authorization: Bearer Ot_…` em toda requisição (contrato §1).
- **Validade máxima: 2 horas**, contadas pela plataforma. O app estima a expiração a partir da primeira
  validação bem-sucedida (S7, marcado como assunção na SPEC) e avisa antes: "Token expira em breve".
- Não há endpoint para consultar a validade restante; a única forma de saber que expirou é a API rejeitar.

## 3. Como o app armazena

- Só no cofre nativo: **Android Keystore** (chave AES no Keystore, valor cifrado em `SharedPreferences`
  privado) e **iOS Keychain** (`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`) — [ADR-008](../adr/ADR-008-token-security.md).
- Nunca em preferências simples, banco local, logs ou na UI: a tela de conta mostra apenas os **4
  últimos caracteres** (S9).
- O logger HTTP sanitiza o cabeçalho `Authorization` (`sanitizeHeader` do Ktor) e roda em
  `LogLevel.HEADERS`, nunca `ALL`, para não registrar corpos de resposta.
- O CI falha se o padrão `Ot_[0-9a-f]{20,}` aparecer em qualquer arquivo versionado.

## 4. O que acontece em cada estado

| Estado | Como o app descobre | O que o usuário vê | Critérios |
|---|---|---|---|
| Sem token | cofre vazio | tela inicial com o campo (RF01) | S1 |
| Formato inválido | validação local | erro no campo, sem chamada à API | S2 |
| Token rejeitado (inválido **ou** expirado) | a API responde HTTP 200 com `{"status":"erro","msg":"Erro desconhecido, por favor tente novamente mais tarde"}` | mensagem específica de token inválido/expirado e volta para a tela de token; o token rejeitado é apagado do cofre | S3, S6, E3 |
| Token ausente na requisição | `{"status":"erro","msg":"Token não está presente na requisição"}` | tratado como bug interno — nunca deve acontecer com o cofre preenchido | E3 |
| Válido | `listar-dispositivos` com página mínima responde `status: "sucesso"` | segue para a lista | S4, S5 |
| Expirando | estimativa local (2 h desde a validação) | aviso na lista + ação "Renovar" (onda 3) | S7, S10 |
| Sem rede | exceção de transporte | estado "offline" com tentar de novo — não confundir com token rejeitado | S6, E2 |

Por que "Erro desconhecido" vira "token rejeitado": a plataforma não distingue token inválido de
expirado nem usa 401/403; foi verificado com chamadas reais que essa mensagem genérica é a resposta a
um token inválido em uma requisição correta. Essa interpretação é uma **regra de negócio no caso de uso**
(não no client HTTP), com teste próprio — [ADR-002](../adr/ADR-002-errors-as-values.md), contrato §1.2.

## 5. Renovação

- Endpoint: `renovarToken` (Swagger: `/autenticacao/renovarToken`; descrição: `POST
  https://<API_HOST>/autenticacao/renovar-token/v1`), corpo `{ "token": "<atual>" }`,
  resposta "Token Gerado". O formato exato da resposta é **a confirmar** com uma chamada real no início da
  onda 3 — ela rotaciona o token em uso, por isso não foi feita durante a leitura do contrato.
- Comportamento planejado (S10): ao aproximar-se da expiração, o app oferece "Renovar"; em sucesso,
  substitui o token no cofre sem sair da tela atual; em falha, mantém o token atual e mostra o estado de
  erro de S6.

## 6. Orçamento de requisições

A conta de teste tem um limite de requisições (~300 no momento da leitura, 20/09/2026). Cada chamada conta, inclusive
a validação do token. Por isso o app valida com **uma** chamada mínima (`tamanhoPagina: 1`), guarda a
lista em cache e nunca faz polling ([ADR-006](../adr/ADR-006-local-persistence-and-request-budget.md)).
Durante o desenvolvimento, prefira os testes com `MockEngine` a chamadas reais.

## 7. Pré-preenchimento em debug — decidido: **não adotado** (2026-09-20)

Estava em aberto se builds de **debug** poderiam pré-preencher o campo a partir de uma chave em
`local.properties`. **Não será feito**, e o motivo é o próprio ciclo de vida do token: `local.properties`
é lido em **tempo de compilação** e vira `BuildConfig`, enquanto o token vale no máximo 2 horas. Na
prática seria editar o arquivo e **recompilar** a cada duas horas — mais caro que colar no campo. O
pré-preenchimento resolveria digitação, mas o gargalo real é a rotação, não a digitação.

O que de fato reduz a fricção, e foi adotado no lugar: a colagem conferível de um olhar (S1.1) e a
validação local de formato, que impede que uma colagem truncada gaste requisição (S1.2).

Se a fricção reaparecer, a alternativa viável é um **deep link só em debug**
(`adb shell am start -d "mibosmart://token/Ot_…"`), que não exige recompilar. Não está adotada: abre
superfície de intent que exigiria validação de deep link e um ADR próprio, e não se justifica enquanto
colar do portal resolver.
