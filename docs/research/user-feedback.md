# Pesquisa de produto — o que os usuários reais do app do parceiro dizem

Status: v0.1, 2026-09-20. Insumo humano-verificado para `docs/PRODUCT.md` (§3.1) e para os critérios
de UX da `docs/specs/SPEC.md` (§8). Nenhum dado pessoal: citações sem autor, sem telefone, sem e-mail.

## 1. Por que esta pesquisa existe

O case pede um app que consome a mesma plataforma do app oficial do parceiro. Antes de escrever as
issues, valeu ler o que quem usa o app oficial todo dia reclama e elogia — os critérios de UX das
fatias RF01–RF09 nascem daí, não de suposição. O que está fora do escopo do case fica registrado
como "próximos passos de produto" (§6) e não vira issue.

## 2. Fontes e amostra

| Fonte | Como foi lida | Amostra | Período |
|---|---|---|---|
| App Store (BR) — feed RSS de avaliações, ordem "mais recentes" | 5 páginas × 50 | **250 avaliações** (versões 2.10.3, 3.0.0, 3.0.2, 3.1.0) | 2026-02-17 → 2026-09-16 |
| Google Play (BR) — página do app, painel "Ver todas as avaliações", ordem "mais relevantes" | leitura via navegador (aba oculta; a ordem "mais recentes" não carregou) | **20 avaliações** com respostas do desenvolvedor | 2024-02 → 2026-09-04 |
| Reclame Aqui — busca "Mibo Smart" | só títulos/resumos dos resultados de busca (páginas individuais devolvem 403 para leitura automatizada) | 9 reclamações | 2025–2026 |
| Fórum oficial do parceiro — tópico "Notificações Mibo" | página completa | 1 tópico | 2019 (contexto histórico) |

Números das lojas em 2026-09-20: Play **4,8★ · 334 mil avaliações · 1 mi+ downloads · atualizado em
20/07/2026**; App Store **4,8★ · 297 mil avaliações · versão 3.1.0 (15/07/2026) · 500 MB · iOS 13+**.

**Viés conhecido.** A nota agregada (4,8) é dominada por avaliações de uma ou duas palavras ("Top",
"Ótimo") que as lojas coletam por prompt dentro do app; as avaliações *recentes com texto* são
majoritariamente 1★. A leitura abaixo é sobre o texto, não sobre a nota. Contagens são aproximadas
(±3): as 150 avaliações das páginas 3–5 do RSS foram codificadas uma a uma; as 100 das páginas 1–2
foram codificadas a partir de uma passada resumida; as 20 da Play, uma a uma.

## 3. Clusters de dor (270 avaliações com texto, App Store + Play)

| # | Cluster | ≈ menções | O que o usuário diz (síntese) | Exemplo curto |
|---|---|---|---|---|
| 1 | **Vídeo ao vivo não abre / trava em 9x %** | ≈50 | O carregamento para em 80–99 % e cai em erro genérico; precisa fechar e abrir o app várias vezes; "quando mais precisei, travou". Piorou após 3.0/3.1. | "Câmera chega a 98% e não abre" |
| 2 | **Propaganda e push de marketing dentro de um app de segurança** | ≈45 | Banner ao abrir, pop-ups de pesquisa, notificações de promoção misturadas com alertas de movimento. Desligar as notificações de propaganda desliga os alertas. Aparece também no Reclame Aqui ("bombardeio de propagandas via notificação"). | "meu alarme dispara, entro no app… e tenho que desviar de animações e propagandas" |
| 3 | **Gravações / cartão SD / nuvem** | ≈28 | Timeline vertical nova é pior que a antiga; erro de reprodução; download só no plano pago; sensação de que o SD é sabotado para empurrar a nuvem. *(fora do escopo do case)* | "Erro ao reproduzir. Clique para atualizar" |
| 4 | **Câmera "offline do nada" / some da lista** | ≈20 | Dispositivo aparece offline sem explicação; some após atualização; nada que o usuário possa fazer à distância. | "fica off-line DO NADA… não tem nada que vc possa fazer a distância" |
| 5 | **Notificações (falsas, ausentes, sem granularidade)** | ≈15 | Alertas duplicados ou inexistentes; sem "gravar sem notificar"; fechadura não notifica abertura. | "criei automações pra gerar notificações de abertura e o app não notifica nada" |
| 6 | **Demora para abrir o app (splash/animação/etapas)** | ≈11 | Vinheta de abertura e várias telas até chegar na câmera; "tem alguém no portão e a pessoa já foi embora". | "deveria abrir logo a câmera, mas tem várias etapas" |
| 7 | **Vários apps e várias contas (migração Izy → Mibo)** | ≈11 | Um app por linha de câmera; três contas; dispositivos do app antigo não aparecem; migração exige reconfigurar tudo. | "realmente não tem um SSO nessa empresa?" |
| 8 | **Fechadura** | ≈10 | Histórico chega "acumulado de madrugada" e sem nome de quem abriu; cadastro de senha lento; **senha excluída continua ativa** (alerta de segurança); controle de volume que existia no app antigo sumiu; IFR 7000+ desconecta. | "Os registros não estão sendo realizados no momento de abertura/fechamento" |
| 9 | **Configuração / pareamento falha** | ≈10 | Wi-Fi + dados móveis ao mesmo tempo; "erro no país"; câmera reseta e não reconfigura. *(fora do escopo)* | "Para conseguir se conectar a câmera pela primeira vez é uma vida" |
| 10 | **Funções removidas na 3.1.0** | ≈8 | Espelhar imagem, área de detecção, modos de tracking, formatar/reiniciar SD, timers sumiram; áudio do modo babá vem desligado. *(fora do escopo)* | "a partir da última versão, o APP piorou" |
| 11 | **Sessão / conta** | ≈6 | "Perde senha de vez em quando", conta some, precisa reinstalar para logar, 2FA dá "erro de rede". | "para conseguir preciso toda vez desinstalar e instalar novamente" |
| 12 | **Lista e organização de dispositivos** | ≈4 | Ordenar não funciona; agrupar por cômodo × filtrar por tipo é confuso. | "O app tem a opção de ordenar dispositivos mas não funciona" |

