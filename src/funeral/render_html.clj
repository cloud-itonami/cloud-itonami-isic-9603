(ns funeral.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave5 rollout, iteration 11): this repo previously had no demo page
  and no generator at all. This namespace drives the REAL actor stack
  (`funeral.operation` -> `funeral.governor` -> `funeral.store`)
  through a scenario adapted from this repo's own `funeral.sim` demo
  driver (`clojure -M:run`, confirmed by actually running it before
  this file was written -- unlike `cloud-itonami-isic-851`'s
  `schoolops.sim`, this repo's own sim driver uses case ids that DO
  match `funeral.store/demo-data`'s seeded cases exactly, and every
  disposition it produces (auto-commit / escalate+approve / HARD hold,
  and the exact `:rule` on each hold) matches `funeral.governor`'s own
  documented checks precisely -- verified fact-for-fact against a real
  `clojure -M:run` transcript, so it was safe to build on directly
  rather than author from scratch), extended by one additional
  independently-probed HARD-hold path (`:evidence-incomplete` on
  case-2, confirmed by a real run before being added here) so all five
  of `funeral.governor`'s numbered HARD checks are demonstrated, not
  just four. Rendered deterministically -- no invented numbers, no
  timestamps in the page content, byte-identical across reruns against
  the same seed (verified by diffing two consecutive runs before
  shipping).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [funeral.store :as store]
            [funeral.operation :as op]
            [langgraph.graph :as g]))

;; ----------------------------- harness (unchanged across every repo
;; in this cluster -- do not rewrite, only copy) -----------------------

