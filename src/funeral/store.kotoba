(ns funeral.store
  "SSoT for the funeral/death-care actor, behind a `Store` protocol so
  the backend is a swap, not a rewrite -- the same seam every prior
  `cloud-itonami-isic-*` actor in this fleet uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/funeral/store_contract_test.clj), which is the whole point:
  the actor, the Funeral Services Governor and the audit ledger never
  know which SSoT they run on.

  Like `clinic.store`'s/`veterinary.store`'s simpler entities, a CASE
  is acted on directly by the ONE actuation op -- no dynamically-filed
  sub-record, and the double-disposition guard checks a dedicated
  `:disposed?` boolean rather than a `:status` value, the same
  discipline `accounting.governor`'s/`marketadmin.governor`'s/
  `testlab.governor`'s/`clinic.governor`'s/`registrar.governor`'s/
  `wagering.governor`'s/`veterinary.governor`'s guards establish.

  The ledger stays append-only on every backend: 'which case was
  screened for verified disposition authorization, which final
  disposition was performed, on what jurisdictional basis, approved by
  whom' is always a query over an immutable log -- the audit trail a
  family trusting a funeral home needs, and the evidence an operator
  needs if a disposition is later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [funeral.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (case-record [s id])
  (all-cases [s])
  (authorization-of [s case-id] "committed authorization screening verdict for a case, or nil")
  (assessment-of [s case-id] "committed jurisdiction assessment, or nil")
  (ledger [s])
  (disposition-history [s] "the append-only final-disposition history (funeral.registry drafts)")
  (next-sequence [s jurisdiction] "next disposition-number sequence for a jurisdiction")
  (case-already-disposed? [s case-id] "has this case's final disposition already been performed?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-cases [s cases] "replace/seed the case directory (map id->case)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained case set so the actor + tests run offline."
  []
  {:cases
   {"case-1" {:id "case-1" :decedent "Sakura Tanaka (family: Tanaka)"
               :hours-since-death 48 :authorization-verified? true
               :disposed? false :jurisdiction "JPN" :status :intake}
    "case-2" {:id "case-2" :decedent "Atlantis Doe (family: Doe)"
               :hours-since-death 48 :authorization-verified? true
               :disposed? false :jurisdiction "ATL" :status :intake}
    "case-3" {:id "case-3" :decedent "鈴木一郎 (family: 鈴木)"
               :hours-since-death 10 :authorization-verified? true
               :disposed? false :jurisdiction "JPN" :status :intake}
    "case-4" {:id "case-4" :decedent "田中花子 (family: 田中)"
               :hours-since-death 48 :authorization-verified? false
               :disposed? false :jurisdiction "JPN" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- perform-disposition!
  "Backend-agnostic `:case/mark-disposed` -- looks up the case via the
  protocol and drafts the final-disposition record, and returns
  {:result .. :case-patch ..} for the caller to persist."
  [s case-id]
  (let [c (case-record s case-id)
        seq-n (next-sequence s (:jurisdiction c))
        result (registry/register-disposition case-id (:jurisdiction c) seq-n)]
    {:result result
     :case-patch {:disposed? true
                  :disposition-number (get result "disposition_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (case-record [_ id] (get-in @a [:cases id]))
  (all-cases [_] (sort-by :id (vals (:cases @a))))
  (authorization-of [_ id] (get-in @a [:authorizations id]))
  (assessment-of [_ case-id] (get-in @a [:assessments case-id]))
  (ledger [_] (:ledger @a))
  (disposition-history [_] (:dispositions @a))
  (next-sequence [_ jurisdiction] (get-in @a [:sequences jurisdiction] 0))
  (case-already-disposed? [_ case-id] (boolean (get-in @a [:cases case-id :disposed?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :case/upsert
      (swap! a update-in [:cases (:id value)] merge value)

      :assessment/set
      (swap! a assoc-in [:assessments (first path)] payload)

      :authorization/set
      (swap! a assoc-in [:authorizations (first path)] payload)

      :case/mark-disposed
      (let [case-id (first path)
            {:keys [result case-patch]} (perform-disposition! s case-id)
            jurisdiction (:jurisdiction (case-record s case-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:sequences jurisdiction] (fnil inc 0))
                       (update-in [:cases case-id] merge case-patch)
                       (update :dispositions registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-cases [s cases] (when (seq cases) (swap! a assoc :cases cases)) s))

(defn seed-db
  "A MemStore seeded with the demo case set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :assessments {} :authorizations {} :ledger [] :sequences {}
                           :dispositions []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (assessment/authorization payloads, ledger
  facts, disposition records) are stored as EDN strings so
  `langchain.db` doesn't expand them into sub-entities -- the same
  convention every sibling actor's store uses."
  {:case/id                     {:db/unique :db.unique/identity}
   :assessment/case-id           {:db/unique :db.unique/identity}
   :authorization/case-id          {:db/unique :db.unique/identity}
   :ledger/seq                      {:db/unique :db.unique/identity}
   :disposition/seq                  {:db/unique :db.unique/identity}
   :sequence/jurisdiction               {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- case->tx [{:keys [id decedent hours-since-death authorization-verified? disposed?
                         jurisdiction status disposition-number]}]
  (cond-> {:case/id id}
    decedent                        (assoc :case/decedent decedent)
    hours-since-death                 (assoc :case/hours-since-death hours-since-death)
    (some? authorization-verified?)     (assoc :case/authorization-verified? authorization-verified?)
    (some? disposed?)                     (assoc :case/disposed? disposed?)
    jurisdiction                            (assoc :case/jurisdiction jurisdiction)
    status                                   (assoc :case/status status)
    disposition-number                        (assoc :case/disposition-number disposition-number)))

(def ^:private case-pull
  [:case/id :case/decedent :case/hours-since-death :case/authorization-verified?
   :case/disposed? :case/jurisdiction :case/status :case/disposition-number])

(defn- pull->case [m]
  (when (:case/id m)
    {:id (:case/id m) :decedent (:case/decedent m) :hours-since-death (:case/hours-since-death m)
     :authorization-verified? (boolean (:case/authorization-verified? m))
     :disposed? (boolean (:case/disposed? m))
     :jurisdiction (:case/jurisdiction m) :status (:case/status m)
     :disposition-number (:case/disposition-number m)}))

(defrecord DatomicStore [conn]
  Store
  (case-record [_ id]
    (pull->case (d/pull (d/db conn) case-pull [:case/id id])))
  (all-cases [_]
    (->> (d/q '[:find [?id ...] :where [?e :case/id ?id]] (d/db conn))
         (map #(pull->case (d/pull (d/db conn) case-pull [:case/id %])))
         (sort-by :id)))
  (authorization-of [_ id]
    (dec* (d/q '[:find ?p . :in $ ?cid
                :where [?k :authorization/case-id ?cid] [?k :authorization/payload ?p]]
              (d/db conn) id)))
  (assessment-of [_ case-id]
    (dec* (d/q '[:find ?p . :in $ ?cid
                :where [?a :assessment/case-id ?cid] [?a :assessment/payload ?p]]
              (d/db conn) case-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (disposition-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :disposition/seq ?s] [?e :disposition/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :sequence/jurisdiction ?j] [?e :sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (case-already-disposed? [s case-id]
    (boolean (:disposed? (case-record s case-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :case/upsert
      (d/transact! conn [(case->tx value)])

      :assessment/set
      (d/transact! conn [{:assessment/case-id (first path) :assessment/payload (enc payload)}])

      :authorization/set
      (d/transact! conn [{:authorization/case-id (first path) :authorization/payload (enc payload)}])

      :case/mark-disposed
      (let [case-id (first path)
            {:keys [result case-patch]} (perform-disposition! s case-id)
            jurisdiction (:jurisdiction (case-record s case-id))
            next-n (inc (next-sequence s jurisdiction))]
        (d/transact! conn
                     [(case->tx (assoc case-patch :id case-id))
                      {:sequence/jurisdiction jurisdiction :sequence/next next-n}
                      {:disposition/seq (count (disposition-history s)) :disposition/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-cases [s cases]
    (when (seq cases) (d/transact! conn (mapv case->tx (vals cases)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:cases ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [cases]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-cases s cases))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo case set -- the Datomic-backed
  analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
