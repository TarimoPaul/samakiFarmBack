-- ============================================================
-- Water Quality: safu ya AMONIA
--
-- Ni safu ya DATA mpya - haikuwepo kwenye V1, ERD, wala Data Dictionary.
-- Inaongezwa kwa uamuzi wa mwenye schema, si kwa kubuni: amonia ndicho
-- chanzo kikuu cha VIFO VYA GHAFLA vya samaki (Moduli 4 ya kozi), hivyo
-- kipimo cha ubora wa maji kisicho na amonia hakiwezi kueleza sababu ya
-- tukio ambalo module hii ipo kwa ajili yake.
--
-- V11 tofauti na kuhariri V10: V10 ilikwisha kutumika (checksum yake
-- imekwisha andikwa kwenye flyway_schema_history ya database ya dev) na
-- imekwisha push. Kuihariri kungehitaji `flyway repair` na force-push;
-- migration ya nyongeza haihitaji lolote kati ya hayo.
--
-- NUMERIC(4,2), si (4,1) kama vipimo vingine: amonia inahukumiwa kwenye
-- desimali mbili. 0.02 mg/L ni salama, 0.25 mg/L inaua polepole - kwa
-- desimali moja tofauti hiyo ingepotea kabisa. Ukomo 99.99 mg/L ni
-- uwezo wa safu, si maoni kuhusu kipimo kizuri.
--
-- Klorini HAIJAONGEZWI: ni suala la maji YANAYOINGIA (kabla ya kujaza),
-- si kipimo cha kila siku cha tanki lenye samaki - halina nafasi kwenye
-- jedwali hili.
-- ============================================================

ALTER TABLE water_quality_logs
    ADD COLUMN ammonia NUMERIC(4,2);

COMMENT ON COLUMN water_quality_logs.ammonia IS
    'Amonia jumla (NH3 + NH4+), mg/L. Nullable: si kila kipimo kina kifaa cha amonia.';
