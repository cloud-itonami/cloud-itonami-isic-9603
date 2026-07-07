# ADR-0001: cloud-itonami-isic-9603 -- FuneralOps-LLM as a contained intelligence node

- Status: Accepted (2026-07-07)
- Related: `cloud-itonami-isic-6511`/`6512`/`6621`/`6622`/`6629`/`6520`/
  `6530`/`6820`/`6612`/`6492`/`6920`/`6611`/`7120`/`8620`/`8530`/`9200`/
  `7500` ADR-0001s (the pattern this ADR ports); ADR-2607071250/
  ADR-2607071320/ADR-2607071351/ADR-2607071618/ADR-2607071640/
  ADR-2607071654/ADR-2607071717/ADR-2607071732/ADR-2607071752
  (`6612`/`6492`/`6920`/`6611`/`7120`/`8620`/`8530`/`9200`/`7500`, the
  nine verticals built outside ADR-2607032000's original insurance/
  real-estate batch -- this is the tenth)
- Context: Continuing the standing "pick a new ISIC blueprint
  vertical" direction past `7500`, this ADR deepens `cloud-itonami-
  isic-9603` (funeral and related activities) from `:blueprint` to
  `:implemented`, the eighteenth actor in this fleet -- the FIRST
  personal-services/death-care vertical (ISIC division 96), a domain
  with a genuinely irreversible single actuation event.

## Problem

A funeral home's disposition workflow bundles several distinct
concerns under one governed workflow:

1. **Jurisdiction death-care correctness** -- an official spec-basis
   citation from a real death-care regulator (MHLW/FTC/Ministry of
   Justice/state public-order authorities), never fabricated.
2. **Statutory waiting-period sufficiency** -- does a case's own
   hours-since-death satisfy a real statutory minimum wait (Japan's
   Cemetery and Burial Act Article 3: 24 hours) before disposition may
   proceed? Reuses `veterinary.registry/withdrawal-period-
   insufficient?`'s NEWLY-established temporal-sufficiency pure-
   ground-truth-recompute shape for a SECOND domain instance -- but
   applied UNCONDITIONALLY (every decedent is subject to the same
   statutory wait, unlike veterinary's food-producing-animal-only
   gate).
3. **Disposition-authorization verification** -- has next-of-kin/
   legal authorization actually been confirmed? Reuses the
   unconditional-evaluation screening discipline for a SEVENTH
   distinct grounding.
4. **Real actuation, once, and irreversible** -- performing a burial or
   cremation is not merely a real-world act but a permanently
   IRREVERSIBLE one -- there is no "undo" for a completed disposition,
   making this actor's single actuation event the starkest instance of
   this fleet's "actuation is always a human call" invariant.

An LLM has no authority or grounding for any of these. The design
problem is therefore not "run a funeral home with an LLM" but "seal
the LLM inside a trust boundary and layer evidence-sufficiency,
statutory-waiting-period verification, authorization verification,
audit and human-approval on top of it, while structurally fixing the
one real, irreversible actuation event as human-only."

## Decision

### 1. FuneralOps-LLM is sealed into the bottom node; it never disposes directly

`funeral.funeralopsllm` returns exactly four kinds of proposal: intake
normalization, jurisdiction disposition checklist, authorization
screening, and final-disposition draft. No proposal writes the SSoT
or commits a real final disposition directly.

### 2. OperationActor = langgraph-clj StateGraph, 1 run = 1 funeral/death-care operation

`funeral.operation/build` is the SAME StateGraph shape as every
sibling actor's operation namespace, copied verbatim.

### 3. `waiting-period-elapsed?` reuses `veterinary.registry`'s temporal-sufficiency shape for a SECOND domain instance -- applied unconditionally

`waiting-period-not-elapsed-violations` reuses the pure-ground-truth
temporal-sufficiency recompute `cloud-itonami-isic-7500` just
established (a minimum time interval must fully elapse before an
irreversible real-world act may proceed), for a genuinely different
domain (death-care rather than veterinary food-safety). UNLIKE
`veterinary.registry/withdrawal-period-insufficient?` (gated on a
`:food-producing?` type tag, since only SOME animals have a
withdrawal-period concept), this check applies UNCONDITIONALLY to
EVERY case, since every decedent is subject to the identical statutory
minimum wait -- proving the newly-established shape generalizes to
both a conditionally-gated and an unconditional application within
two consecutive builds.

### 4. `24` is a real statutory figure, not an invented one

`funeral.registry/minimum-waiting-period-hours` is drawn directly from
Japan's 墓地、埋葬等に関する法律 (Cemetery and Burial Act) Article 3,
cited in `funeral.facts`'s JPN entry -- the same "never fabricate a
number, cite a real source" discipline this fleet's entire `facts`/
`registry` architecture enforces, extended here to a numeric
regulatory constant rather than only jurisdiction spec-basis text.

### 5. Authorization screening reuses the unconditional-evaluation discipline for a seventh distinct grounding

`authorization-unverified-violations` reuses `casualty.governor/
sanctions-violations`'s fix (evaluated unconditionally, not scoped to
a specific op, so the screening op itself can HARD-hold on its own
finding) for a further application in this fleet: an unverified next-
of-kin/legal disposition authorization blocks the real-world act even
if the screening op itself never (re)ran in this session.

### 6. Single actuation event -- the starkest instance of this fleet's actuation invariant

`funeral.governor`'s `high-stakes` set has exactly one member
(`:actuation/perform-disposition`), matching `6511`'s/`6621`'s/
`6629`'s/`6612`'s/`6492`'s/`7120`'s/`8620`'s/`7500`'s single-actuation
shape -- but uniquely among this fleet's single-actuation domains, a
performed disposition cannot be reversed at all (a mis-settled payout
or a mis-finalized grade can in principle be corrected through further
process; a completed cremation cannot). This makes the "no phase ever
auto-commits this op" invariant carry the highest possible real-world
stakes in this fleet to date.

### 7. Double-disposition guard checks a dedicated boolean fact, not `:status`

`already-disposed-violations` checks `:disposed?`, a dedicated boolean
set once and never cleared, rather than a `:status` value that could
legitimately advance past a checked state (the exact trap `cloud-
itonami-isic-6492`'s ADR-0001 documents in detail, explicitly avoided
BY DESIGN in `6920`'s, `6611`'s, `7120`'s, `8620`'s, `8530`'s, `9200`'s
and `7500`'s equivalent guards). This actor's `:status` never needs to
encode "has this actuation already happened" at all -- a deliberate
architectural choice applied here for an eighth consecutive time.

### 8. No bespoke capability lib

Like `6920`/`7120`/`8620`/`8530`/`9200`/`7500`, and unlike most other
actors in this fleet (each referencing its own `kotoba-lang/*`
capability lib), this vertical's service/member records are practice-
specific rather than a shared cross-operator data contract --
`funeral.*` runs on the generic identity/forms/dmn/bpmn/audit-ledger
stack only, per the blueprint's own explicit statement.

### 9. No bug this time

Like `7120`/`8620`/`8530`/`9200`/`7500` (and unlike `6492`'s status-
lifecycle bug or `6920`'s NullPointerException), this build's test
suite, lint, and demo-ledger verification all passed clean on the
first run. The demo (`clojure -M:dev:run`) was still independently
verified against the printed audit ledger -- basis tags `:no-spec-
basis` · `:waiting-period-not-elapsed` · `:authorization-unverified` ·
`:already-disposed` all appear exactly where the sim script intends,
and the disposition history contains exactly one drafted record after
the double-disposition attempt is held -- the same discipline that
caught every real bug in this fleet so far, applied here and finding
nothing to fix.

## Consequences

- (+) Funeral/death-care gets the same governed, auditable-actor
  treatment as the seventeen prior actors, extending the pattern to a
  genuinely different domain (ISIC division 96) for the first time,
  and to the fleet's starkest irreversibility case yet.
- (+) The actuation invariant (governor + phase, two layers) is
  regression-tested by `test/funeral/phase_test.clj`'s `disposition-
  perform-never-auto-at-any-phase`.
- (+) `MemStore` ‖ `DatomicStore` parity is proven by `test/funeral/
  store_contract_test.clj`, the same `:db-api`-driven swap pattern
  every sibling actor uses.
- (+) `waiting-period-elapsed?`/`waiting-period-not-elapsed-
  violations` proves `veterinary.registry`'s newly-established
  temporal-sufficiency check shape generalizes across BOTH a
  conditionally-gated application (veterinary, food-producing-animal-
  only) and an unconditional application (funeral, every case) within
  two consecutive builds, regression-tested by `test/funeral/
  governor_contract_test.clj`'s `waiting-period-not-elapsed-is-held`.
- (+) Both the demo and the full test suite passed clean on the first
  run -- no bug this time, unlike `6492`/`6920`.
- (-) This R0 seeds only 4 jurisdictions (JPN, USA, GBR, DEU) with an
  official spec-basis, out of ~194 worldwide; `funeral.facts/coverage`
  reports this honestly rather than claiming broader coverage.
- (-) `waiting-period-elapsed?` models only a single representative
  statutory figure (24 hours, drawn from Japan's Cemetery and Burial
  Act), not a jurisdiction-by-jurisdiction survey of every waiting-
  period variant, nor a full next-of-kin priority hierarchy with
  objection resolution -- see `cloud-itonami-isic-9603`'s own ADR-0001
  and README coverage table for the full honest-scope accounting.
- Fleet-wide: 23 actors now `:implemented` out of 643 total registry
  entries before this promotion (24 after); the next "pick a new ISIC
  blueprint vertical" firing remains free to select from ANY remaining
  `:blueprint`-tier `cloud-itonami-*` entry.

## Alternatives considered

| Option | Verdict | Reason |
|---|---|---|
| Add this as an addendum to any prior post-batch ADR | ❌ | All nine of those ADRs' titles and scopes are explicitly `cloud-itonami-isic-6612`/`6492`/`6920`/`6611`/`7120`/`8620`/`8530`/`9200`/`7500`; mixing a different ISIC division (96, distinct from those nine's divisions) into any would blur scope boundaries |
| Keep `cloud-itonami-isic-9603` at `:blueprint` only | ❌ | The standing direction continues past `7500`; funeral/death-care is a natural, well-precedented next domain that also proves the newly-established temporal-sufficiency check shape generalizes to an unconditional application |
| Model a full next-of-kin priority hierarchy with objection resolution for conformance-test rigor | ❌ | Genuinely more complex real-world legal-authority logic that this R0 does not claim to model correctly -- honestly scoped to a single verified/unverified authorization flag instead, same as every sibling's "starting catalog, not exhaustive" posture |
| Reference a capability lib (e.g. a hypothetical `kotoba-lang/funeral`) for consistency with most prior actors | ❌ | The blueprint itself explicitly states this vertical's records are practice-specific, not a shared cross-operator contract -- inventing a capability lib reference where the blueprint says none exists would misrepresent the domain, the same reasoning `6920`'s/`7120`'s/`8620`'s/`8530`'s/`9200`'s/`7500`'s ADRs already established |
