package com.samaki.farm.asset;

import com.fasterxml.jackson.databind.JsonNode;
import com.samaki.farm.asset.entity.Asset;
import com.samaki.farm.asset.entity.AssetCategory;
import com.samaki.farm.asset.repository.AssetCategoryRepository;
import com.samaki.farm.asset.repository.AssetRepository;
import com.samaki.farm.auth.security.JwtAuthFilter;
import com.samaki.farm.farm.entity.Farm;
import com.samaki.farm.farm.repository.FarmRepository;
import com.samaki.farm.farmuser.entity.FarmUser;
import com.samaki.farm.farmuser.repository.FarmUserRepository;
import com.samaki.farm.rbac.repository.RoleRepository;
import com.samaki.farm.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DAFTARI LA MALI - module ya kwanza inayovuka mashamba.
 *
 * =====================================================================
 * KWA NINI MAJARIBIO HAYA YANAONEKANA TOFAUTI NA MENGINE YA REPO
 *
 * Kila module nyingine inajaribiwa kwa swali "je, shamba A linaona data
 * ya shamba B?" ambalo jibu lake sahihi ni HAPANA kila mara. Hapa jibu
 * ni NDIYO KWA MASHARTI, na masharti ndiyo yote:
 *
 *   * NDIYO ikiwa mwombaji ni mwanachama (au mmiliki) wa mashamba yote
 *     mawili - ndiyo hoja nzima ya module: mmiliki ana orodha MOJA.
 *   * HAPANA kwa shamba lolote asilo mwanachama wake - ni sheria ile ile
 *     ya D-1, ikitekelezwa kwa uanachama halisi badala ya farmId moja ya
 *     principal.
 *
 * Majaribio mawili ya kwanza ya {@link Kampuni} ni pande hizo mbili za
 * sarafu ile ile, na hakuna lenye maana bila lenzake.
 *
 * KIKOMO KINACHOJULIKANA cha auth kinaonekana hapa: JwtAuthFilter
 * inampa mtumiaji ruhusa za uanachama wa KWANZA pekee. Ndiyo maana
 * jaribio la mashamba mengi linamweka admin (tayari OWNER wa shamba A,
 * lenye id ndogo) kuwa mwanachama wa B - mpangilio mwingine
 * ungembadilishia ruhusa zake kimyakimya.
 * =====================================================================
 */
@DisplayName("Daftari la mali")
class AssetRegisterTest extends IntegrationTest {

    private static final String ASSETS_QUERY = """
            query { assets { assetId name cost sizeLabel acquiredDate \
            farm { farmId name } assetCategory { assetCategoryId name } } }""";

    private static final String CATEGORIES_QUERY =
            "query { assetCategories { assetCategoryId name } }";

    private static final String MY_FARMS_QUERY = "query { myFarms { farmId name } }";

    /** Kanda ile ile AssetService inayoitumia kufafanua "leo". */
    private static final ZoneId EAT = ZoneId.of("Africa/Nairobi");

    @Autowired private AssetRepository assetRepository;
    @Autowired private AssetCategoryRepository assetCategoryRepository;
    @Autowired private FarmRepository farms;
    @Autowired private FarmUserRepository farmUsers;
    @Autowired private RoleRepository roles;

    // --------------------------------------------------------- misaada

    private int category(String name) {
        JsonNode res = graphql(adminToken,
                "mutation { createAssetCategory(name: \"" + name + "\") { assetCategoryId name } }");
        return res.path("data").path("createAssetCategory").path("assetCategoryId").asInt();
    }

    private String createMutation(String name, int farmId, String cost, String acquiredDate,
                                   String sizeLabel, int categoryId) {
        return "mutation { createAsset(name: \"" + name + "\", farmId: " + farmId
                + ", cost: " + cost + ", acquiredDate: \"" + acquiredDate + "\""
                + (sizeLabel == null ? "" : ", sizeLabel: \"" + sizeLabel + "\"")
                + ", assetCategoryId: " + categoryId
                + ") { assetId name cost sizeLabel acquiredDate "
                + "farm { farmId name } assetCategory { assetCategoryId name } } }";
    }

