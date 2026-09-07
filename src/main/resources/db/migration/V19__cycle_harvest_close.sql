-- ============================================================
-- Kufunga mzunguko: umri wa kuweka, data ya mavuno, na kiwango cha
-- kuishi KILICHOTOKEA.
--
-- Mzunguko ulikuwa na mwanzo bila mwisho: `status` ilikuwa na 'HARVESTED'
-- na 'FAILED' tangu V1, lakini hakukuwa na njia yoyote ya kuzifikia -
-- CycleService iliandika 'ACTIVE' pekee. Kila mzunguko uliowahi kuwekwa
-- ulibaki ukiendelea milele: ukiendelea kuzalisha vikumbusho vya kulisha
-- samaki waliokwisha vunwa (DailyTaskRepository.findOutstandingForFarm
-- inachuja kwa `cycle.status = 'ACTIVE'`), na kuacha tanki likionekana
-- limekaliwa.
--
-- =====================================================================
-- 1. stocking_age_months - UMRI WA SAMAKI SIKU WALIPOWEKWA
--
-- expected_harvest_date ilikuwa `stocking_date + growth_months_avg`,
-- hesabu inayodhania kwamba KILA mzunguko unaanza na vifaranga vya siku
-- ya kwanza. Mkulima anayenunua samaki wa miezi 2 na kuwaweka ana miezi
-- 4 ya kusubiri, si 6 - utabiri ulikuwa unamchelewesha miezi 2, kila
-- mzunguko, kimyakimya.
--
-- DEFAULT 0 ndiyo inayofanya safu hii isiwe na madhara kwa data iliyopo:
-- mizunguko yote ya zamani inabaki ikimaanisha kile ile ilichokuwa
-- ikimaanisha - "waliwekwa wakiwa wadogo kabisa" - na hesabu yao
-- HAIBADILIKI hata kidogo.
--
-- 2. harvested_count / total_weight_kg / harvest_notes - MAVUNO YENYEWE
--
-- NULL kwa mzunguko unaoendelea; zinajazwa na closeCycle pekee.
--
-- 3. actual_survival_rate - GENERATED, NA NDIYO JAMBO ZIMA
--
-- Kiwango cha kuishi KILICHOTOKEA si maoni ya mtu - ni mgawanyo wa namba
-- mbili zilizokwisha rekodiwa. Kikiwa safu ya kawaida, mtu angeweza
-- kukiandika: fomu ingekuwa na uga wake, au mutation ingeukubali, na
-- shamba lingeweza kuripoti kuishi kwa 95% likiwa limevuna nusu ya
-- lililoweka.
--
-- GENERATED ALWAYS ... STORED inaifanya hali hiyo ISIWEZEKANE kwenye
-- NGAZI YA DATABASE, si kwenye ngazi ya service pekee: Postgres
-- INAKATAA kila INSERT au UPDATE inayoigusa safu hii, hata ikitoka
-- kwenye psql. Ni mtindo ule ule wa `feed_purchases.total_cost` (V1),
-- kwa sababu ile ile.
--
-- CASE inashughulikia hali mbili halali: mzunguko ambao bado
-- haujavunwa (harvested_count IS NULL) unabaki NULL - "hakijulikani
-- bado", si sifuri - na fingerlings_count = 0 haigawanyi kwa sifuri.
--
-- NUMERIC(6,4): kuishi ni uwiano (0.8500), na tarakimu mbili kabla ya
-- nukta zinaruhusu kuhesabu upya kunakozidi kilichowekwa - kuhesabu
-- samaki si sayansi kamili, na kikwazo kingegeuza kosa la kuhesabu kuwa
-- kosa la database.
-- =====================================================================

ALTER TABLE cycles
    ADD COLUMN stocking_age_months INT NOT NULL DEFAULT 0,
    ADD COLUMN harvested_count     INT,
    ADD COLUMN total_weight_kg     NUMERIC(10,2),
    ADD COLUMN harvest_notes       TEXT;

ALTER TABLE cycles
    ADD COLUMN actual_survival_rate NUMERIC(6,4)
        GENERATED ALWAYS AS (
            CASE
                WHEN harvested_count IS NULL OR fingerlings_count IS NULL
                     OR fingerlings_count = 0
                THEN NULL
                ELSE harvested_count::numeric / fingerlings_count
            END
        ) STORED;

-- Vikwazo vya thamani zisizo na maana. Ukaguzi upo pia kwenye
-- CycleService (ndipo ujumbe wa Kiswahili unapotoka), lakini database
-- ndiyo inayohakikisha kwamba hakuna njia NYINGINE - seed, script ya
-- kurekebisha, au psql - inayoweza kuingiza umri hasi au mavuno hasi.
ALTER TABLE cycles
    ADD CONSTRAINT cycles_stocking_age_months_non_negative
        CHECK (stocking_age_months >= 0),
    ADD CONSTRAINT cycles_harvested_count_non_negative
        CHECK (harvested_count IS NULL OR harvested_count >= 0),
    ADD CONSTRAINT cycles_total_weight_kg_non_negative
        CHECK (total_weight_kg IS NULL OR total_weight_kg >= 0);
