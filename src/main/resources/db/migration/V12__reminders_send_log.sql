-- ============================================================
-- REMINDERS: kutoka "ratiba" kwenda "LOGI YA KUTUMA"
--
-- Jedwali la V1 lilikuwa na (task_id, channel, send_time, status)
-- pekee. Safu hizo zinaeleza kwamba kikumbusho KIPO, lakini haziwezi
-- kujibu swali ambalo scheduler inaliuliza kila tiki:
--
--     "Je, MTU HUYU tayari amepigiwa kuhusu KAZI HII, kwa NJIA HII,
--      SIKU HII?"
--
-- Bila jibu hilo, tiki ya pili (au restart ya app, au kuanzisha
-- instance ya pili) ingemtumia mtu SMS ile ile mara ya pili. Hiyo si
-- kero tu - ni gharama halisi kwa kila ujumbe.
--
-- KWA HIYO safu tatu zinaongezwa, na UNIQUE ndiyo lengo hasa:
--
--   recipient_user_id  - MTU aliyekumbushwa. Bila yeye, "imetumwa"
--                        ingekuwa ya kazi nzima, si ya mtu - na
--                        wapokeaji ni wengi kwa kila kazi.
--   reminder_date      - SIKU inayokumbushwa. Kiolezo cha daily_tasks
--                        kinajirudia kila siku (angalia TaskCompletion),
--                        hivyo bila tarehe rekodi moja ingezuia
--                        vikumbusho vya milele.
--   sent_at            - saa halisi ya kufaulu. `send_time` ya V1 ni
--                        saa ya tiki (nia), si ya kufaulu (matokeo).
--
-- UNIQUE(task_id, reminder_date, recipient_user_id, channel) ndicho
-- KIKWAZO CHA MWISHO. Ukaguzi wowote wa Java ungeweza kupitwa na tiki
-- mbili zinazoendeshwa kwa wakati mmoja; kikwazo cha database
-- hakiwezi. Scheduler inaandika rekodi KWANZA (`INSERT ... ON CONFLICT
-- DO NOTHING`) kisha inatuma - hivyo rekodi ndiyo hati ya kutuma, si
-- kumbukumbu ya baadaye.
--
-- NOT NULL kwa recipient_user_id na reminder_date ni ya LAZIMA, si
-- ukamilifu: PostgreSQL inahesabu NULL kuwa tofauti na NULL kwenye
-- unique index, hivyo safu inayoruhusu NULL ingeacha UNIQUE
-- isiyolinda chochote pale thamani ilipokosekana. Kuziongeza kama NOT
-- NULL kunawezekana kwa sababu jedwali ni TUPU - hakuna kilichokuwa
-- kikiandika `reminders` kabla ya batch hii.
--
-- Safu za V1 HAZIGUSWI (additive pekee), na `channel` inabaki na CHECK
-- yake ya ('PUSH','SMS') - provider (Africa's Talking / Pinpoint) ni
-- undani wa Java, si wa schema.
-- ============================================================

ALTER TABLE reminders
    ADD COLUMN recipient_user_id UUID NOT NULL REFERENCES users(user_id),
    ADD COLUMN reminder_date     DATE NOT NULL,
    ADD COLUMN sent_at           TIMESTAMPTZ;

ALTER TABLE reminders
    ADD CONSTRAINT reminders_task_date_recipient_channel_key
        UNIQUE (task_id, reminder_date, recipient_user_id, channel);

-- Tiki inauliza "nimekwisha kumtumia nani leo?" kwa shamba zima. UNIQUE
-- hapo juu tayari inatoa index inayoanza na task_id; hii ni ya swali la
-- upande mwingine (tarehe kwanza) linalotumiwa na ripoti/usafishaji.
CREATE INDEX idx_reminders_date ON reminders(reminder_date);
