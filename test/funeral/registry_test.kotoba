(ns funeral.registry-test
  (:require [clojure.test :refer [deftest is]]
            [funeral.registry :as r]))

;; ----------------------------- waiting-period-elapsed? -----------------------------

(deftest waiting-period-elapsed-when-at-or-above-minimum
  (is (r/waiting-period-elapsed? {:hours-since-death r/minimum-waiting-period-hours}))
  (is (r/waiting-period-elapsed? {:hours-since-death (+ r/minimum-waiting-period-hours 1)}))
  (is (not (r/waiting-period-elapsed? {:hours-since-death (- r/minimum-waiting-period-hours 1)}))))

(deftest waiting-period-not-elapsed-when-missing
  (is (not (r/waiting-period-elapsed? {}))))

;; ----------------------------- register-disposition -----------------------------

(deftest disposition-is-a-draft-not-a-real-disposition
  (let [result (r/register-disposition "case-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest disposition-assigns-disposition-number
  (let [result (r/register-disposition "case-1" "JPN" 7)]
    (is (= (get result "disposition_number") "JPN-DSP-000007"))
    (is (= (get-in result ["record" "case_id"]) "case-1"))
    (is (= (get-in result ["record" "kind"]) "final-disposition-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest disposition-validation-rules
  (is (thrown? Exception (r/register-disposition "" "JPN" 0)))
  (is (thrown? Exception (r/register-disposition "case-1" "" 0)))
  (is (thrown? Exception (r/register-disposition "case-1" "JPN" -1))))

(deftest disposition-history-is-append-only
  (let [d1 (r/register-disposition "case-1" "JPN" 0)
        hist (r/append [] d1)
        d2 (r/register-disposition "case-2" "JPN" 1)
        hist2 (r/append hist d2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-DSP-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-DSP-000001" (get-in hist2 [1 "record_id"])))))
