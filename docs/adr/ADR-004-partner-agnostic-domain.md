# ADR-004. Partner-agnostic domain; the partner contract lives only in `:shared:data`

Status: Accepted (2026-09-20)

## Context

The rubric names "suporte a múltiplos parceiros" as an architecture criterion. The Open Casa Inteligente contract
carries a long list of quirks that must not leak upward (`docs/api-contract.md` §7): Portuguese field
names (`tamanhoPagina`, `pagina`, `origem`, `aberto`, `habilitado`), plural/singular mismatch
(`vinculados` vs `vinculado`), the `productId`/`idProduto` bug on `/fechaduras/volume/v1`, composite
lock addressing `<lockNs>_<hubNs>_<hubIdProduto>`, two hosts, two envelopes, HTTP-200-for-everything.

Efthymiou (p. 227–229) prescribes an explicit mapping chain `JSON → ModelRaw → ModelPlain → Model →
ModelPresentation` and (p. 236) "your architecture should tell readers about the system, not about
the frameworks you used in your system".

## Decision Drivers

- Renaming or replacing the partner API must not touch `:shared:domain` or `:shared:app`.
- Domain vocabulary in English; the wire names belong to the DTO layer.
- The lock's composite address is a partner detail, not a domain concept.

## Considered Options

1. Use the JSON DTOs (`@Serializable`) directly as domain models with `@SerialName`.
2. Separate DTOs in `:shared:data` with mappers to English domain models in `:shared:domain`.

## Decision

Option 2.

- `:shared:domain`: `Device(id: DeviceId, name, model, kind: DeviceKind, isOnline, lastSeen: Instant,
  origin: DeviceOrigin, parent: DeviceId?)`, `DeviceKind = Camera | Lock | Hub | Other`,
  `LockAddress` as an opaque value type, `DeviceOriginFilter = All | Linked | Shared`, repository
  contracts `DeviceRepository`, `LockRepository`, `StreamingRepository`, `SessionRepository`.
- `:shared:data`: `@Serializable` DTOs mirroring the wire (`ListarDispositivosRequest(tamanhoPagina,
  pagina, origem)`, `DeviceDto(ns, modelo, nome, status, subdispositivo, idProduto, dispositivoPai,
  idProdutoDispositivoPai, …)`), mappers, the composite-`ns` builder, and the `productId`+`idProduto`
  double field on the volume request.
- Base URL and host are injected (`ApiConfig(baseUrl)`) from local, unversioned configuration
  (`local.properties` → `smarthome.apiHost`, surfaced through `BuildConfig`); there is no hard-coded
  default. The contract documents two equivalent hosts (`<API_HOST>` and `<PORTAL_HOST>`), which is one
  more reason the value must be injectable.
- Device classification (`modelo` prefix first, `funcoes` contains `RTSV` to confirm streaming) is a
  data-layer policy behind `DeviceRepository.list()`; the domain only sees `DeviceKind`.

## Consequences

- (+) A second partner = a second data module implementing the same contracts; zero UI change.
- (+) Contract bugs are documented and quarantined in one place with tests.
- (−) Mapper code and duplicated model shapes; accepted for a ~10-field model.

## Confirmation

- `rg "tamanhoPagina|idProduto|statusCode" shared/domain shared/app` returns nothing.
- `:shared:data` tests assert the exact JSON body sent for each request (Ktor `MockEngine`) and the
  mapping of a real captured response into domain models.

## Guardrail

- Guardrail: rule 1 — `domain must not depend on frameworks, persistence or the outer modules` in `:konture-test`
- Guardrail: rule 7 — `serialization stays inside the data module` in `:konture-test`
