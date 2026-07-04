# Governance

`cloud-itonami-isic-9603` is an OSS open-business blueprint for funeral and related activities -- funeral home, crematory, cemetery and related death-care services.
Governance covers both the capability layer and the operator model.

## Maintainers

Maintainers may merge changes that preserve these invariants:

- the Funeral Services Governor remains independent of the advisor.
- hard policy violations (fabricated verification, incomplete records) cannot be
  overridden by human approval.
- performing a final disposition (burial or cremation) always escalates to a human -- never automated.
- every hold, approval and action path is auditable.
- member/client/customer personal data stay outside Git.

## Decision Records

Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, public business model, operator certification or license
should add or update an ADR.

## Operator Governance

Anyone may fork and operate independently. itonami.cloud certification is a
separate trust mark and should require security, audit and data-flow review.

Certified operators can lose certification for:

- bypassing the Funeral Services Governor's policy checks
- mishandling member/client/customer data
- misrepresenting certification status
- failing to respond to security incidents
- hiding material changes to customer-facing operation