    private JsonNode register(String token) {
        return graphql(token, ASSETS_QUERY).path("data").path("assets");
    }

    private JsonNode rowNamed(String token, String name) {
        for (JsonNode row : register(token)) {
            if (name.equals(row.path("name").asText())) {
                return row;
            }
        }
        return null;
    }

    private String today() {
        return LocalDate.now(EAT).toString();
    }

    /** Majina ya mashamba ya `myFarms`, kwa mpangilio yalivyorudi. */
    private List<String> myFarmNames(String token) {
        List<String> names = new ArrayList<>();
        for (JsonNode row : graphql(token, MY_FARMS_QUERY).path("data").path("myFarms")) {
            names.add(row.path("name").asText());
        }
        return names;
    }

    /**
     * Kufuta shamba MOJA KWA MOJA kwenye database.
     *
     * FarmService.delete ingekataa: shamba B lina wanachama (angalia
     * DevSeedService), na kikwazo hicho si kile kinachojaribiwa hapa.
     * Uanachama unabaki umesimama - ndiyo hali hasa inayoweza kuvujisha
     * shamba lililofutwa.
     */
    private void softDeleteFarm(int farmId) {
        inTx(() -> {
            Farm farm = farms.findByFarmId(farmId).orElseThrow();
            farm.softDelete(adminId);
            return farms.save(farm);
        });
    }

    /**
     * Uanachama wa PILI kwa admin - ndiye "mmiliki wa mashamba mengi"
     * ambaye module hii imejengwa kwa ajili yake, na ambaye fixture ya
     * dev haina (angalia DevSeedService: kila mtu ana shamba moja).
     *
     * Cache ya JwtAuthFilter inafutwa kwa sababu inashikilia principal
     * kwa dakika 15; bila hivyo ombi linalofuata lingesoma uanachama wa
     * zamani na jaribio lingeshindwa kwa sababu isiyohusiana na msimbo
     * unaojaribiwa.
     */
    private void addAdminToFarmB() {
        inTx(() -> {
            FarmUser membership = new FarmUser();
            membership.setUser(userRepository.findByUserId(adminId).orElseThrow());
            membership.setFarm(farms.findByFarmId(farmB).orElseThrow());
            membership.setRole(roles.findByName("OWNER").orElseThrow());
            return farmUsers.save(membership);
        });
        JwtAuthFilter.clearUserCache(adminId);
    }

    /**
     * Mali inayoandikwa MOJA KWA MOJA kwenye database, si kupitia API.
     *
     * Inahitajika kwa jaribio la kuvuja: ili kuonyesha kwamba shamba
     * ambalo mwombaji si mwanachama wake HALIONEKANI, ni lazima liwe na
     * mali - na API yenyewe ingekataa kuiandika hapo, ambayo ndiyo
     * sheria inayojaribiwa.
     */
    private void writeAssetDirectly(String name, int farmId, int categoryId, String cost) {
        inTx(() -> {
            Asset asset = new Asset();
            asset.setName(name);
            asset.setFarm(farms.findByFarmId(farmId).orElseThrow());
            asset.setAssetCategory(assetCategoryRepository.findByAssetCategoryId(categoryId).orElseThrow());
            asset.setCost(new BigDecimal(cost));
            asset.setAcquiredDate(LocalDate.now(EAT).minusDays(1));
            return assetRepository.save(asset);
        });
    }

    // =====================================================================

    @Nested
    @DisplayName("kuandikisha")
    class Kuandikisha {

