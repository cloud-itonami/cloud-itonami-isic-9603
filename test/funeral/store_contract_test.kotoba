(ns funeral.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a configuration
  change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the sibling
  actor."
  (:require [clojure.test :refer [deftest is testing]]
            [funeral.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Sakura Tanaka (family: Tanaka)" (:decedent (store/case-record s "case-1"))))
      (is (= "JPN" (:jurisdiction (store/case-record s "case-1"))))
      (is (= 48 (:hours-since-death (store/case-record s "case-1"))))
      (is (true? (:authorization-verified? (store/case-record s "case-1"))))
      (is (= 10 (:hours-since-death (store/case-record s "case-3"))))
      (is (false? (:authorization-verified? (store/case-record s "case-4"))))
      (is (false? (:disposed? (store/case-record s "case-1"))))
      (is (= ["case-1" "case-2" "case-3" "case-4"]
             (mapv :id (store/all-cases s))))
      (is (nil? (store/authorization-of s "case-1")))
      (is (nil? (store/assessment-of s "case-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/disposition-history s)))
      (is (zero? (store/next-sequence s "JPN")))
      (is (false? (store/case-already-disposed? s "case-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :case/upsert
                                 :value {:id "case-1" :decedent "Sakura Tanaka (family: Tanaka)"}})
        (is (= "Sakura Tanaka (family: Tanaka)" (:decedent (store/case-record s "case-1"))))
        (is (= 48 (:hours-since-death (store/case-record s "case-1"))) "hours-since-death preserved"))
      (testing "assessment / authorization payloads commit and read back"
        (store/commit-record! s {:effect :assessment/set :path ["case-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/assessment-of s "case-1")))
        (store/commit-record! s {:effect :authorization/set :path ["case-1"]
                                 :payload {:case-id "case-1" :verdict :verified}})
        (is (= {:case-id "case-1" :verdict :verified} (store/authorization-of s "case-1"))))
      (testing "final disposition drafts a disposition record and advances the sequence"
        (store/commit-record! s {:effect :case/mark-disposed :path ["case-1"]})
        (is (= "JPN-DSP-000000" (get (first (store/disposition-history s)) "record_id")))
        (is (= "final-disposition-draft" (get (first (store/disposition-history s)) "kind")))
        (is (true? (:disposed? (store/case-record s "case-1"))))
        (is (= 1 (count (store/disposition-history s))))
        (is (= 1 (store/next-sequence s "JPN")))
        (is (true? (store/case-already-disposed? s "case-1")))
        (is (false? (store/case-already-disposed? s "case-2"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/case-record s "nope")))
    (is (= [] (store/all-cases s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/disposition-history s)))
    (is (zero? (store/next-sequence s "JPN")))
    (store/with-cases s {"x" {:id "x" :decedent "d"
                              :hours-since-death 48 :authorization-verified? true
                              :disposed? false :jurisdiction "JPN" :status :intake}})
    (is (= "d" (:decedent (store/case-record s "x"))))))
