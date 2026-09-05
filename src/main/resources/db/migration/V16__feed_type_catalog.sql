-- ============================================================
-- Katalogi ya AINA ZA CHAKULA (feed_types), na kuunganisha module ya
-- chakula nayo.
--
-- KWA NINI. V1 iliweka `feed_type` kama maandishi huru (VARCHAR(80)) kwenye
-- feed_purchases na feeding_logs. Maandishi huru hayawezi kujibu swali la
-- msingi la ulishaji: "chakula hiki ni cha samaki wa umri gani?".
-- "Pellet 3mm", "pellet 3 mm" na "PELLET3MM" ni vitu vitatu tofauti kwa
-- database na kitu kimoja kwa mkulima - hivyo salio la stoo halikuwa
-- linahesabika kwa aina, na hakuna sehemu ya kuhifadhi [min, max] ya umri.
--
-- Katalogi ni YA KIMFUMO (haina farm_id), kama `species` ya V1: aina ya
-- chakula ni ukweli wa kibiashara unaoshirikiwa na mashamba yote, si mali
-- ya shamba moja.
--
-- HAKUNA BACKFILL, na hii ni migration ya wakati wa ujenzi: hakuna data ya
-- ununuzi wala ulishaji popote. Safu ya zamani inaondolewa moja kwa moja
-- badala ya kuhamishwa, na feed_type_id inaingia ikiwa NOT NULL tangu
-- mwanzo - kitu ambacho kisingewezekana kwenye jedwali lenye rekodi.
-- ============================================================

CREATE TABLE feed_types (
    feed_type_id   SERIAL PRIMARY KEY,
    name           VARCHAR(80) NOT NULL UNIQUE,

    -- Dirisha la umri ambalo chakula hiki kimetengenezwa kwa ajili yake,
    -- kwa miezi, PANDE ZOTE MBILI ZIKIHUSISHWA: [min, max]. Aina ya
    -- vifaranga ni [0, 0] - mwezi wa kwanza pekee.
    min_age_months INT NOT NULL,
    max_age_months INT NOT NULL,

    -- Aina inayoachwa kutumika haifutwi (rekodi za zamani zinaielekea);
    -- inazimwa. FeedService inasoma zinazotumika pekee.
    active         BOOLEAN NOT NULL DEFAULT true,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    updated_by UUID REFERENCES users(user_id),
    deleted_by UUID REFERENCES users(user_id),

    -- Dirisha lililopinduka ([12, 6]) lingefanya kila samaki asifae. Sheria
    -- ile ile inatekelezwa FeedService.createFeedType kwa ujumbe unaosomeka;
    -- hii ni ngome ya mwisho kwa njia yoyote inayopita kando ya service.
    CONSTRAINT chk_feed_types_age_range  CHECK (max_age_months >= min_age_months),
    CONSTRAINT chk_feed_types_age_nonneg CHECK (min_age_months >= 0)
);

-- ---------- feed_purchases: maandishi -> FK ----------
-- Ilikuwa NOT NULL kama maandishi; inabaki NOT NULL kama FK.
ALTER TABLE feed_purchases DROP COLUMN feed_type;
ALTER TABLE feed_purchases
    ADD COLUMN feed_type_id INT NOT NULL REFERENCES feed_types(feed_type_id);

-- ---------- feeding_logs: maandishi (NULLABLE) -> FK (NOT NULL) ----------
-- KUKAZWA KWA MAKUSUDI. V1 iliruhusu ulishaji usiotaja chakula. Rekodi
-- kama hiyo haiwezi kupunguza stoo ya aina yoyote, hivyo salio la kila
-- aina lingekuwa uongo mara ya kwanza mtu anapoacha safu hiyo wazi.
-- Kulisha KUNA chakula kilichotumika - kukisema ni sharti, si hiari.
ALTER TABLE feeding_logs DROP COLUMN feed_type;
ALTER TABLE feeding_logs
    ADD COLUMN feed_type_id INT NOT NULL REFERENCES feed_types(feed_type_id);

-- ---------- feed_stock_movements: salio kwa AINA ----------
-- Leja ilihesabu kilo bila kujali ni chakula gani, hivyo kilo 50 za
-- vifaranga na kilo 50 zilizoliwa za wakubwa zilighairiana hadi 0 - stoo
-- iliyojaa ikionekana tupu. NOT NULL: kila movement inazalishwa na ununuzi
-- au ulishaji, na vyote viwili sasa vina aina.
ALTER TABLE feed_stock_movements
    ADD COLUMN feed_type_id INT NOT NULL REFERENCES feed_types(feed_type_id);

CREATE INDEX idx_feed_purchases_feed_type ON feed_purchases(feed_type_id);
CREATE INDEX idx_feeding_logs_feed_type   ON feeding_logs(feed_type_id);
-- Salio linaulizwa kama "shamba hili, kwa kila aina" - index inafuata
-- mpangilio huo hasa (angalia FeedStockMovementRepository.sumBalanceByFarmId).
CREATE INDEX idx_feed_stock_movements_farm_type
    ON feed_stock_movements(farm_id, feed_type_id);