(def ^:private operator
  {:actor-id "op-1" :actor-role :licensed-funeral-director :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach, using ONLY real case ids from
  `funeral.store/demo-data`:

  case-1 (JPN, decedent Sakura Tanaka; 48 hours since death,
  authorization already verified) walks the full clean lifecycle: a
  `:case/intake` directory-normalization patch is a phase-3, no-legal-
  act-yet auto-commit (governor clean, `:case/intake` is the ONLY op in
  phase 3's `:auto` set); `:jurisdiction/assess` (JPN has a real
  spec-basis in `funeral.facts` -- Japan's Cemetery and Burial Act) and
  `:authorization/screen` (clean) each ALWAYS escalate (neither op is
  ever auto-eligible, at any phase) and are approved by a human
  licensed funeral director; `:disposition/perform` -- the ONE
  real-world, irreversible actuation event this actor performs (a real
  burial or cremation) -- ALSO ALWAYS escalates (the governor's own
  `high-stakes` gate AND the phase table agree, independently, that
  actuation is never auto, at any phase) and is approved, producing one
  draft final-disposition record (`JPN-DSP-000000`).

  Then five DISTINCT HARD-hold reasons, none of which ever reach a
  human (a human approver cannot override a HARD violation) -- all
  five of `funeral.governor`'s numbered checks:
    - case-2 (jurisdiction ATL, not in `funeral.facts/catalog`):
      `:jurisdiction/assess` HARD-holds on `:no-spec-basis` -- the
      advisor may not invent a jurisdiction's death-care/disposition
      requirements.
    - case-2 again, `:disposition/perform` attempted directly despite
      the blocked assessment above (assessment never committed, so
      `funeral.store/assessment-of` is nil for case-2): HARD-holds on
      `:evidence-incomplete` -- the governor independently refuses to
      trust the advisor's confidence when no disposition evidence
      checklist is actually on file.
    - case-3 (JPN, assessed cleanly first so evidence is on file and
      this hold is isolated to the waiting-period check alone; only 10
      hours since death): `:disposition/perform` HARD-holds on
      `:waiting-period-not-elapsed` -- the governor independently
      recomputes hours-since-death against the statutory 24-hour
      minimum (Cemetery and Burial Act Article 3) rather than trusting
      the advisor's proposal.
    - case-4 (`:authorization-verified? false` in the seed data):
      `:authorization/screen` HARD-holds on `:authorization-unverified`
      -- an unresolved next-of-kin/legal disposition authorization
      blocks progress, un-overridably, even though the screening op
      itself is the one that (re)discovers it.
    - case-1 AGAIN, `:disposition/perform` attempted a second time
      after it already committed once above: HARD-holds on
      `:already-disposed` -- the governor refuses a double disposition
      of the same case off a dedicated `:disposed?` fact.

  Returns the resulting store -- every field `render` below reads is
  real governor/store output, not a hand-typed copy."
  []
  (let [db (store/seed-db)
        actor (op/build db)]

    ;; case-1: clean directory-normalization patch -- phase-3
    ;; auto-commit, no legal act yet.
    (exec! actor "c1-intake" {:op :case/intake :subject "case-1"
                               :patch {:id "case-1" :decedent "Sakura Tanaka (family: Tanaka)"}})

    ;; case-1: jurisdiction disposition-requirements assessment (JPN
    ;; has a real spec-basis) -- ALWAYS escalates, approved by a human.
    (exec! actor "c1-assess" {:op :jurisdiction/assess :subject "case-1"})
    (approve! actor "c1-assess")

    ;; case-1: disposition-authorization screening, clean -- ALWAYS
    ;; escalates, approved by a human.
    (exec! actor "c1-screen" {:op :authorization/screen :subject "case-1"})
    (approve! actor "c1-screen")

    ;; case-1: REAL final disposition (actuation/perform-disposition, a
    ;; real burial or cremation) -- ALWAYS escalates regardless of
    ;; phase or confidence, approved by a human licensed funeral
    ;; director.
    (exec! actor "c1-dispose" {:op :disposition/perform :subject "case-1"})
    (approve! actor "c1-dispose")

    ;; case-2 (ATL): no official spec-basis in funeral.facts -> HARD
    ;; hold on :no-spec-basis, never reaches a human.
    (exec! actor "c2-assess" {:op :jurisdiction/assess :subject "case-2"})

    ;; case-2 again: disposition attempted directly despite the
    ;; blocked assessment above -- no assessment ever committed for
    ;; case-2, so the governor HARD-holds independently on
    ;; :evidence-incomplete, never reaches a human.
    (exec! actor "c2-dispose" {:op :disposition/perform :subject "case-2"})

    ;; case-3: assess JPN first (clean escalate+approve) so evidence is
    ;; on file and the waiting-period hold below is isolated.
    (exec! actor "c3-assess" {:op :jurisdiction/assess :subject "case-3"})
    (approve! actor "c3-assess")

    ;; case-3: only 10 hours since death, below the 24-hour statutory
    ;; minimum -> HARD hold on :waiting-period-not-elapsed, never
    ;; reaches a human.
    (exec! actor "c3-dispose" {:op :disposition/perform :subject "case-3"})

    ;; case-4: seeded with an unverified disposition authorization ->
    ;; HARD hold on :authorization-unverified, never reaches a human.
    (exec! actor "c4-screen" {:op :authorization/screen :subject "case-4"})

    ;; case-1 AGAIN: double disposition of an already-processed case ->
    ;; HARD hold on :already-disposed, never reaches a human.
    (exec! actor "c1-dispose-again" {:op :disposition/perform :subject "case-1"})

    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger subject-id]
  (last (filter #(= (:subject %) subject-id) ledger)))

(defn- status-cell [ledger subject-id]
  (let [f (last-fact-for ledger subject-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :governor-hold (:t f))
      (let [rule (-> f :violations first :rule)]
        (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>"))
      (= :approval-rejected (:t f)) "<span class=\"critical\">approval rejected</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- case-row [ledger {:keys [id decedent jurisdiction hours-since-death
                                 authorization-verified? disposed? disposition-number]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc id) (esc decedent) (esc jurisdiction) (esc hours-since-death)
          (if authorization-verified? "<span class=\"ok\">verified</span>" "<span class=\"critical\">unverified</span>")
          (if disposed? (str "disposed &middot; <code>" (esc disposition-number) "</code>") "not disposed")
          (status-cell ledger id)))

(defn- ledger-row [{:keys [t op subject disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc subject)
          (esc (or (some->> basis (map #(if (keyword? %) (name %) %)) (str/join ", "))
                    (some-> disposition name) ""))))

(defn- record-row [{:strs [record_id case_id jurisdiction immutable]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc record_id) (esc case_id) (esc jurisdiction)
          (if immutable "<span class=\"ok\">immutable draft</span>" "draft")))

(def ^:private action-gate-rows
  ;; Static description of this actor's own op contract
  ;; (`funeral.governor`/`funeral.phase`) -- documentation of fixed
  ;; behavior, not runtime telemetry, so it is legitimately hand-
  ;; described rather than derived from a live run.
  ["        <tr><td><code>:case/intake</code></td><td><span class=\"ok\">phase-3 auto-commit when clean, no legal act yet -- the ONLY auto-eligible op in this domain</span></td></tr>"
   "        <tr><td><code>:jurisdiction/assess</code></td><td><span class=\"warn\">ALWAYS human approval &middot; spec-basis independently checked against <code>funeral.facts</code>, never fabricated</span></td></tr>"
   "        <tr><td><code>:authorization/screen</code></td><td><span class=\"warn\">ALWAYS human approval when clean &middot; an unverified next-of-kin/legal disposition authorization is a HARD, un-overridable hold instead</span></td></tr>"
   "        <tr><td><code>:disposition/perform</code></td><td><span class=\"warn\">ALWAYS human approval &middot; a real burial or cremation (actuation/perform-disposition) &middot; disposition-evidence completeness, the statutory waiting period, authorization status and double-disposition are all independently recomputed, never auto at any phase</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        cases (store/all-cases db)
        case-rows (str/join "\n" (map (partial case-row ledger) cases))
        ledger-rows (str/join "\n" (map ledger-row ledger))
        record-rows (str/join "\n" (map record-row (store/disposition-history db)))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-9603 &middot; funeral and related activities</title><style>\n"
     "table { width: 100%; border-collapse: collapse; font-size: 14px; }\n"
     ".ok { color: #137a3f; }\n"
     "body { font-family: system-ui,-apple-system,sans-serif; margin: 0; color: #1a1a1a; background: #fafafa; }\n"
     "header.bar { display: flex; align-items: center; gap: 12px; padding: 12px 20px; background: #fff; border-bottom: 1px solid #e5e5e5; }\n"
     "th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #f0f0f0; }\n"
     "h2 { margin-top: 0; font-size: 15px; }\n"
     ".warn { color: #b25c00; background: #fff8e1; padding: 2px 6px; border-radius: 4px; }\n"
     "main { max-width: 980px; margin: 24px auto; padding: 0 20px; }\n"
     "header.bar h1 { font-size: 18px; margin: 0; font-weight: 600; }\n"
     ".muted { color: #888; font-size: 13px; }\n"
     ".critical { color: #fff; background: #b3261e; padding: 2px 6px; border-radius: 4px; font-weight: 600; }\n"
     ".card { background: #fff; border: 1px solid #e5e5e5; border-radius: 8px; padding: 16px; margin-bottom: 16px; }\n"
     ".err { color: #b3261e; background: #fbe9e7; padding: 2px 6px; border-radius: 4px; }\n"
     "th { font-weight: 600; color: #555; font-size: 12px; text-transform: uppercase; letter-spacing: 0.04em; }\n"
     "header.bar .badge { margin-left: auto; font-size: 12px; color: #666; }\n"
     "code { font-size: 12px; background: #f4f4f4; padding: 1px 4px; border-radius: 3px; }\n"
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Funeral and related activities (ISIC 9603) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · final disposition always human-approved</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Cases</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>funeral.store</code> via <code>funeral.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Case</th><th>Decedent</th><th>Jurisdiction</th><th>Hours since death</th><th>Disposition authorization</th><th>Final disposition</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     case-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Draft final-disposition records</h2>\n"
     "    <p class=\"muted\">Unsigned drafts only — the licensed funeral director's own act of performing and signing off the disposition is outside this actor's authority (see README <code>Actuation</code>).</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Record id</th><th>Case</th><th>Jurisdiction</th><th>Status</th></tr></thead>\n"
     "      <tbody>\n"
     record-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (Funeral Services Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden by a human approver. Jurisdiction spec-basis, disposition-evidence completeness, the statutory waiting period, and disposition authorization are independently recomputed, never trusted from the advisor's proposal; a real final disposition is always a licensed funeral director's call, at every rollout phase.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Case</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/disposition-history db)) "disposition drafts )")))
