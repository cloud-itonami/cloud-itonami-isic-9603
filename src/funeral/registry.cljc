(ns funeral.registry
  "Pure-function final-disposition record construction -- an append-
  only funeral-home book-of-record draft.

  Like every sibling actor's registry, there is no single international
  check-digit standard for a disposition reference number -- every
  funeral home/jurisdiction assigns its own reference format. This
  namespace does NOT invent one; it builds a jurisdiction-scoped
  sequence number and validates the record's required fields, the same
  honest, non-fabricating discipline `funeral.facts` uses.

  `waiting-period-elapsed?`/`minimum-waiting-period-hours` reuse
  `veterinary.registry/withdrawal-period-insufficient?`'s NEWLY-
  established temporal-sufficiency pure-ground-truth-recompute shape
  for a SECOND domain instance: a minimum time interval must fully
  elapse before an irreversible real-world act may proceed. Unlike
  `veterinary.registry`'s check (gated on a `:food-producing?` type
  tag -- only SOME animals have a withdrawal-period concept at all),
  this check applies UNCONDITIONALLY to every case (every decedent is
  subject to the same statutory minimum wait), so no type-tag gate is
  needed here -- a simpler application of the same shape. `24` is not
  an invented figure: it is the REAL statutory minimum under Japan's
  墓地、埋葬等に関する法律 (Cemetery and Burial Act) Article 3 -- see
  `funeral.facts` for the citation.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real funeral-home-management system. It builds the
  RECORD a funeral home would keep, not the act of performing the
  disposition itself (that is `funeral.operation`'s `:disposition/
  perform`, always human-gated -- see README `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  licensed funeral director's act, not this actor's. See README
  `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(def minimum-waiting-period-hours
  "The real statutory minimum interval between death and disposition
  (cremation/burial) -- see ns docstring for the citation (Japan's
  Cemetery and Burial Act Article 3). A single representative figure,
  not a jurisdiction-by-jurisdiction survey of every waiting-period
  variant -- see `funeral.facts` for the honest per-jurisdiction
  citation this figure is drawn from."
  24)

(defn waiting-period-elapsed?
  "Does `case-record`'s own `:hours-since-death` satisfy `minimum-
  waiting-period-hours`? A pure ground-truth check against the case's
  own permanent field -- applies UNCONDITIONALLY to every case (see ns
  docstring for why this needs no type-tag gate, unlike `veterinary.
  registry/withdrawal-period-insufficient?`)."
  [{:keys [hours-since-death]}]
  (and (number? hours-since-death) (>= hours-since-death minimum-waiting-period-hours)))

(defn register-disposition
  "Validate + construct the FINAL-DISPOSITION registration DRAFT -- the
  funeral home's own legal act of performing a real burial or
  cremation. Pure function -- does not touch any real funeral-home-
  management system; it builds the RECORD a funeral home would keep.
  `funeral.governor` independently re-verifies the case's own waiting-
  period sufficiency and authorization status, and blocks a double-
  disposition of the same case, before this is ever allowed to
  commit."
  [case-id jurisdiction sequence]
  (when-not (and case-id (not= case-id ""))
    (throw (ex-info "disposition: case_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "disposition: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "disposition: sequence must be >= 0" {})))
  (let [disposition-number (str (str/upper-case jurisdiction) "-DSP-" (zero-pad sequence 6))
        record {"record_id" disposition-number
                "kind" "final-disposition-draft"
                "case_id" case-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "disposition_number" disposition-number
     "certificate" (unsigned-certificate "FinalDisposition" disposition-number disposition-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