        @Test
        @DisplayName("mali inahifadhiwa ikiwa na shamba lake NA aina yake")
        void persistsWithFarmAndCategory() {
            int gari = category("Gari");

            JsonNode res = graphql(adminToken,
                    createMutation("Pikipiki", farmA, "1500000", "2024-03-15", "125cc", gari));

            assertThat(graphqlErrorCode(res)).isNull();
            JsonNode created = res.path("data").path("createAsset");
            assertThat(created.path("assetId").asInt()).isPositive();
            assertThat(created.path("name").asText()).isEqualTo("Pikipiki");
            assertThat(created.path("cost").asDouble()).isEqualTo(1500000.0);
            assertThat(created.path("sizeLabel").asText()).isEqualTo("125cc");
            assertThat(created.path("acquiredDate").asText()).isEqualTo("2024-03-15");
            assertThat(created.path("farm").path("farmId").asInt()).isEqualTo(farmA);
            assertThat(created.path("farm").path("name").asText()).isEqualTo("Dev Farm A");
            assertThat(created.path("assetCategory").path("assetCategoryId").asInt()).isEqualTo(gari);
            assertThat(created.path("assetCategory").path("name").asText()).isEqualTo("Gari");

            // Na si kwenye jibu la mutation pekee - kwenye DATABASE.
            Asset saved = inTx(() -> assetRepository.findAll().stream()
                    .filter(a -> "Pikipiki".equals(a.getName()))
                    .findFirst().orElseThrow());
            assertThat(saved.getFarm().getFarmId()).isEqualTo(farmA);
            assertThat(saved.getAssetCategory().getAssetCategoryId()).isEqualTo(gari);
            assertThat(saved.getCost()).isEqualByComparingTo(new BigDecimal("1500000.00"));
        }

        @Test
        @DisplayName("mali iliyoandikishwa inasomeka mara moja kwenye daftari")
        void readableImmediately() {
            int jengo = category("Jengo");
            graphql(adminToken, createMutation("Ghala", farmA, "8000000", "2023-01-10", null, jengo));

            JsonNode row = rowNamed(adminToken, "Ghala");

            assertThat(row).isNotNull();
            assertThat(row.path("farm").path("farmId").asInt()).isEqualTo(farmA);
            // sizeLabel haikutolewa - inabaki null, si maandishi matupu.
            assertThat(row.path("sizeLabel").isNull()).isTrue();
        }

        @Test
        @DisplayName("lebo ya ukubwa yenye nafasi tupu pekee inakuwa null, si maandishi matupu")
        void blankSizeLabelBecomesNull() {
            int kifaa = category("Kifaa");
            graphql(adminToken, createMutation("Mzani", farmA, "250000", "2024-06-01", "   ", kifaa));

            assertThat(rowNamed(adminToken, "Mzani").path("sizeLabel").isNull()).isTrue();
        }
    }

    @Nested
    @DisplayName("daftari la kampuni")
    class Kampuni {

        /**
         * HOJA NZIMA YA MODULE. Kila query nyingine ya data ingerudisha
         * shamba MOJA hapa - lile la principal (angalia
         * PermissionChecker.requireFarmScope). Daftari la mali linarudisha
         * yote mawili, kila mstari ukiwa na lebo yake.
         */
        @Test
        @DisplayName("mmiliki wa mashamba mawili anaona mali za YOTE kwenye orodha moja")
        void spansEveryFarmTheCallerBelongsTo() {
            int kifaa = category("Kifaa");
            addAdminToFarmB();

            graphql(adminToken, createMutation("Jenereta A", farmA, "3000000", "2024-02-01", null, kifaa));
            graphql(adminToken, createMutation("Jenereta B", farmB, "2500000", "2024-04-01", null, kifaa));

            JsonNode register = register(adminToken);

            assertThat(register).hasSize(2);
            assertThat(rowNamed(adminToken, "Jenereta A").path("farm").path("name").asText())
                    .isEqualTo("Dev Farm A");
            assertThat(rowNamed(adminToken, "Jenereta B").path("farm").path("name").asText())
                    .isEqualTo("Dev Farm B");
        }

        /**
         * Upande wa pili wa sarafu ile ile. Bila jaribio hili, "orodha
         * inayovuka mashamba" ingeweza kuwa imetekelezwa kama "mashamba
         * YOTE ya kampuni" - kosa ambalo jaribio la hapo juu
         * lisingeliona.
         */
        @Test
        @DisplayName("shamba ambalo mwombaji si mwanachama wake HALIVUJI kwenye daftari lake")
        void doesNotLeakFarmsTheCallerHasNoMembershipIn() {
            int kifaa = category("Kifaa");
            // Admin ni mwanachama wa A pekee (fixture ya dev).
            writeAssetDirectly("Boti ya B", farmB, kifaa, "4000000");
            graphql(adminToken, createMutation("Pampu ya A", farmA, "600000", "2024-05-05", null, kifaa));

            JsonNode register = register(adminToken);

            assertThat(register).hasSize(1);
            assertThat(register.get(0).path("name").asText()).isEqualTo("Pampu ya A");
            assertThat(rowNamed(adminToken, "Boti ya B")).isNull();
        }

