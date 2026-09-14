-- ============================================================
-- GHARAMA ZA UENDESHAJI (costs) + katalogi ya AINA ZA GHARAMA
-- (cost_categories).
--
-- KWA NINI JEDWALI LA V1 LINAJENGWA UPYA, SI KUONGEZWA SAFU.
-- Ni hoja ile ile ya V21 kwa `assets`, na ushahidi ule ule: V1 iliweka
-- `costs` kama kiunzi kisichokamilika, na HAKUNA entity, repository,
-- service wala resolver iliyowahi kuligusa - hakuna njia yoyote kwenye
-- msimbo wote inayoweza kuandika safu hata moja hapa (angalia
-- GAP_ANALYSIS.md, ambapo `costs` imeorodheshwa kati ya majedwali 11
-- yasiyo na entity, na module ya Fedha imewekwa "ABSENT"). Hakuna data
-- ya kuhamisha, hakuna mwandishi wa kuvunja.
--
-- KILICHOKOSEKANA, na kwa nini kila kimoja kinalazimisha ujenzi upya:
--
--   * HAKUNA farm_id KABISA. Kiungo pekee cha shamba kilikuwa cycle_id,
--     yaani kwa NJIA YA MZUNGUKO (cycles -> production_units -> farms).
--     Gharama ya SHAMBA ZIMA - ambayo ndiyo nusu ya module hii, na ndiyo
--     maana cycle_id ni NULLABLE hapa - isingekuwa na shamba hata
--     kimoja: safu yatima isiyoonekana kwenye jumla ya shamba lolote.
--     Hiki peke yake kinatosha.
--   * `category` ilikuwa maandishi huru VARCHAR(50) - inakuwa FK, kwa
--     hoja ile ile ya V16/V21: maandishi huru hayahesabiki kwa kundi.
--   * HAKUNA audit wala soft-delete. V2 iliruka jedwali hili (angalia
--     GAP_ANALYSIS.md), hivyo entity yake isingeweza kurithi BaseEntity
--     bila migration nyingine.
--   * HAKUNA CHECK ya amount > 0.
--
-- ============================================================
-- GHARAMA HAZIHESABIWI MARA MBILI NA CHAKULA - lakini soma hii.
--
-- Fedha ya chakula tayari inarekodiwa mahali pengine: `feed_purchases`
-- (farm_id, quantity_kg, unit_cost, total_cost GENERATED). Jedwali hili
-- HALIGUSI hilo hata kidogo - ni safu tofauti kwenye jedwali tofauti, na
-- hakuna popote kwenye msimbo panapojumlisha vyote viwili (jumla ya
-- kila aina ni kazi ya frontend; angalia CostResolver na AssetResolver).
--
-- Muhimu zaidi: `feeding_logs` inaunganisha chakula na MZUNGUKO kwa
-- KILO pekee - haina safu ya fedha hata moja, na `feed_purchases` haina
-- cycle_id. Yaani gharama ya chakula HAIJAWAHI kuhesabiwa kwa mzunguko
-- popote kwenye mfumo huu. Gharama za hapa ni NYONGEZA juu ya hilo, si
-- nakala yake.
--
-- HATARI ILIYOBAKI ni ya MTUMIAJI, si ya schema: mtu akiunda aina ya
-- gharama iitwayo "Chakula" kisha akaandika manunuzi ya chakula HAPA
-- pia, jumla ya frontend itayahesabu mara mbili. Backend haiwezi
-- kuligundua hilo - majina ya aina yanaundwa na mtumiaji, na
-- kukataza neno "chakula" kungekuwa kubahatisha. Ni jambo la UI
-- kulieleza pale mtu anapounda aina, si la kikwazo cha database.
-- ============================================================

-- ---------- Katalogi ya aina za gharama ----------
-- Umbo ni lile lile la `asset_categories` (V21): jina la kipekee +
-- audit + soft-delete, BILA farm_id. "Umeme", "Mafuta", "Usafiri",
-- "Mishahara" ni maneno ya kibiashara yanayoshirikiwa na mashamba yote
-- ya kampuni - si mali ya shamba moja, kama ilivyo kwa aina za mali,
-- aina za chakula (V16) na aina za samaki (V1).
--
-- `name` ni UNIQUE ya kawaida (SI partial index kama `farms` ya V14),
-- hivyo jina la aina ILIYOFUTWA linabaki limechukuliwa - ndiyo maana
-- CostCategoryRepository inauliza swali la native linalohesabu na
-- zilizofutwa, kama AssetCategoryRepository/SpeciesRepository/
-- FeedTypeRepository.
CREATE TABLE cost_categories (
    cost_category_id SERIAL PRIMARY KEY,
    name             VARCHAR(80) NOT NULL UNIQUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    updated_by UUID REFERENCES users(user_id),
    deleted_by UUID REFERENCES users(user_id)
);

