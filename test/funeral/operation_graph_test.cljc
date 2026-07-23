(ns funeral.operation-graph-test
  "Integration tests for `funeral.operation/build` -- proves the REAL
  compiled `langgraph.graph` StateGraph runs end-to-end via
  `langgraph.graph/run*` through commit / hard-hold / escalate-approve /
  escalate-reject routes. No prior test file in this repo exercised
  `operation/build` at all -- every other test covers
  governor/phase/facts/registry/store in isolation, which proves those
  pure functions work but not that the graph wiring actually threads
  them together."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [funeral.facts :as facts]
            [funeral.operation :as operation]
            [funeral.store :as store]))

(def ^:private op-context {:actor-id "operator-01" :phase 3})

(defn- exec
  ([actor tid request] (exec actor tid request op-context))
  ([actor tid request context]
   (g/run* actor {:request request :context context} {:thread-id tid})))

(deftest commit-path-case-intake-auto-commits-in-phase-3
  (testing ":case/intake is the only op in phase-3's :auto set -- a
            clean intake proposal commits straight through the REAL
            compiled graph with no interrupt, and the ledger is
            verified EMPTY before the run so the post-run fact is
            genuinely this run's own effect"
    (let [s (store/seed-db)
          actor (operation/build s)]
      (is (empty? (store/ledger s)))
      (let [result (exec actor "t-commit"
                         {:op :case/intake :subject "case-test-1"
                          :patch {:id "case-test-1" :decedent "Test Decedent"
                                  :hours-since-death 0 :authorization-verified? true
                                  :disposed? false :jurisdiction "JPN" :status :intake}})
            state (:state result)]
        (is (= :done (:status result)))
        (is (= :commit (:disposition state)))
        (let [ledger (store/ledger s)]
          (is (= 1 (count ledger)))
          (is (= :committed (:t (first ledger))))
          (is (= :case/intake (:op (first ledger)))))
        (is (= "Test Decedent" (:decedent (store/case-record s "case-test-1"))))))))

(deftest hard-hold-no-spec-basis-blocks-before-escalation
  (testing "case-2's own jurisdiction (ATL) has no registered spec-basis
            -- a HARD governor violation. The real graph routes straight
            to :hold, never pausing for human approval even though
            :jurisdiction/assess is not in phase-3's :auto set"
    (let [s (store/seed-db)
          actor (operation/build s)]
      (is (empty? (store/ledger s)))
      (let [result (exec actor "t-hold" {:op :jurisdiction/assess :subject "case-2"})
            state (:state result)]
        (is (= :done (:status result)) "no interrupt -- HARD holds never pause for approval")
        (is (= :hold (:disposition state)))
        (let [ledger (store/ledger s)]
          (is (= 1 (count ledger)))
          (is (= :governor-hold (:t (first ledger))))
          (is (some #{:no-spec-basis} (map :rule (:violations (first ledger))))))))))

(deftest hard-hold-waiting-period-not-elapsed-through-compiled-graph
  (testing "case-3's own hours-since-death (10) is below the statutory
            minimum waiting period (24 hours, Japan's Cemetery and
            Burial Act Article 3) -- a HARD governor violation, proven
            end-to-end through the compiled graph. Evidence is
            pre-seeded complete so this isolates the waiting-period
            check specifically among the possible hard violations"
    (let [s (store/seed-db)
          _ (store/commit-record!
             s {:effect :assessment/set
                :path ["case-3"]
                :payload {:jurisdiction "JPN"
                          :checklist (:required-evidence (facts/spec-basis "JPN"))
                          :spec-basis (:provenance (facts/spec-basis "JPN"))}})
          actor (operation/build s)
          result (exec actor "t-waiting" {:op :disposition/perform :subject "case-3"})
          state (:state result)]
      (is (= :done (:status result)) "no interrupt -- HARD holds never pause for approval")
      (is (= :hold (:disposition state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :governor-hold (:t (first ledger))))
        (is (some #{:waiting-period-not-elapsed} (map :rule (:violations (first ledger)))))))))

(deftest hard-hold-authorization-unverified-self-holds-on-screening
  (testing "case-4's own :authorization-verified? false means the
            :authorization/screen proposal ITSELF reports :verdict
            :unverified -- the governor HARD-holds on the screening
            op's own finding, before any disposition is even attempted,
            proven end-to-end through the compiled graph"
    (let [s (store/seed-db)
          actor (operation/build s)
          result (exec actor "t-authhold" {:op :authorization/screen :subject "case-4"})
          state (:state result)]
      (is (= :done (:status result)))
      (is (= :hold (:disposition state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (some #{:authorization-unverified} (map :rule (:violations (first ledger)))))))))

(deftest escalate-then-approve-commits-and-genuinely-consults-advisor
  (testing ":jurisdiction/assess is NEVER in any phase's :auto set, so
            even a Governor-clean proposal for a REAL jurisdiction
            (JPN, with a real spec-basis) GENUINELY interrupts
            (checkpointed) at :request-approval -- the ledger stays
            EMPTY until a human resumes it. Also proves the Advisor's
            real proposal (JPN's own spec-basis :provenance string, not
            a hardcoded literal in funeral.operation) threads through
            :advise -> :govern -> :decide -> :request-approval -> :commit"
    (let [s (store/seed-db)
          actor (operation/build s)]
      (is (empty? (store/ledger s)))
      (let [held (exec actor "t-escalate" {:op :jurisdiction/assess :subject "case-1"})]
        (is (= :interrupted (:status held)))
        (is (= [:request-approval] (:frontier held)))
        (is (empty? (store/ledger s)) "not yet committed -- awaiting human sign-off")
        (let [approved (g/run* actor {:approval {:status :approved :by "funeral-director-01"}}
                               {:thread-id "t-escalate" :resume? true})
              approved-state (:state approved)]
          (is (= :done (:status approved)))
          (is (= :commit (:disposition approved-state)))
          (let [ledger (store/ledger s)]
            (is (= 1 (count ledger)))
            (is (= :committed (:t (first ledger))))
            (is (= :jurisdiction/assess (:op (first ledger)))))
          (let [assessment (store/assessment-of s "case-1")]
            (is (some? assessment))
            (is (= (:provenance (facts/spec-basis "JPN")) (:spec-basis assessment))
                "the committed assessment carries the REAL JPN spec-basis's
                own provenance string -- proof the graph genuinely threads
                the Advisor's proposal through rather than hardcoding one")))))))

(deftest escalate-then-reject-holds
  (testing "a human funeral director rejecting an escalated
            :jurisdiction/assess routes to :hold via the
            :request-approval node's own decision, and durably records
            the rejection -- not a hand-rolled parallel path"
    (let [s (store/seed-db)
          actor (operation/build s)
          _held (exec actor "t-reject" {:op :jurisdiction/assess :subject "case-1"})
          rejected (g/run* actor {:approval {:status :rejected :by "funeral-director-01"}}
                           {:thread-id "t-reject" :resume? true})
          rejected-state (:state rejected)]
      (is (= :done (:status rejected)))
      (is (= :hold (:disposition rejected-state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :approval-rejected (:t (first ledger))))))))
