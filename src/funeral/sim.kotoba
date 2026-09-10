(ns funeral.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean case through
  intake -> jurisdiction assessment -> disposition-authorization
  screening -> final-disposition proposal (always escalates) -> human
  approval -> commit, then shows four HARD holds (a jurisdiction with
  no spec-basis, a case whose statutory waiting period has not yet
  elapsed, an unverified disposition authorization, and a double
  disposition of an already-processed case) that never reach a human
  at all, and prints the audit ledger + the draft final-disposition
  records."
  (:require [langgraph.graph :as g]
            [funeral.store :as store]
            [funeral.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :licensed-funeral-director :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== case/intake case-1 (JPN, clean; 48 hours since death, authorization verified) ==")
    (println (exec! actor "t1" {:op :case/intake :subject "case-1"
                                :patch {:id "case-1" :decedent "Sakura Tanaka (family: Tanaka)"}} operator))

    (println "== jurisdiction/assess case-1 (escalates -- human approves) ==")
    (println (exec! actor "t2" {:op :jurisdiction/assess :subject "case-1"} operator))
    (println (approve! actor "t2"))

    (println "== authorization/screen case-1 (clean; escalates -- human approves) ==")
    (println (exec! actor "t3" {:op :authorization/screen :subject "case-1"} operator))
    (println (approve! actor "t3"))

    (println "== disposition/perform case-1 (always escalates -- actuation/perform-disposition) ==")
    (let [r (exec! actor "t4" {:op :disposition/perform :subject "case-1"} operator)]
      (println r)
      (println "-- human licensed funeral director approves --")
      (println (approve! actor "t4")))

    (println "== jurisdiction/assess case-2 (no spec-basis -> HARD hold) ==")
    (println (exec! actor "t5" {:op :jurisdiction/assess :subject "case-2" :no-spec? true} operator))

    (println "== jurisdiction/assess case-3 (escalates -- human approves; sets up the waiting-period test) ==")
    (println (exec! actor "t6" {:op :jurisdiction/assess :subject "case-3"} operator))
    (println (approve! actor "t6"))

    (println "== disposition/perform case-3 (10 hours since death < 24-hour statutory minimum -> HARD hold) ==")
    (println (exec! actor "t7" {:op :disposition/perform :subject "case-3"} operator))

    (println "== authorization/screen case-4 (unverified disposition authorization -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t8" {:op :authorization/screen :subject "case-4"} operator))

    (println "== disposition/perform case-1 AGAIN (double disposition of an already-processed case -> HARD hold) ==")
    (println (exec! actor "t9" {:op :disposition/perform :subject "case-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft final-disposition records ==")
    (doseq [r (store/disposition-history db)] (println r))))
