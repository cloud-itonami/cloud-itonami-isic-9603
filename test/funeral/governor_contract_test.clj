(ns funeral.governor-contract-test
  "The governor contract as executable tests -- the funeral/death-care
  analog of `cloud-itonami-isic-6512`'s `casualty.governor-contract-
  test`. The single invariant under test:

    FuneralOps-LLM never performs a final disposition the Funeral
    Services Governor would reject, `:disposition/perform` NEVER auto-
    commits at any phase, `:case/intake` (no direct capital risk) MAY
    auto-commit when clean, and every decision (commit OR hold) leaves
    exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [funeral.store :as store]
            [funeral.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :licensed-funeral-director :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- assess!
  "Walks `subject` through assess -> approve, leaving an assessment on
  file. Uses distinct thread-ids per call site by suffixing
  `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-assess") {:op :jurisdiction/assess :subject subject} operator)
  (approve! actor (str tid-prefix "-assess")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :case/intake :subject "case-1"
                   :patch {:id "case-1" :decedent "Sakura Tanaka (family: Tanaka)"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Sakura Tanaka (family: Tanaka)" (:decedent (store/case-record db "case-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest jurisdiction-assess-always-needs-approval
  (testing "assess is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :jurisdiction/assess :subject "case-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/assessment-of db "case-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a jurisdiction/assess proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :jurisdiction/assess :subject "case-1" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/assessment-of db "case-1")) "no assessment written"))))

(deftest disposition-perform-without-assessment-is-held
  (testing "disposition/perform before any jurisdiction assessment -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :disposition/perform :subject "case-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest waiting-period-not-elapsed-is-held
  (testing "a case whose statutory minimum waiting period has not yet elapsed -> HOLD"
    (let [[db actor] (fresh)
          _ (assess! actor "t5pre" "case-3")
          res (exec-op actor "t5" {:op :disposition/perform :subject "case-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:waiting-period-not-elapsed} (-> (store/ledger db) last :basis)))
      (is (empty? (store/disposition-history db))))))

(deftest authorization-unverified-is-held-and-unoverridable
  (testing "an unverified disposition authorization on a case -> HOLD, and never reaches request-approval"
    (let [[db actor] (fresh)
          res (exec-op actor "t6" {:op :authorization/screen :subject "case-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:authorization-unverified} (-> (store/ledger db) first :basis)))
      (is (nil? (store/authorization-of db "case-4")) "no clearance written"))))

(deftest disposition-perform-always-escalates-then-human-decides
  (testing "a clean, fully-assessed, waiting-period-satisfied case still ALWAYS interrupts for human approval -- actuation/perform-disposition is never auto"
    (let [[db actor] (fresh)
          _ (assess! actor "t7pre" "case-1")
          r1 (exec-op actor "t7" {:op :disposition/perform :subject "case-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, disposition record drafted"
        (let [r2 (approve! actor "t7")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:disposed? (store/case-record db "case-1"))))
          (is (= 1 (count (store/disposition-history db))) "one draft disposition record")))))
  (testing "reject -> hold, nothing disposed"
    (let [[db actor] (fresh)
          _ (assess! actor "t8pre" "case-1")
          _ (exec-op actor "t8" {:op :disposition/perform :subject "case-1"} operator)
          r2 (g/run* actor {:approval {:status :rejected :by "op-1"}}
                     {:thread-id "t8" :resume? true})]
      (is (= :hold (get-in r2 [:state :disposition])))
      (is (empty? (store/disposition-history db)) "nothing disposed on reject"))))

(deftest disposition-perform-double-disposition-is-held
  (testing "performing a final disposition for the same case twice -> HOLD on the second attempt, even though the figures match cleanly"
    (let [[db actor] (fresh)
          _ (assess! actor "t9pre" "case-1")
          _ (exec-op actor "t9a" {:op :disposition/perform :subject "case-1"} operator)
          _ (approve! actor "t9a")
          res (exec-op actor "t9" {:op :disposition/perform :subject "case-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-disposed} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/disposition-history db))) "still only the one earlier disposition"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :case/intake :subject "case-1"
                          :patch {:id "case-1" :decedent "Sakura Tanaka (family: Tanaka)"}} operator)
      (exec-op actor "b" {:op :jurisdiction/assess :subject "case-1" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
