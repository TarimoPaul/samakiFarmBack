-- ============================================================
-- `manage_assets` inahamia module ya FINANCE.
--
-- HAKUNA RUHUSA INAYOBADILIKA. Msimbo, ugawaji wa roles na ulinzi wa
-- endpoints vyote vinabaki vile vile: `module` na `group_name` vina kazi
-- MOJA tu - kupanga visanduku kwenye skrini ya Nafasi na Ruhusa
-- (angalia maoni ya Permission entity). Hii ni ya UWASILISHAJI pekee.
--
-- KWA NINI. V22 iliweka daftari la mali kwenye FARM/ASSETS, lakini maoni
-- yake yenyewe yanasema daftari ni "jibu la swali la KIMTAJI: kampuni
-- imewekeza kiasi gani, na wapi?" - na swali la mtaji ni la fedha, si la
-- shamba. Zaidi ya hapo daftari ni la KAMPUNI nzima (mali za mashamba
-- yote ambayo mwombaji ni mwanachama wake), tofauti na kila kitu kingine
-- kwenye FARM ambacho hupunguzwa kwa shamba teule.
--
-- Kilichochochea mabadiliko ni sidebar: mali na gharama za uendeshaji
-- zinawekwa pamoja chini ya "Fedha" kwenye urambazaji. Zikiachwa
-- zikitofautiana, skrini ya Nafasi na Ruhusa ingeonyesha mali chini ya
-- "Shamba" wakati sidebar inaonyesha chini ya "Fedha" - taxonomia mbili
-- zinazopingana kwa kitu kile kile.
--
-- `group_name` INABAKI 'ASSETS': mali si gharama, na kuziunganisha
-- kungeficha kwamba ni madaftari mawili tofauti yenye ruhusa mbili
-- tofauti (`manage_assets` dhidi ya `manage_costs`).
--
-- seed/permissions.csv imesahihishwa pia, kwa ajili ya DB mpya kabisa
-- ambazo hazipitii migration hii.
-- ============================================================

UPDATE permissions
SET module = 'FINANCE'
WHERE code = 'manage_assets';