        @Test
        @DisplayName("kuandikisha kwenye shamba asilo lake ni FORBIDDEN, si rekodi iliyoandikwa")
        void cannotRegisterIntoAnotherFarm() {
            int kifaa = category("Kifaa");

            JsonNode res = graphql(adminToken,
                    createMutation("Mali ya B", farmB, "900000", "2024-05-05", null, kifaa));

            assertThat(graphqlErrorCode(res)).isEqualTo("FORBIDDEN");
            assertThat(inTx(() -> assetRepository.count())).isZero();
        }

        /**
         * Uanachama wa PILI unafungua shamba la pili kwa KUANDIKA pia -
         * si kwa kusoma tu. requireResourceInCallersFarm ingekataa hapa,
         * kwa sababu principal inashikilia farmA pekee; ndiyo maana
         * AssetService ina ukaguzi wake (angalia requireCallersFarm).
         */
        @Test
        @DisplayName("mwanachama wa mashamba mawili anaandikisha kwenye lolote kati yao")
        void multiFarmMemberCanRegisterIntoEither() {
            int kifaa = category("Kifaa");
            addAdminToFarmB();

            JsonNode res = graphql(adminToken,
                    createMutation("Mali ya B", farmB, "900000", "2024-05-05", null, kifaa));

            assertThat(graphqlErrorCode(res)).isNull();
            assertThat(res.path("data").path("createAsset").path("farm").path("farmId").asInt())
                    .isEqualTo(farmB);
        }

        /**
         * Mtu aliyeidhinishwa asiye na shamba lolote ni hali HALALI
         * (angalia JwtAuthFilter). Jibu lake ni orodha tupu, si
         * NO_FARM_CONTEXT: daftari la kampuni halina "shamba teule" la
         * kukosa. (`noroleToken` ana uanachama lakini hana role, hivyo
         * anakwama kwenye ruhusa - angalia Ruhusa hapo chini.)
         */
        @Test
        @DisplayName("orodha tupu si hitilafu")
        void emptyRegisterIsNotAnError() {
            JsonNode res = graphql(adminToken, ASSETS_QUERY);

            assertThat(graphqlErrorCode(res)).isNull();
            assertThat(res.path("data").path("assets")).isEmpty();
        }
    }

    /**
     * KICHAGUA-SHAMBA cha fomu ya kuandikisha mali.
     *
     * Kilikuwa hakiwezekani: farmId ilionekana kwenye `assets` pekee,
     * hivyo shamba lingeweza kuchaguliwa BAADA tu ya kuwa na mali - na
     * mali ya KWANZA ndiyo isiyoweza kuandikishwa. Majaribio haya ni
     * mipaka mitatu ya orodha hiyo: yote yake, yake PEKEE, na yaliyopo
     * pekee.
     */
    @Nested
    @DisplayName("mashamba yangu")
    class MashambaYangu {

        @Test
        @DisplayName("mmiliki wa mashamba mawili anayaona YOTE mawili")
        void returnsEveryFarmTheCallerBelongsTo() {
            addAdminToFarmB();

            assertThat(myFarmNames(adminToken)).containsExactly("Dev Farm A", "Dev Farm B");
        }

        /**
         * Upande wa pili wa sarafu, kama Kampuni hapo juu: bila jaribio
         * hili, `myFarms` ingeweza kuwa imetekelezwa kama
         * FarmService.listAll (mashamba YOTE ya kampuni) na jaribio la
         * hapo juu lisingeliona.
         */
        @Test
        @DisplayName("shamba ambalo mwombaji si mwanachama wake HALIVUJI")
        void doesNotLeakFarmsTheCallerHasNoMembershipIn() {
            // Admin ni mwanachama/mmiliki wa A pekee (fixture ya dev),
            // ilhali "Dev Farm B" lipo na lina mwanachama wake mwenyewe.
            assertThat(myFarmNames(adminToken)).containsExactly("Dev Farm A");
        }

