(ns funeral.facts
  "Per-jurisdiction death-care/disposition regulatory catalog -- the
  G2-style spec-basis table the Funeral Services Governor checks every
  jurisdiction/assess proposal against ('did the advisor cite an
  OFFICIAL public source for this jurisdiction's disposition/funeral-
  service requirements, or did it invent one?').

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.

  Seed values are drawn from each jurisdiction's official death-care
  regulator (see `:provenance`); they are a STARTING catalog, not a
  from-scratch survey of all ~194 jurisdictions. Extending coverage is
  additive: add one map to `catalog`, cite a real source, done --
  never invent a jurisdiction's requirements to make coverage look
  bigger.

  The JPN entry's legal basis (墓地、埋葬等に関する法律, the Cemetery
  and Burial Act) is also the source of `funeral.registry/minimum-
  waiting-period-hours` -- Article 3 of that Act is the REAL statutory
  origin of the 24-hour minimum wait between death and cremation/
  burial this actor's distinctive check enforces, not an invented
  figure. Like `clinic.facts`'s/`veterinary.facts`'s USA entries, the
  USA entry here cites a real FEDERAL regulation (the FTC's Funeral
  Rule, 16 CFR Part 453) rather than a state-by-state survey --
  unusually for this fleet, US death-care disclosure requirements
  genuinely ARE federally regulated at this level, so no federation
  caveat is needed for that entry specifically (state-level licensing
  of funeral directors themselves remains federated and out of scope
  for this seed).")

(def catalog
  "iso3 -> requirement map. `:required-evidence` mirrors the generic
  death-certificate/authorization-to-dispose/funeral-director-license-
  verification/arrangement-plan-documentation evidence set submitted in
  some form; `:legal-basis` / `:owner-authority` / `:provenance` are
  the G2 citation the governor requires before any :jurisdiction/
  assess proposal can commit."
  {"JPN" {:name "Japan"
          :owner-authority "厚生労働省 (Ministry of Health, Labour and Welfare, MHLW)"
          :legal-basis "墓地、埋葬等に関する法律 (Cemetery and Burial Act) 第3条"
          :national-spec "死後24時間を経過した後でなければ火葬又は埋葬をしてはならない"
          :provenance "https://www.mhlw.go.jp/"
          :required-evidence ["死亡診断書/死体検案書 (death certificate)"
                              "埋火葬許可証 (authorization-to-dispose documentation)"
                              "葬祭業者資格確認記録 (funeral-director license verification)"
                              "施行計画書 (arrangement-plan documentation)"]}
   "USA" {:name "United States"
          :owner-authority "Federal Trade Commission (FTC)"
          :legal-basis "FTC Funeral Rule (16 CFR Part 453)"
          :national-spec "FTC Funeral Rule disclosure and itemization requirements"
          :provenance "https://www.ftc.gov/legal-library/browse/rules/funeral-rule"
          :required-evidence ["Death certificate"
                              "Authorization-to-dispose documentation"
                              "Funeral-director license verification"
                              "Arrangement-plan documentation"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "Ministry of Justice"
          :legal-basis "Cremation (England and Wales) Regulations 2008"
          :national-spec "Cremation authorization and medical-referee certification requirements"
          :provenance "https://www.gov.uk/government/organisations/ministry-of-justice"
          :required-evidence ["Death certificate"
                              "Authorization-to-dispose documentation"
                              "Funeral-director license verification"
                              "Arrangement-plan documentation"]}
   "DEU" {:name "Germany"
          :owner-authority "Ordnungsbehörden der Bundesländer (state public-order authorities, e.g. Bestattungsgesetz NRW)"
          :legal-basis "Bestattungsgesetze der Länder (state burial acts)"
          :national-spec "Landesrechtliche Bestattungspflicht und Fristenregelungen"
          :provenance "https://www.mags.nrw/bestattungswesen"
          :required-evidence ["Todesbescheinigung (death certificate)"
                              "Bestattungsgenehmigung (authorization-to-dispose documentation)"
                              "Bestatterqualifikationsnachweis (funeral-director license verification)"
                              "Durchführungsplan (arrangement-plan documentation)"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to perform a
  disposition on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-9603 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `funeral.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
