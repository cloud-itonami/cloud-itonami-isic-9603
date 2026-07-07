(ns funeral.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:disposition/perform` must NEVER be a member of any
  phase's `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [funeral.phase :as phase]))

(deftest disposition-perform-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real final disposition"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :disposition/perform))
          (str "phase " n " must not auto-commit :disposition/perform")))))

(deftest authorization-screen-never-auto-at-any-phase
  (testing "screening carries no direct capital risk, but is still never auto-eligible, matching every sibling KYC/conflict/independence/surveillance/calibration/credential/integrity/patron screen"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :authorization/screen))
          (str "phase " n " must not auto-commit :authorization/screen")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":case/intake carries no direct capital risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:case/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :case/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :disposition/perform} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :case/intake} :commit)))))