        /**
         * Uanachama wa `farm_users` unabaki hata baada ya shamba kufutwa,
         * hivyo swali linalosoma safu ya FK pekee (`fu.farm.farmId`)
         * lingelirudisha - ndiyo sababu findFarmIdsByUserId ina join ya
         * WAZI. Hili ndilo jaribio linaloshika hilo.
         */
        @Test
        @DisplayName("shamba LILILOFUTWA halionekani, hata ukiwa bado mwanachama wake")
        void excludesSoftDeletedFarms() {
            addAdminToFarmB();
            assertThat(myFarmNames(adminToken)).hasSize(2);

            softDeleteFarm(farmB);

            assertThat(myFarmNames(adminToken)).containsExactly("Dev Farm A");
        }

        /**
         * LANGO NI KUINGIA, SI `manage_assets` - tofauti na kila query
         * nyingine ya module hii (angalia Ruhusa hapo chini, ambapo hawa
         * wawili wanapata FORBIDDEN kwenye `assets`). Mashamba ya mtu
         * mwenyewe si data ya kampuni.
         */
        @Test
        @DisplayName("asiye na manage_assets bado anaona mashamba YAKE")
        void anyAuthenticatedUserSeesTheirOwnFarms() {
            assertThat(graphqlErrorCode(graphql(workerToken, MY_FARMS_QUERY))).isNull();
            assertThat(myFarmNames(workerToken)).containsExactly("Dev Farm A");

            // Hata asiye na role kabisa - ana uanachama, na ndilo swali.
            assertThat(graphqlErrorCode(graphql(noroleToken, MY_FARMS_QUERY))).isNull();
            assertThat(myFarmNames(noroleToken)).containsExactly("Dev Farm A");
        }

        @Test
        @DisplayName("kila shamba linakuja na farmId ya kuchagua nayo")
        void carriesFarmId() {
            JsonNode row = graphql(adminToken, MY_FARMS_QUERY)
                    .path("data").path("myFarms").get(0);

            assertThat(row.path("farmId").asInt()).isEqualTo(farmA);
            assertThat(row.path("name").asText()).isEqualTo("Dev Farm A");
        }

        /**
         * Bila token si orodha tupu - ni 401. Ruhusa imeondolewa kwenye
         * query hii, kuingia HAKUJAONDOLEWA.
         *
         * Ombi linakatwa na SecurityConfig kabla halijafika injini ya
         * GraphQL, hivyo jibu ni ApiResponse ya 401 - si `errors` ya
         * GraphQL. Ni sheria ile ile ya SpeciesCatalogTest.
         */
        @Test
        @DisplayName("asiyeingia anapata 401 UNAUTHENTICATED, si orodha tupu")
        void anonymousIsUnauthenticated() throws Exception {
            String body = json.writeValueAsString(Map.of("query", MY_FARMS_QUERY));

            ResponseEntity<String> response = post("/graphql", body, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(parse(response).path("errorCode").asText()).isEqualTo("UNAUTHENTICATED");
        }
    }

    @Nested
    @DisplayName("katalogi ya aina")
    class Katalogi {

        @Test
        @DisplayName("aina mpya inaingia kwenye katalogi na inasomeka mara moja")
        void createsAndLists() {
            JsonNode res = graphql(adminToken,
                    "mutation { createAssetCategory(name: \"Nyavu\") { assetCategoryId name } }");

            assertThat(graphqlErrorCode(res)).isNull();
            assertThat(res.path("data").path("createAssetCategory").path("name").asText())
                    .isEqualTo("Nyavu");

            JsonNode katalogi = graphql(adminToken, CATEGORIES_QUERY).path("data").path("assetCategories");
            assertThat(katalogi).hasSize(1);
            assertThat(katalogi.get(0).path("name").asText()).isEqualTo("Nyavu");
        }

