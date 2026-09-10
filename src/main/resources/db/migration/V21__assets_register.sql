-- ============================================================
-- DAFTARI LA MALI (assets) + katalogi ya AINA ZA MALI
-- (asset_categories).
--
-- KWA NINI JEDWALI LA V1 LINAJENGWA UPYA, SI KUONGEZWA SAFU.
-- V1 iliweka `assets` kama kiunzi kisichokamilika: farm_id ilikuwa
-- NULLABLE, `category` ilikuwa maandishi huru (VARCHAR(80)), `value` na
-- `purchase_date` zilikuwa za hiari, na jedwali zima liliachwa nje ya V2
-- (hivyo halina audit wala soft-delete). Hakuna entity, repository,
-- service wala resolver iliyowahi kuligusa - HAKUNA njia yoyote kwenye
-- msimbo wote inayoweza kuandika safu hata moja hapa (angalia
-- docs/REPO_AUDIT.md, ambapo `assets` imeorodheshwa kama jedwali
-- yatima).
--
-- Kwa hivyo hii ni migration ya WAKATI WA UJENZI, kama V16: hakuna data
-- ya kuhamisha, na kubadilisha safu sita moja baada ya nyingine
-- kungeacha historia ndefu isiyoeleza kitu. Jedwali linajengwa upya kwa
-- umbo lililokusudiwa tangu mwanzo. Jina linabaki `assets`: kuunda
-- jedwali la pili lenye maana ile ile pembeni mwa hili lililokufa
-- kungekuwa mbaya zaidi kuliko chaguo lolote kati ya haya mawili.
--
-- KILICHOONDOKA na kwa nini:
--   * `status` - haikuwa na mtumiaji yeyote. Mali iliyoachwa kutumika
--     inashughulikiwa na soft-delete ya BaseEntity, kama kila kitu
--     kingine kwenye repo hii.
--   * `category` (maandishi) - inakuwa FK. Angalia hoja hapo chini.
--
-- KATALOGI NI YA KIMFUMO (haina farm_id), kama `species` (V1) na
-- `feed_types` (V16). "Jengo", "Gari", "Pampu" si mali ya shamba moja -
-- ni maneno ya kibiashara yanayoshirikiwa na mashamba yote ya kampuni.
-- Ndiyo maana hakuna orodha ya kudumu iliyoandikwa hapa: mkulima mwenye
-- "vitu vingi" asivyovitabirika anaunda aina anazozihitaji mwenyewe,
-- kama anavyounda aina ya chakula au ya samaki.
--
-- MALI YENYEWE INA farm_id NOT NULL - hapa ndipo tofauti ilipo. Kila
-- kitu kinachomilikiwa kiko MAHALI fulani, na daftari lisiloweza kujibu
-- "generator iko shamba lipi?" halina maana. NOT NULL (V1 iliruhusu
-- NULL) kwa sababu hiyo hiyo: mali isiyo na shamba haingeonekana kwenye
-- jumla ya shamba lolote, na ingepotea kimyakimya kwenye ripoti.
-- ============================================================

-- ---------- Katalogi ya aina za mali ----------
-- Umbo ni lile lile la feed_types (V16) bila safu za umri: jina la
-- kipekee + audit + soft-delete. `name` ni UNIQUE ya kawaida (SI partial
-- index kama farms ya V14), hivyo jina la aina ILIYOFUTWA linabaki
-- limechukuliwa - ndiyo maana AssetService inauliza swali la native
-- linalohesabu na zilizofutwa, kama SpeciesRepository/FeedTypeRepository.
CREATE TABLE asset_categories (
    asset_category_id SERIAL PRIMARY KEY,
    name              VARCHAR(80) NOT NULL UNIQUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    updated_by UUID REFERENCES users(user_id),
    deleted_by UUID REFERENCES users(user_id)
);

-- ---------- Daftari la mali ----------
DROP TABLE assets;

CREATE TABLE assets (
    asset_id          SERIAL PRIMARY KEY,

    -- Kila mali ni ya shamba MOJA. CASCADE inafuata mtindo wa V1 kwa kila
    -- kitu kinachoelekea farms.
    farm_id           INT NOT NULL REFERENCES farms(farm_id) ON DELETE CASCADE,

    -- HAKUNA CASCADE hapa kwa makusudi: aina inayotumiwa na mali haipaswi
    -- kutoweka pamoja na mali hizo. Kufuta aina ni soft-delete (hakuna
    -- mutation yake bado), na FK inabaki ikielekea safu iliyopo.
    asset_category_id INT NOT NULL REFERENCES asset_categories(asset_category_id),

    name              VARCHAR(150) NOT NULL,

    -- Bei ya kununulia. NUMERIC(14,2) ni ile ile ya `costs.amount` na
    -- `feed_purchases.total_cost` - fedha ni fedha kote kwenye schema hii.
    cost              NUMERIC(14,2) NOT NULL,

    -- Maandishi huru ya HIARI: "5000L", "ekari 2", "20HP". Ni lebo ya
    -- kusoma, si kipimo cha kukokotoa - ndiyo maana si NUMERIC pamoja na
    -- kizio. Vitu vinavyoingia kwenye daftari hili havishiriki kipimo
    -- kimoja, hivyo safu ya namba ingelazimisha kila kitu kwenye kizio
    -- kisichokifaa.
    size_label        VARCHAR(80),

    acquired_date     DATE NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    updated_by UUID REFERENCES users(user_id),
    deleted_by UUID REFERENCES users(user_id),

    -- Sheria ile ile inatekelezwa AssetService.createAsset kwa ujumbe
    -- unaosomeka; hii ni ngome ya mwisho kwa njia yoyote inayopita kando
    -- ya service (kama chk_feed_types_age_range ya V16). Mali ya bei
    -- sifuri si mali - ni safu inayoharibu jumla ya shamba.
    CONSTRAINT chk_assets_cost_positive CHECK (cost > 0)
);

-- Daftari linasomwa kwa njia MOJA: "mali za mashamba haya, mpya kwanza"
-- (angalia AssetRepository.findByFarm_FarmIdInOrderByAcquiredDateDesc).
-- Index inafuata mpangilio huo.
CREATE INDEX idx_assets_farm_acquired ON assets(farm_id, acquired_date DESC);
CREATE INDEX idx_assets_category ON assets(asset_category_id);
