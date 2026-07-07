# cloud-itonami-isic-9603

Open Business Blueprint for **ISIC Rev.5 9603**: Funeral and related
activities. This repository publishes a funeral/death-care actor --
case intake, jurisdiction assessment, disposition-authorization
screening and final disposition -- as an OSS business that any
qualified funeral-home operator can fork, deploy, run, improve and
sell.

Built on this workspace's
[`langgraph-clj`](https://github.com/com-junkawasaki/langgraph-clj)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet
([`cloud-itonami-isic-6511`](https://github.com/cloud-itonami/cloud-itonami-isic-6511),
[`6512`](https://github.com/cloud-itonami/cloud-itonami-isic-6512),
[`6621`](https://github.com/cloud-itonami/cloud-itonami-isic-6621),
[`6622`](https://github.com/cloud-itonami/cloud-itonami-isic-6622),
[`6629`](https://github.com/cloud-itonami/cloud-itonami-isic-6629),
[`6520`](https://github.com/cloud-itonami/cloud-itonami-isic-6520),
[`6530`](https://github.com/cloud-itonami/cloud-itonami-isic-6530),
[`6820`](https://github.com/cloud-itonami/cloud-itonami-isic-6820),
[`6612`](https://github.com/cloud-itonami/cloud-itonami-isic-6612),
[`6492`](https://github.com/cloud-itonami/cloud-itonami-isic-6492),
[`6920`](https://github.com/cloud-itonami/cloud-itonami-isic-6920),
[`6611`](https://github.com/cloud-itonami/cloud-itonami-isic-6611),
[`7120`](https://github.com/cloud-itonami/cloud-itonami-isic-7120),
[`8620`](https://github.com/cloud-itonami/cloud-itonami-isic-8620),
[`8530`](https://github.com/cloud-itonami/cloud-itonami-isic-8530),
[`9200`](https://github.com/cloud-itonami/cloud-itonami-isic-9200),
[`7500`](https://github.com/cloud-itonami/cloud-itonami-isic-7500)) --
the first personal-services/death-care vertical (ISIC division 96) in
this fleet. Here it is **FuneralOps-LLM ⊣ Funeral Services Governor**.

> **Why an actor layer at all?** An LLM is great at drafting a case
> summary, normalizing intake, and checking whether a case's own
> hours-since-death actually satisfies a statutory minimum waiting
> period -- but it has **no notion of which jurisdiction's death-care/
> disposition requirements are official, no license to perform a real,
> irreversible final disposition, and no way to know on its own
> whether next-of-kin/legal authorization has actually been
> verified**. Letting it perform a disposition directly invites
> fabricated jurisdiction citations, a burial or cremation performed
> before the statutory waiting period has elapsed, and an unverified
> authorization being quietly waved through -- and liability for
> whoever runs it. This project seals the FuneralOps-LLM into a single
> node and wraps it with an independent **Funeral Services Governor**,
> a human **approval workflow**, and an immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor covers case intake through jurisdiction assessment,
disposition-authorization screening and final disposition. It does
**not**, by itself, hold a license to operate a funeral home in any
jurisdiction, and it does not claim to. It also does **not** model a
full next-of-kin priority hierarchy (spouse > adult children > parents
> siblings, etc., with objection resolution) -- it checks a single
verified/unverified authorization flag, not a ranked-authority dispute
process (see `funeral.registry/waiting-period-elapsed?`'s own
docstring for the honest simplification the waiting-period check
makes: a single representative statutory figure, not a jurisdiction-
by-jurisdiction survey of every waiting-period variant). Whoever
deploys and operates a live instance (a licensed funeral-home
operator) supplies the jurisdiction-specific license, the real family-
liaison/legal expertise and the real funeral-home-management-system
integrations, and bears that jurisdiction's liability -- the software
supplies the governed, spec-cited, audited execution scaffold so that
operator does not have to build the compliance layer from scratch for
every new market.

### Actuation

**Performing a real, irreversible final disposition (burial or
cremation) is never autonomous, at any phase, by construction.** Two
independent layers enforce this (`funeral.governor`'s `:actuation/
perform-disposition` high-stakes gate and `funeral.phase`'s phase
table, which never puts `:disposition/perform` in any phase's `:auto`
set) -- see `funeral.phase`'s docstring and `test/funeral/
phase_test.clj`'s `disposition-perform-never-auto-at-any-phase`. The
actor may draft, check and recommend; a human licensed funeral
director is always the one who actually performs a disposition. Like
`6511`/`6621`/`6629`/`6612`/`6492`/`7120`/`8620`/`7500`, this actor has
ONE actuation event.

## The core contract

```
case intake + jurisdiction facts (funeral.facts, spec-cited)
        |
        v
   ┌──────────────┐   proposal      ┌───────────────────────┐
   │ FuneralOps-  │ ─────────────▶ │ Funeral Services            │  (independent system)
   │ LLM (sealed) │  + citations    │ Governor: spec-basis ·      │
   └──────────────┘                 │ evidence-incomplete ·        │
                             commit ◀────┼──────────▶ hold │ waiting-period-not-elapsed
                                 │             │           │ (temporal sufficiency,
                           record + ledger  escalate ─▶ human   2nd domain instance) ·
                                             (ALWAYS for         authorization-unverified ·
                                              :disposition/         already-disposed
                                              perform)
```

**The FuneralOps-LLM never performs a final disposition the Funeral
Services Governor would reject, and never does so without a human
sign-off.** Hard violations (fabricated jurisdiction requirements;
unsupported disposition evidence; a statutory waiting period that
hasn't yet elapsed; an unverified disposition authorization; a double
disposition) force **hold** and *cannot* be approved past; a clean
disposition proposal still always routes to a human.

## Run

```bash
clojure -M:dev:run     # walk one clean lifecycle (final disposition) + four HARD-hold cases through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here a facility-logistics robot
assists physical preparation-room and transport tasks, under the
actor, gated by the independent **Funeral Services Governor**. The
governor never dispatches hardware itself; `:high`/`:safety-critical`
actions require human sign-off.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Funeral Services Governor, final-disposition draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`9603`). Like `6920`/`7120`/`8620`/`8530`/`9200`/`7500`, this
vertical's service/member records are practice-specific rather than a
shared cross-operator data contract, so `funeral.*` runs on the
generic identity/forms/dmn/bpmn/audit-ledger stack only -- no bespoke
domain capability lib to reference at all.

## Layout

| File | Role |
|---|---|
| `src/funeral/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + final-disposition history. No dynamically-filed sub-record -- the actuation op acts directly on a pre-seeded case, and the double-disposition guard checks a dedicated `:disposed?` boolean rather than a `:status` value |
| `src/funeral/registry.cljc` | Final-disposition draft records, plus `waiting-period-elapsed?`/`minimum-waiting-period-hours` -- reuses `veterinary.registry`'s newly-established temporal-sufficiency shape for a SECOND domain instance (a real statutory minimum wait, cited from Japan's Cemetery and Burial Act), applied unconditionally (no type-tag gate needed, unlike the veterinary check) |
| `src/funeral/facts.cljc` | Per-jurisdiction death-care/disposition catalog with an official spec-basis citation per entry, honest coverage reporting |
| `src/funeral/funeralopsllm.cljc` | **FuneralOps-LLM Advisor** -- `mock-advisor` ‖ `llm-advisor`; intake/assessment/authorization-screening/final-disposition proposals |
| `src/funeral/governor.cljc` | **Funeral Services Governor** -- 4 HARD checks (spec-basis · evidence-incomplete · waiting-period-not-elapsed, pure ground-truth temporal-sufficiency recompute · authorization-unverified, unconditional evaluation) + already-disposed guard + 1 soft (confidence/actuation gate) |
| `src/funeral/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted assess → supervised (disposition always human; case intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/funeral/operation.cljc` | **OperationActor** -- langgraph-clj StateGraph |
| `src/funeral/sim.cljc` | demo driver |
| `test/funeral/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers case intake through jurisdiction assessment,
disposition-authorization screening and final disposition -- the core
governed lifecycle this blueprint's own `docs/business-model.md` names
as its Offer:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Case intake + per-jurisdiction death-care checklisting, HARD-gated on an official spec-basis citation (`:case/intake`/`:jurisdiction/assess`) | A full next-of-kin priority hierarchy with objection resolution (see `waiting-period-elapsed?`'s docstring and the README scope note above) |
| Disposition-authorization screening, evaluated unconditionally so the screening op itself can HARD-hold on its own finding (`:authorization/screen`) | Real funeral-home-management-system integration, cemetery/crematory-facility scheduling |
| Final disposition, HARD-gated on the statutory minimum waiting period having elapsed and a double-disposition guard (`:disposition/perform`) | Ongoing bereavement-support/aftercare services themselves |
| Immutable audit ledger for every intake/assessment/screening/disposition decision | |

Extending coverage is additive: add the next gate (e.g. a next-of-kin
priority-ranking check) as its own governed op with its own HARD
checks and tests, following the SAME "an independent governor re-
verifies against the actor's own records before any real-world act"
pattern this repo's flagship op already establishes.

## Jurisdiction coverage (honest)

`funeral.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `funeral.facts/catalog` --
currently 4 seeded (JPN, USA, GBR, DEU) out of ~194 jurisdictions
worldwide. This is a starting catalog to prove the governor contract
end-to-end, not a claim of global coverage. Adding a jurisdiction is
additive: one map entry in `funeral.facts/catalog`, citing a real
official source -- never fabricate a jurisdiction's requirements to make
coverage look bigger.

## Maturity

`:implemented` -- `FuneralOps-LLM` + `Funeral Services Governor` run
as real, tested code (see `Run` above), promoted from the originally-
published `:blueprint`-tier scaffold, modeled closely on the
seventeen prior actors' architecture. See `docs/adr/0001-
architecture.md` for the history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