        @Test
        @DisplayName("katalogi inaanza TUPU - hakuna orodha ya kudumu iliyopandwa")
        void startsEmpty() {
            // Ndiyo tofauti kati ya jedwali na enum: hakuna "Kifaa, Jengo,
            // Gari" iliyoamuliwa na msimbo. V1 iliandika orodha hiyo kwenye
            // maoni; V21 imeiacha kwa mtumiaji.
            assertThat(graphql(adminToken, CATEGORIES_QUERY).path("data").path("assetCategories"))
                    .isEmpty();
        }

        @Test
        @DisplayName("nafasi tupu pembeni mwa jina zinaondolewa")
        void trimsName() {
            graphql(adminToken, "mutation { createAssetCategory(name: \"  Pampu  \") { name } }");

            JsonNode katalogi = graphql(adminToken, CATEGORIES_QUERY).path("data").path("assetCategories");
            assertThat(katalogi.get(0).path("name").asText()).isEqualTo("Pampu");
        }

        @Test
        @DisplayName("jina tupu linakataliwa")
        void refusesBlankName() {
            assertThat(graphqlErrorCode(graphql(adminToken,
                    "mutation { createAssetCategory(name: \"   \") { name } }")))
                    .isEqualTo("VALIDATION_ERROR");
        }

        @Test
        @DisplayName("jina lililochukuliwa linakataliwa kwa CONFLICT, si kwa hitilafu ya database")
        void refusesDuplicateName() {
            category("Gari");

            JsonNode res = graphql(adminToken,
                    "mutation { createAssetCategory(name: \"Gari\") { assetCategoryId } }");

            assertThat(graphqlErrorCode(res)).isEqualTo("CONFLICT");
            // Sentensi yetu, si ile ya jumla ya vikwazo vya database.
            assertThat(graphqlMessage(res)).contains("jina hili tayari ipo");
        }

        /**
         * `asset_categories.name` ni UNIQUE ya kawaida (V21), lakini
         * @SQLRestriction inaificha aina iliyofutwa kwenye kila query ya
         * JPA. Bila swali la native, ukaguzi wetu ungepita na database
         * ndiyo ingekataa - CONFLICT yenye sentensi isiyomweleza
         * msimamizi kwamba tatizo ni jina ASILOLIONA. Ni sheria ile ile
         * ya FeedType na Species.
         */
        @Test
        @DisplayName("jina la aina ILIYOFUTWA bado limechukuliwa")
        void refusesNameOfSoftDeletedCategory() {
            int id = category("Zamani");
            inTx(() -> {
                AssetCategory category = assetCategoryRepository.findByAssetCategoryId(id).orElseThrow();
                category.softDelete(adminId);
                return assetCategoryRepository.save(category);
            });
            assertThat(graphql(adminToken, CATEGORIES_QUERY).path("data").path("assetCategories"))
                    .isEmpty();

            JsonNode res = graphql(adminToken,
                    "mutation { createAssetCategory(name: \"Zamani\") { assetCategoryId } }");

            assertThat(graphqlErrorCode(res)).isEqualTo("CONFLICT");
            assertThat(graphqlMessage(res)).contains("jina hili tayari ipo");
        }
    }

    @Nested
    @DisplayName("uthibitisho")
    class Uthibitisho {

        @Test
        @DisplayName("jina tupu linakataliwa")
        void refusesBlankName() {
            int kifaa = category("Kifaa");

            JsonNode res = graphql(adminToken,
                    createMutation("   ", farmA, "100000", "2024-01-01", null, kifaa));

            assertThat(graphqlErrorCode(res)).isEqualTo("VALIDATION_ERROR");
            assertThat(inTx(() -> assetRepository.count())).isZero();
        }

        @Test
        @DisplayName("bei ya sifuri au hasi inakataliwa")
        void refusesNonPositiveCost() {
            int kifaa = category("Kifaa");

            assertThat(graphqlErrorCode(graphql(adminToken,
                    createMutation("Sifuri", farmA, "0", "2024-01-01", null, kifaa))))
                    .isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlErrorCode(graphql(adminToken,
                    createMutation("Hasi", farmA, "-5000", "2024-01-01", null, kifaa))))
                    .isEqualTo("VALIDATION_ERROR");
            assertThat(inTx(() -> assetRepository.count())).isZero();
        }

