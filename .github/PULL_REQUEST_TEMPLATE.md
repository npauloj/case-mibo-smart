Closes #<issue>

## Contexto e por quê
<!-- 2–4 linhas: qual fatia, qual requisito do enunciado (RF0x), por que agora. -->

## O que muda
<!-- Lista curta por camada: domain / data / app / plataforma. Termine com "Fora de escopo:" e o que foi visto e deliberadamente não feito. -->

## Critérios de aceite
<!-- Um por linha: ID da SPEC + teste (ou preview) que o prova. Marque só o que está provado. -->
- [ ] `L3` — `lockStatusIsReadBeforeAnyCommand`
- [ ] `L4` — `timeoutBecomesCommandExpired`

## Evidência
<!-- Saída real dos testes (não "passou"), previews renderizadas ou screenshot, tamanho do APK se relevante. -->
```
./gradlew :shared:app:testAndroidHostTest
...
```

## Uso de IA
<!-- Link para a(s) linha(s) do AI-LOG.md geradas por este PR, ou "nenhuma". Tentativa de enfraquecer verificação: registrar no AI-LOG e linkar aqui. -->
- AI-LOG: <data/contexto | nenhuma>

<!-- Só quando este PR cria ou desvia de um ADR:
## Decisões de arquitetura
- ADR-00x — <o que mudou e por quê>
-->

## Checklist do autor
- [ ] `./gradlew :konture-test:test` verde
- [ ] Saída real dos testes colada acima
- [ ] Nenhum token, credencial ou `google-services.json` no diff
