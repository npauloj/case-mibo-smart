---
name: Slice (vertical, agent-ready)
about: One vertical slice of the spec, sized for one reviewable PR. Follows the ticket contract.
title: "<ID>: <imperative, specific title>"
labels: ["type:slice"]
---

<!-- Fill every field; empty fields fail the pre-dispatch gate. Reference SPEC criteria by ID, do not copy them.
     `agent-ready` is applied by the gate, not here. -->

- **Problem:** <what is missing and why it matters now>
- **Scope:** <what this slice covers>
- **Non-goals:** <what it explicitly does NOT touch>
- **Expected behaviour:** <observable outcome for the user / the screen>
- **Technical detail:** <contracts, envelopes, invariants, request budget — see docs/api-contract.md>
- **Files:** <modules and paths this slice creates or edits>
- **Depends on:** <SLICE-IDs | none>
- **Acceptance criteria (EARS):** <SPEC IDs, e.g. L3, L4 — each with the test that proves it; previews: see SPEC "Visual acceptance">
- **Test scenarios:** <happy path; edge; failure — including offline / token expired where relevant>
- **Applies to / ADRs:** <commonMain | androidMain + iosMain (expect/actual)> · <ADR-00x implemented or at risk> · Risk: <n/a | how to disable>