        /**
         * 0.004 ni CHANYA, lakini kwa NUMERIC(14,2) ni 0.00 - ambayo CHECK
         * ya V21 ingeikataa kama ukiukwaji wa kikwazo cha database. Sheria
         * ile ile ya SpeciesService (thamani inayoshuka hadi sifuri baada
         * ya kuzungushwa).
         */
        @Test
        @DisplayName("bei inayoshuka hadi sifuri baada ya kuzungushwa inakataliwa kwa ujumbe")
        void refusesCostThatRoundsToZero() {
            int kifaa = category("Kifaa");

            JsonNode res = graphql(adminToken,
                    createMutation("Ndogo", farmA, "0.004", "2024-01-01", null, kifaa));

            assertThat(graphqlErrorCode(res)).isEqualTo("VALIDATION_ERROR");
            assertThat(inTx(() -> assetRepository.count())).isZero();
        }

        /**
         * Kitu ambacho bado hakijanunuliwa si mali - ni mpango. Sheria ile
         * ile ya DailyTaskService.requireNotInTheFuture, na "leo" ni ya
         * EAT kwa sababu ile ile.
         */
        @Test
        @DisplayName("tarehe ya baadaye inakataliwa")
        void refusesFutureAcquiredDate() {
            int kifaa = category("Kifaa");
            String kesho = LocalDate.now(EAT).plusDays(1).toString();

            JsonNode res = graphql(adminToken,
                    createMutation("Kesho", farmA, "100000", kesho, null, kifaa));

            assertThat(graphqlErrorCode(res)).isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlMessage(res)).contains("ya baadaye");
            assertThat(inTx(() -> assetRepository.count())).isZero();
        }

        @Test
        @DisplayName("mbali zaidi ya kesho pia - si suala la siku moja")
        void refusesFarFutureToo() {
            int kifaa = category("Kifaa");
            String mwakaUjao = LocalDate.now(EAT).plusYears(1).toString();

            assertThat(graphqlErrorCode(graphql(adminToken,
                    createMutation("Mwakani", farmA, "100000", mwakaUjao, null, kifaa))))
                    .isEqualTo("VALIDATION_ERROR");
        }

        /**
         * LEO na ZAMANI zote ni halali, na ya zamani ndiyo hali ya
         * kawaida: daftari linaanzishwa leo kwa vitu vya miaka mitatu
         * iliyopita. Ukaguzi wa kesho HAUKUGUSA hizi.
         */
        @Test
        @DisplayName("tarehe ya leo na ya nyuma zinakubaliwa")
        void acceptsTodayAndPast() {
            int kifaa = category("Kifaa");

            assertThat(graphqlErrorCode(graphql(adminToken,
                    createMutation("Leo", farmA, "100000", today(), null, kifaa)))).isNull();
            assertThat(graphqlErrorCode(graphql(adminToken,
                    createMutation("Zamani", farmA, "100000", "2019-07-04", null, kifaa)))).isNull();
        }

        @Test
        @DisplayName("tarehe isiyosomeka inakataliwa kwa ujumbe unaotaja muundo")
        void refusesUnparseableDate() {
            int kifaa = category("Kifaa");

            JsonNode res = graphql(adminToken,
                    createMutation("Mbovu", farmA, "100000", "15/03/2024", null, kifaa));

            assertThat(graphqlErrorCode(res)).isEqualTo("VALIDATION_ERROR");
            assertThat(graphqlMessage(res)).contains("YYYY-MM-DD");
        }

