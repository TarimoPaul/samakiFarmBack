-- ============================================================
-- Jina la shamba ni la kipekee KWA MASHAMBA YALIYOPO.
--
-- Hii ndiyo hatua V9 iliyoiahidi kwa maneno yake yenyewe:
--
--   "UNIQUE ya kawaida, si partial index ya `WHERE is_deleted = false`:
--    farms ina safu za soft-delete (V2) lakini HAKUNA endpoint ya kufuta
--    shamba... Endpoint ya kufuta ikija, hapo ndipo hii igeuzwe kuwa
--    partial index."
--
-- Endpoint hiyo (DELETE /api/farms/{farmId}) imefika, hivyo kikwazo
-- kinabadilishwa sasa. Bila mabadiliko haya, kufuta "Shamba la Mbeya" na
-- kisha kuunda jipya kwa jina lilelile kungekataliwa milele - na
-- kingekataliwa na safu ambayo HAIONEKANI popote kwenye mfumo, hivyo
-- msimamizi asingeweza kabisa kujua kwa nini.
--
-- Baada ya hapa: majina ya mashamba YALIYOPO hayajirudii, na jina la
-- shamba lililofutwa linaachiwa huru kutumika tena.
-- ============================================================

ALTER TABLE farms DROP CONSTRAINT IF EXISTS uq_farms_name;

CREATE UNIQUE INDEX IF NOT EXISTS uq_farms_name_active
    ON farms (name)
    WHERE is_deleted = false;
