-- ============================================================
-- MAVUNO NI MATUKIO MENGI, SI NAMBA MOJA YA SIKU YA KUFUNGA.
--
-- V19 ilidhani mavuno yanatokea MARA MOJA: closeCycle ilipokea
-- harvested_count na total_weight_kg kutoka kwa mwombaji, na mzunguko
-- ukafungwa. Majaribio ya shambani (pilot) yameonyesha kwamba si hivyo:
-- samaki wanatoka kwenye bwawa KILA SIKU kwa wiki kadhaa - wanauzwa kwa
-- wateja tofauti, wanakufa, wanahamishwa - na namba ya siku ya kufunga
-- ilikuwa ikijumlishwa kwa mkono kwenye daftari la karatasi, kisha
-- kuandikwa hapa. Mfumo ulikuwa ukihifadhi JUMLA ya mtu, si matukio
-- yenyewe.
--
-- Sasa kila tukio linarekodiwa (harvest_events), na closeCycle
-- INAJUMLISHA matukio hayo yenyewe - haipokei idadi wala uzito tena.
--
-- =====================================================================
-- 1. harvest_events
--
-- reason: SOLD / DIED / REMOVED - tatu zilizotajwa na pilot, HAKUNA
-- nyingine. Enum ya Java (HarvestEvent.Reason) ndiyo inayokataa thamani
-- nyingine kwa ujumbe unaosomeka; CHECK hapa ni ngome ya mwisho, kama
-- production_units.type ya V1.
--
-- KIWANGO CHA KUISHI kinahesabu SOLD + REMOVED pekee: samaki
-- WALIOTOKA WAKIWA HAI. DIED ni vifo - vinajumlishwa kando
-- (cycles.mortality_count). Samaki aliyekufa bwawani ametoka, ndiyo,
-- lakini hakuishi; kumhesabu kungefanya bwawa lenye vifo vingi lionekane
-- limefanikiwa.
--
-- weight_kg: LAZIMA (> 0) kwa SOLD - samaki anauzwa kwa kilo. HIARI kwa
-- DIED/REMOVED: mkulima haipimi mizoga, na kulazimisha namba kungezaa
-- namba za kubuni. Ikitolewa, lazima iwe > 0 (sifuri si kipimo).
--
-- sale_amount: fedha ya mauzo - kwa SOLD PEKEE, na LAZIMA > 0 hapo.
-- Kwa DIED/REMOVED ni NULL, kila mara: mzoga hauna mapato, na safu ya
-- mapato isiyo tupu kwenye kifo ingeingia kwenye jumla ya mapato.
--
-- HAKUNA farm_id: cycle_id ni NOT NULL, hivyo shamba linafikiwa
-- kupitia cycles -> production_units -> farms bila utata. (Tofauti na
-- `costs` ya V23, ambapo mzunguko ni wa hiari na farm_id ndiyo kiungo
-- pekee cha gharama ya shamba zima.)
--
-- 2. DROP TABLE sales
--
-- Ni hoja ile ile ya V21 (`assets`) na V23 (`costs`): V1 iliweka
-- `sales` kama kiunzi, na HAKUNA entity, repository, service, resolver
-- wala schema iliyowahi kuligusa - hakuna mwandishi wa kuvunja, hakuna
-- data ya kuhamisha. Na halingefaa hata likibaki: kila safu ni mauzo
-- (hakuna idadi ya samaki, hakuna vifo), fedha yake ni kg * bei
-- (GENERATED) badala ya kiasi kilichopokelewa, na V2 ililiruka (hakuna
-- audit/soft-delete). Likibaki pembeni ya harvest_events, fedha ya
-- mauzo ingekuwa na NYUMBA MBILI.
--
-- `customers` HAIGUSWI kwa makusudi: haitumiki leo, lakini inabaki kwa
-- ufuatiliaji wa wateja wa baadaye. FK pekee iliyoielekea ilikuwa ya
-- `sales`, na inaondoka pamoja nalo.
--
-- 3. Safu mpya za cycles
--
-- fingerling_cost - gharama ya vifaranga siku ya kuweka. HIARI (NULL =
--   haikurekodiwa), na ikitolewa > 0. Iko HAPA, si kwenye `costs`: ni
--   gharama ya mzunguko huu hasa, inayojulikana siku ya kuweka.
-- mortality_count - jumla ya DIED, inayoandikwa na closeCycle pekee.
-- total_revenue   - jumla ya sale_amount ya SOLD, na closeCycle pekee.
--
-- Zote mbili za mwisho ni NULL kwa mzunguko unaoendelea - "haijulikani
-- bado", si sifuri - kama harvested_count ya V19.
--
-- actual_survival_rate (V19) HAIBADILIKI hata kidogo: bado ni
-- harvested_count / fingerlings_count. Kilichobadilika ni MWANDISHI wa
-- harvested_count: sasa ni jumla ya SOLD + REMOVED inayokokotolewa na
-- server siku ya kufunga, si namba ya mwombaji. (Postgres haiwezi
-- kufanya GENERATED inayosoma jedwali jingine, hivyo jumla inaandikwa
-- kwenye mstari wa mzunguko, na GENERATED inaendelea kuigawanya.)
--
-- HAKUNA BACKFILL: mizunguko iliyokwisha fungwa inabaki na namba zake za
-- V19 zilizoandikwa kwa mkono; mortality_count na total_revenue zao ni
-- NULL, kwa sababu hazikuwahi kurekodiwa - kuzibuni sasa kungekuwa
-- uongo unaoonekana kama takwimu.
-- =====================================================================