        @Test
        @DisplayName("aina isiyojulikana inakataliwa")
        void refusesUnknownCategory() {
            JsonNode res = graphql(adminToken,
                    createMutation("Yatima", farmA, "100000", "2024-01-01", null, 99999));

            assertThat(graphqlErrorCode(res)).isEqualTo("VALIDATION_ERROR");
            assertThat(inTx(() -> assetRepository.count())).isZero();
        }
    }

    @Nested
    @DisplayName("ruhusa")
    class Ruhusa {

        @Test
        @DisplayName("WORKER hawezi kuandikisha mali")
        void workerCannotRegister() {
            int kifaa = category("Kifaa");

            JsonNode res = graphql(workerToken,
                    createMutation("Ya mfanyakazi", farmA, "100000", "2024-01-01", null, kifaa));

            assertThat(graphqlErrorCode(res)).isEqualTo("FORBIDDEN");
            assertThat(inTx(() -> assetRepository.count())).isZero();
        }

        /**
         * Tofauti na `species`/`feedTypes`, KUSOMA nako ni
         * `manage_assets`. Bei ya kila kitu kampuni inachomiliki si
         * taarifa ya kazi ya leo, na hakuna kazi ya shambani
         * inayoihitaji.
         */
        @Test
        @DisplayName("WORKER hawezi hata KUSOMA daftari - si taarifa ya kazi ya leo")
        void workerCannotRead() {
            assertThat(graphqlErrorCode(graphql(workerToken, ASSETS_QUERY))).isEqualTo("FORBIDDEN");
            assertThat(graphqlErrorCode(graphql(workerToken, CATEGORIES_QUERY))).isEqualTo("FORBIDDEN");
        }

        @Test
        @DisplayName("VIEWER hawezi kusoma wala kuandika")
        void viewerCannotReadOrWrite() {
            int kifaa = category("Kifaa");

            assertThat(graphqlErrorCode(graphql(viewerToken, ASSETS_QUERY))).isEqualTo("FORBIDDEN");
            assertThat(graphqlErrorCode(graphql(viewerToken, CATEGORIES_QUERY))).isEqualTo("FORBIDDEN");
            assertThat(graphqlErrorCode(graphql(viewerToken,
                    createMutation("Ya mtazamaji", farmA, "100000", "2024-01-01", null, kifaa))))
                    .isEqualTo("FORBIDDEN");
            assertThat(graphqlErrorCode(graphql(viewerToken,
                    "mutation { createAssetCategory(name: \"Ya mtazamaji\") { assetCategoryId } }")))
                    .isEqualTo("FORBIDDEN");
        }

        @Test
        @DisplayName("WORKER hawezi kuunda aina ya mali")
        void workerCannotCreateCategory() {
            JsonNode res = graphql(workerToken,
                    "mutation { createAssetCategory(name: \"Ya mfanyakazi\") { assetCategoryId } }");

            assertThat(graphqlErrorCode(res)).isEqualTo("FORBIDDEN");
            assertThat(inTx(() -> assetCategoryRepository.count())).isZero();
        }

        @Test
        @DisplayName("asiye na role hana ruhusa - lango ni ruhusa, si uanachama")
        void noRoleIsForbidden() {
            assertThat(graphqlErrorCode(graphql(noroleToken, ASSETS_QUERY))).isEqualTo("FORBIDDEN");
        }

        /**
         * FARM_MANAGER ANAYO (V22). Fixture ya dev haina mtu wa nafasi
         * hiyo, hivyo uthibitisho unafanywa kwa kubadilisha role ya
         * mfanyakazi - njia ile ile ambayo RBAC ya kweli ingetumia.
         */
        @Test
        @DisplayName("FARM_MANAGER anayo ruhusa - si OWNER pekee")
        void farmManagerIsAllowed() {
            inTx(() -> {
                FarmUser membership = farmUsers
                        .findByUser_UserIdAndFarm_FarmId(workerId, farmA).orElseThrow();
                membership.setRole(roles.findByName("FARM_MANAGER").orElseThrow());
                return farmUsers.save(membership);
            });
            JwtAuthFilter.clearUserCache(workerId);

            JsonNode res = graphql(workerToken,
                    "mutation { createAssetCategory(name: \"Ya meneja\") { assetCategoryId name } }");

            assertThat(graphqlErrorCode(res)).isNull();
            assertThat(graphqlErrorCode(graphql(workerToken, ASSETS_QUERY))).isNull();
        }
    }
}