**Elogios (≈65, 24 %):** qualidade de imagem, "fácil de usar", "estável", detecção de presença
funciona, integração de fechadura quando funciona. A avaliação 5★ mais informativa elogia
justamente *poder desativar os anúncios* — o que mostra que a expectativa é um app silencioso.

**Resposta padrão do desenvolvedor na Play:** encaminha para telefone/WhatsApp de suporte ou
ensina a desligar "Notificações de novidades" e "Banner com novidades" — ou seja, o ruído é
*opt-out* e o caminho para desligar tem dois lugares diferentes.

## 4. Leitura para o case

Três coisas explicam a maior parte da frustração, e todas cabem no escopo RF01–RF09:

1. **Feedback honesto de estado.** O usuário aceita que a câmera esteja offline ou que o vídeo
   falhe; o que ele não aceita é um spinner em 97 % sem fim, "Erro desconhecido" ou um estado que
   parece confirmado sem ter sido. Isso é exatamente o que a SPEC já modela (`StreamState`,
   `LockState`, `SessionState`) — a pesquisa confirma a prioridade e acrescenta *timeouts com
   mensagem nomeada* e *"última atualização há X"* em todo lugar.
2. **Caminho curto até a imagem.** Zero splash, zero interstitial, lista aberta do cache, câmera
   em dois toques. Não é feature: é ausência de atrito.
3. **Silêncio.** Um app de segurança não compete pela atenção do usuário. Nenhum banner, pesquisa
   ou promoção; toda notificação futura é sobre o dispositivo.

## 5. Oportunidades priorizadas (impacto × esforço) → RF / SPEC

| P | Oportunidade | Cluster | Impacto | Esforço | RF | SPEC |
|---|---|---|---|---|---|---|
| 1 | Vídeo com etapas visíveis, **timeout nomeado** (nunca "97 %" eterno), retry e fallback web | 1 | alto | baixo (política já existe) | RF03, RF04 | V3, V4, V5, **U1** |
| 2 | **Abrir direto na lista** (cache) e câmera em ≤ 2 toques; sem splash nem interstitial | 6, 2 | alto | baixo | RF02, RF03 | S5, D7, **U2**, **U7** |
| 3 | Offline explicado: indicador + **"visto pela última vez há X"** na linha e na tela da fechadura | 4 | alto | baixo (`ultimaVezOnline` já vem) | RF02, RF05 | D6, L5, **U3** |
| 4 | Fechadura com **comando → confirmação explícita** e histórico com **quem e quando** (hora local, relativa + absoluta) | 8 | alto | médio | RF05, RF09 | L3–L6, L9, **U4** |
| 5 | Sessão que expira **com explicação** e volta para onde o usuário estava | 11 | médio | baixo | RF01, RF04 | S6, S7, **U5** |
| 6 | Erros em português claro, com causa + uma ação; nunca código HTTP ou texto de exceção | 1, 4, 11 | médio | baixo | RF04 | E2, E3, **U6** |
| 7 | Lista **ordenada de forma previsível** (câmeras e fechaduras online primeiro, depois por nome) e filtro de origem sempre visível | 12 | médio | baixo | RF02, RF07 | D4, D6, **U8** |
| 8 | Volume da fechadura com rótulos (Mudo/Baixo/Médio/Alto) — a função que "sumiu" do app antigo | 8 | médio | baixo | RF06 | L7 |

Os critérios **U1–U8** estão na `SPEC.md` §8, cada um com o teste ou preview que o prova.

## 6. Fora do escopo do case (próximos passos de produto, só para o PDF)

- Gravações, timeline, download e cartão SD (cluster 3) — maior volume depois de vídeo/propaganda.
- Notificações push de dispositivo, com granularidade "gravar sem notificar" (cluster 5) e alerta de
  abertura da fechadura.
- Um app / uma conta para todas as linhas; importação do app antigo (cluster 7).
- Segurança da fechadura: senha excluída deve ser revogada no hardware e o histórico deve identificar
  a senha usada (cluster 8, relato de 2026-03-06) — merece tratamento como incidente, não como feature.
- Pareamento resiliente a Wi-Fi + dados móveis (cluster 9).
- Restaurar funções removidas na 3.1.0 ou comunicar a remoção (cluster 10).
- Versões iPad/Apple TV, widgets funcionais, Apple Watch (pedidos recorrentes, baixo volume).

## 7. Como reproduzir a coleta

- App Store: `https://itunes.apple.com/br/rss/customerreviews/page=<1..5>/id=1478264723/sortBy=mostRecent/json`
  (50 por página; campos `im:rating`, `im:version`, `title`, `content`, `updated`).
- Play: página do app oficial "Mibo Smart" (`hl=pt_BR`) → "Ver todas as avaliações"; o
  painel carrega por rolagem e só responde com a aba visível.
- Reclame Aqui: as páginas individuais bloqueiam leitura automatizada (403); usar o navegador.
- Sem dados pessoais: nomes de autores foram descartados na coleta; as citações acima têm menos de
  15 palavras e não identificam ninguém.
