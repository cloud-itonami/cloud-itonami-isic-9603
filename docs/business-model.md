# Business Model: Funeral and related activities

## Classification

- Repository: `cloud-itonami-isic-9603`
- ISIC Rev.5: `9603`
- Activity: funeral and related activities -- funeral home, crematory, cemetery and related death-care services
- Social impact: community access, data sovereignty, transparent audit

## Customer

- independent funeral homes
- cooperative death-care collectives
- community cemetery/crematory operators

## Offer

- decedent/family intake
- arrangement-plan proposal
- disposition (burial/cremation)-completion proposal
- immutable audit ledger

## Revenue

- self-host setup: one-time implementation fee
- managed hosting: monthly subscription per funeral home
- support: monthly retainer with SLA
- migration: import from an incumbent funeral-home-management system
- per-arrangement fee

## Trust Controls

- no disposition (burial or cremation) is performed without human sign-off (a licensed funeral director) and family/legal authorization
- a fabricated authorization forces a hold, not an override
- every disposition path is auditable
- decedent/family data stays outside Git
- emergency manual override paths remain outside LLM control