-- ---------- Gharama zenyewe ----------
DROP TABLE costs;

CREATE TABLE costs (
    cost_id          SERIAL PRIMARY KEY,

    -- SHAMBA NI LAZIMA. Kila gharama inatokea MAHALI fulani, na daftari
    -- lisiloweza kujibu "umeme wa shamba lipi?" halina maana. Ni safu
    -- ile ile mpya ya `assets` ya V21, kwa sababu ile ile.
    farm_id          INT NOT NULL REFERENCES farms(farm_id) ON DELETE CASCADE,

    -- MZUNGUKO NI WA HIARI, na hapa ndipo tofauti kubwa na `assets`
    -- ilipo. Safu hii ndiyo inayotofautisha aina MBILI za gharama
    -- zinazoishi kwenye jedwali moja:
    --
    --   NULL      -> gharama ya SHAMBA ZIMA. Umeme wa mwezi, mshahara
    --                wa mlinzi, kodi ya ardhi. Havigawanyiki kwa
    --                mzunguko, na kuvilazimisha kwenye mmojawapo
    --                kungepandisha gharama ya mzunguko huo kwa kiasi
    --                ambacho si chake.
    --   IMEWEKWA  -> gharama ya MZUNGUKO HUO. Dawa ya bwawa fulani,
    --                usafiri wa kupeleka mavuno ya mzunguko fulani.
    --
    -- Ndiyo maana si NOT NULL: nusu ya gharama halisi za shamba hazina
    -- mzunguko wa kuzibebesha, na jedwali la pili kwa ajili yao
    -- lingegawanya swali moja ("tumetumia kiasi gani?") kwenye maswali
    -- mawili yasiyokutana kamwe.
    --
    -- Uthibitisho kwamba mzunguko huu ni WA SHAMBA HILI unafanywa
    -- CostService.createCost (`cycles` haina farm_id, hivyo kikwazo cha
    -- database hakiwezi kuisema sheria hiyo).
    cycle_id         INT REFERENCES cycles(cycle_id) ON DELETE CASCADE,

    -- HAKUNA CASCADE kwa makusudi, kama V21: aina inayotumiwa na
    -- gharama zilizorekodiwa haipaswi kutoweka pamoja nazo. Kufuta aina
    -- ni soft-delete, na FK inabaki ikielekea safu iliyopo.
    cost_category_id INT NOT NULL REFERENCES cost_categories(cost_category_id),

    -- NUMERIC(14,2) ni ile ile ya `assets.cost`, `feed_purchases.
    -- total_cost` na `costs.amount` ya V1 - fedha ni fedha kote kwenye
    -- schema hii.
    amount           NUMERIC(14,2) NOT NULL,

    cost_date        DATE NOT NULL,

    -- Maelezo ya hiari: "LUKU ya Machi", "matengenezo ya pampu".
    -- Inabaki TEXT kama V1.
    description      TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    updated_by UUID REFERENCES users(user_id),
    deleted_by UUID REFERENCES users(user_id),

    -- Sheria ile ile inatekelezwa CostService kwa ujumbe unaosomeka;
    -- hii ni ngome ya mwisho kwa njia yoyote inayopita kando ya service
    -- (kama chk_assets_cost_positive ya V21). Gharama ya sifuri si
    -- gharama - ni safu inayoharibu jumla.
    CONSTRAINT chk_costs_amount_positive CHECK (amount > 0)
);

-- Daftari linasomwa kwa njia MOJA: "gharama za mashamba haya, mpya
-- kwanza" (angalia CostRepository). Index inafuata mpangilio huo, kama
-- idx_assets_farm_acquired ya V21.
CREATE INDEX idx_costs_farm_date ON costs(farm_id, cost_date DESC);

-- Jina lile lile la index ya V1 (ilitoweka pamoja na jedwali lake):
-- gharama za mzunguko mmoja ni swali la ripoti ya mzunguko.
CREATE INDEX idx_costs_cycle ON costs(cycle_id);

CREATE INDEX idx_costs_category ON costs(cost_category_id);