DROP TABLE sales;

CREATE TABLE harvest_events (
    harvest_event_id SERIAL PRIMARY KEY,

    cycle_id    INT NOT NULL REFERENCES cycles(cycle_id) ON DELETE CASCADE,
    event_date  DATE NOT NULL,
    fish_count  INT NOT NULL,
    weight_kg   NUMERIC(10,2),
    reason      VARCHAR(10) NOT NULL,

    -- NUMERIC(14,2) - fedha ni fedha kote kwenye schema hii (costs.amount,
    -- assets.cost, feed_purchases.total_cost).
    sale_amount NUMERIC(14,2),

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    updated_by UUID REFERENCES users(user_id),
    deleted_by UUID REFERENCES users(user_id),

    -- Sheria zote hizi zinatekelezwa HarvestService kwa ujumbe
    -- unaosomeka; hizi ni ngome ya mwisho kwa njia yoyote inayopita kando
    -- ya service (seed, script, psql).
    CONSTRAINT chk_harvest_events_reason
        CHECK (reason IN ('SOLD', 'DIED', 'REMOVED')),
    CONSTRAINT chk_harvest_events_fish_count_positive
        CHECK (fish_count > 0),
    CONSTRAINT chk_harvest_events_weight_positive
        CHECK (weight_kg IS NULL OR weight_kg > 0),
    CONSTRAINT chk_harvest_events_sold_has_weight
        CHECK (reason <> 'SOLD' OR weight_kg IS NOT NULL),
    CONSTRAINT chk_harvest_events_sale_amount
        CHECK ((reason = 'SOLD' AND sale_amount IS NOT NULL AND sale_amount > 0)
            OR (reason <> 'SOLD' AND sale_amount IS NULL))
);

-- Matukio yanasomwa kwa njia MOJA: "ya mzunguko huu, mapya kwanza" -
-- orodha ya skrini NA jumla ya closeCycle.
CREATE INDEX idx_harvest_events_cycle_date ON harvest_events(cycle_id, event_date DESC);

ALTER TABLE cycles
    ADD COLUMN fingerling_cost NUMERIC(14,2),
    ADD COLUMN mortality_count INT,
    ADD COLUMN total_revenue   NUMERIC(14,2);

ALTER TABLE cycles
    ADD CONSTRAINT cycles_fingerling_cost_positive
        CHECK (fingerling_cost IS NULL OR fingerling_cost > 0),
    ADD CONSTRAINT cycles_mortality_count_non_negative
        CHECK (mortality_count IS NULL OR mortality_count >= 0),
    ADD CONSTRAINT cycles_total_revenue_non_negative
        CHECK (total_revenue IS NULL OR total_revenue >= 0);
