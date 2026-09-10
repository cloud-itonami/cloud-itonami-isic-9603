(ns funeral.governor
  "Funeral Services Governor -- the independent compliance layer that
  earns the FuneralOps-LLM the right to commit. The LLM has no notion
  of jurisdictional death-care/disposition law, whether a case's own
  hours-since-death actually satisfies the statutory minimum waiting
  period, whether next-of-kin/legal authorization has actually been
  verified, or when an act stops being a draft and becomes a real-
  world final disposition, so this MUST be a separate system able to
  *reject* a proposal and fall back to HOLD -- the death-care analog
  of `cloud-itonami-isic-6512`'s CasualtyGovernor.

  Five checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them (you don't get to approve your way past a
  fabricated jurisdiction spec-basis, incomplete disposition evidence,
  a statutory waiting period that hasn't yet elapsed, an unverified
  disposition authorization, or a double disposition of the same
  case). The confidence/actuation gate is SOFT: it asks a human to
  look (low confidence / actuation), and the human may approve -- but
  see `funeral.phase`: for `:stake :actuation/perform-disposition` (a
  real burial or cremation) NO phase ever allows auto-commit either.
  Two independent layers agree that actuation is always a human call.

    1. Spec-basis                  -- did the jurisdiction proposal cite
                                       an OFFICIAL source (`funeral.
                                       facts`), or invent one? Like
                                       `credit.governor`'s/`clinic.
                                       governor`'s/`veterinary.
                                       governor`'s actuation ops,
                                       `:disposition/perform` acts
                                       directly on a pre-seeded case
                                       (see `funeral.store`'s own
                                       docstring) -- there is no 'case
                                       is missing' failure mode to
                                       guard against here.
    2. Evidence incomplete         -- for `:disposition/perform`, has
                                       the jurisdiction actually been
                                       assessed with a full disposition
                                       evidence checklist on file?
    3. Waiting period not elapsed  -- for `:disposition/perform`,
                                       INDEPENDENTLY recompute whether
                                       the case's own `:hours-since-
                                       death` satisfies `funeral.
                                       registry/minimum-waiting-period-
                                       hours` (`funeral.registry/
                                       waiting-period-elapsed?`) --
                                       needs no proposal inspection or
                                       stored-verdict lookup at all.
                                       Reuses `veterinary.registry/
                                       withdrawal-period-insufficient?`'s
                                       NEWLY-established temporal-
                                       sufficiency pure-ground-truth-
                                       recompute shape for a SECOND
                                       domain instance -- but UNLIKE
                                       that check (gated on a `:food-
                                       producing?` type tag, since only
                                       SOME animals have a withdrawal-
                                       period concept), this check
                                       applies UNCONDITIONALLY to every
                                       case, since every decedent is
                                       subject to the same statutory
                                       minimum wait -- a simpler
                                       application of the same shape,
                                       with no type-tag gate needed.
    4. Authorization unverified    -- reported by THIS proposal itself
                                       (an `:authorization/screen` that
                                       just found an unverified
                                       authorization), or already on
                                       file for the case
                                       (`:authorization/screen`/
                                       `:disposition/perform`).
                                       Evaluated UNCONDITIONALLY (not
                                       scoped to a specific op), the
                                       SAME discipline `casualty.
                                       governor/sanctions-violations`/
                                       `marketadmin.governor/
                                       surveillance-flag-unresolved-
                                       violations`/`testlab.governor/
                                       calibration-not-current-
                                       violations`/`clinic.governor/
                                       credential-not-current-
                                       violations`/`registrar.governor/
                                       integrity-flag-unresolved-
                                       violations`/`wagering.governor/
                                       patron-flag-unresolved-
                                       violations` established -- the
                                       SEVENTH distinct application of
                                       this exact discipline (an
                                       unverified next-of-kin/legal
                                       authorization blocks the real-
                                       world act even if the screening
                                       op itself never (re)ran in this
                                       session).
    5. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:disposition/
                                       perform` (a REAL, irreversible
                                       act) -> escalate.

  One more guard, double-disposition prevention, is enforced but NOT
  listed as a numbered HARD check above because it needs no upstream
  comparison at all -- `already-disposed-violations` refuses to
  perform a final disposition for the SAME case twice, off a dedicated
  `:disposed?` fact (never a `:status` value) -- the SAME 'check a
  dedicated boolean, not status' discipline `accounting.governor`'s/
  `marketadmin.governor`'s/`testlab.governor`'s/`clinic.governor`'s/
  `registrar.governor`'s/`wagering.governor`'s/`veterinary.
  governor`'s guards establish, informed by `cloud-itonami-isic-6492`'s
  status-lifecycle bug (ADR-2607071320)."
  (:require [funeral.facts :as facts]
            [funeral.registry :as registry]
            [funeral.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Performing a real final disposition (burial or cremation) is the ONE
  real-world actuation event this actor performs, and it is
  irreversible -- a single-member set, matching `cloud-itonami-isic-
  6511`'s/`6621`'s/`6629`'s/`6612`'s/`6492`'s/`7120`'s/`8620`'s/
  `7500`'s single-actuation shape."
  #{:actuation/perform-disposition})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:jurisdiction/assess` (or `:disposition/perform`) proposal with
  no spec-basis citation is a HARD violation -- never invent a
  jurisdiction's death-care/disposition requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:jurisdiction/assess :disposition/perform} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は法域要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:disposition/perform`, the jurisdiction's required death-
  certificate/authorization-to-dispose/funeral-director-license
  evidence must actually be satisfied -- do not trust the advisor's
  self-reported confidence alone."
  [{:keys [op subject]} st]
  (when (= op :disposition/perform)
    (let [c (store/case-record st subject)
          assessment (store/assessment-of st subject)]
      (when-not (and assessment
                     (facts/required-evidence-satisfied?
                      (:jurisdiction c) (:checklist assessment)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(死亡診断書/埋火葬許可証/資格確認記録等)が充足していない状態での施行提案"}]))))

(defn- waiting-period-not-elapsed-violations
  "For `:disposition/perform`, INDEPENDENTLY recompute whether the
  case's own hours-since-death satisfies `funeral.registry/minimum-
  waiting-period-hours` via `funeral.registry/waiting-period-
  elapsed?` -- needs no proposal inspection or stored-verdict lookup
  at all. Reuses `veterinary.registry`'s temporal-sufficiency shape
  for a second domain instance, applied UNCONDITIONALLY (see
  `funeral.registry`'s own docstring for why no type-tag gate is
  needed here)."
  [{:keys [op subject]} st]
  (when (= op :disposition/perform)
    (let [c (store/case-record st subject)]
      (when-not (registry/waiting-period-elapsed? c)
        [{:rule :waiting-period-not-elapsed
          :detail (str subject " の死後経過時間(" (:hours-since-death c)
                      "時間)が法定待機期間(" registry/minimum-waiting-period-hours "時間)を下回っている")}]))))

(defn- authorization-unverified-violations
  "An unverified disposition authorization -- reported by THIS
  proposal (e.g. an `:authorization/screen` that itself just found an
  unverified authorization), or already on file in the store for the
  case (`:authorization/screen`/`:disposition/perform`) -- is a HARD,
  un-overridable hold. Evaluated UNCONDITIONALLY (not scoped to a
  specific op) so the screening op itself can HARD-hold on its own
  finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (= :unverified (get-in proposal [:value :verdict]))
        case-id (when (contains? #{:authorization/screen :disposition/perform} op) subject)
        hit-on-file? (and case-id (= :unverified (:verdict (store/authorization-of st case-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :authorization-unverified
        :detail "遺族/法定代理人による施行許可が未確認の状態での施行提案は進められない"}])))

(defn- already-disposed-violations
  "For `:disposition/perform`, refuses to perform a final disposition
  for the SAME case twice, off a dedicated `:disposed?` fact (never a
  `:status` value) -- see ns docstring for why this sidesteps the
  status-lifecycle risk `cloud-itonami-isic-6492`'s ADR-0001
  documents."
  [{:keys [op subject]} st]
  (when (= op :disposition/perform)
    (when (store/case-already-disposed? st subject)
      [{:rule :already-disposed
        :detail (str subject " は既に施行済み")}])))

(defn check
  "Censors a FuneralOps-LLM proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (waiting-period-not-elapsed-violations request st)
                           (authorization-unverified-violations request proposal st)
                           (already-disposed-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
