package com.samaki.farm.asset.services;

import com.samaki.farm.asset.entity.Asset;
import com.samaki.farm.asset.entity.AssetCategory;
import com.samaki.farm.asset.repository.AssetCategoryRepository;
import com.samaki.farm.asset.repository.AssetRepository;
import com.samaki.farm.auth.security.PermissionChecker;
import com.samaki.farm.common.exception.ConflictException;
import com.samaki.farm.farm.entity.Farm;
import com.samaki.farm.farm.services.FarmMembershipService;
import com.samaki.farm.reminder.config.ReminderProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * DAFTARI LA MALI - kila kitu kampuni inachomiliki, na kiko wapi.
 *
 * =====================================================================
 * MODULE HII NI YA KAMPUNI, SI YA SHAMBA - NDIYO TOFAUTI YAKE NZIMA
 *
 * Kila query nyingine ya data kwenye repo hii inaanza na
 * {@code permissionChecker.requireFarmScope(...)}, ambayo inarudisha
 * farmId MOJA ya mwombaji na kuchuja kila kitu kwayo. Hiyo ni sahihi kwa
 * data ya uzalishaji: ulishaji wa shamba A hauhusiani kabisa na shamba B.
 *
 * Mali si hivyo. Mmiliki mwenye mashamba matatu ana swali MOJA -
 * "nimewekeza kiasi gani, na wapi?" - na jibu lake ni orodha moja
 * inayovuka mashamba yote, kila mstari ukiwa na lebo ya shamba lake.
 * Kuichuja kwa shamba teule kungegeuza daftari la kampuni kuwa daftari
 * la shamba, na jumla ambayo mmiliki anaitafuta isingekuwepo popote.
 *
 * Ndiyo maana hapa ni {@code require("manage_assets")} - SI
 * {@code requireFarmScope} - kisha uanachama unakokotolewa kwa
 * {@link #callersFarmIds()}. Ruhusa inasema "unaruhusiwa kuona daftari";
 * uanachama unasema "daftari LIPI". Ni mgawanyo ule ule wa
 * SpeciesService/FeedService.listFeedTypes kwa katalogi za kimfumo,
 * lakini kwa sababu tofauti: huko hakuna farm_id hata kidogo, hapa kuna
 * farm_id na ni MENGI.
 *
 * KIKOMO KINACHOJULIKANA (si cha module hii). JwtAuthFilter
 * inampa mtumiaji ruhusa za uanachama wa KWANZA pekee
 * ({@code memberships.get(0)}, yenye TODO ya farm switching). Kwa hivyo
 * mtu aliye WORKER shamba #1 na OWNER shamba #2 anashikilia ruhusa za
 * WORKER kila mahali, hivyo hapati {@code manage_assets} kabisa. Daftari
 * hili haliwezi kurekebisha hilo - lingehitaji auth kubadilishwa - na
 * halijaribu: linakokotoa MASHAMBA kutoka database, lakini RUHUSA
 * inabaki ile ile principal aliyonayo.
 * =====================================================================
 */
@Service
public class AssetService {

    /** Lango la module NZIMA - kusoma na kuandika (angalia V22). */
    private static final String PERMISSION = "manage_assets";

    /** `assets.name` ni VARCHAR(150), `size_label` ni VARCHAR(80) (V21). */
    private static final int NAME_MAX_LENGTH = 150;
    private static final int SIZE_LABEL_MAX_LENGTH = 80;

    /** `asset_categories.name` ni VARCHAR(80) (V21). */
    private static final int CATEGORY_NAME_MAX_LENGTH = 80;

    /** `assets.cost` ni NUMERIC(14,2): precision - scale inaishia 999999999999.99. */
    private static final int COST_SCALE = 2;
    private static final BigDecimal COST_MAX = new BigDecimal("999999999999.99");

    private final AssetRepository assetRepository;
    private final AssetCategoryRepository assetCategoryRepository;
    private final FarmMembershipService farmMembership;
    private final PermissionChecker permissionChecker;
    private final ReminderProperties reminderProperties;

    public AssetService(AssetRepository assetRepository,
                        AssetCategoryRepository assetCategoryRepository,
                        FarmMembershipService farmMembership,
                        PermissionChecker permissionChecker,
                        ReminderProperties reminderProperties) {
        this.assetRepository = assetRepository;
        this.assetCategoryRepository = assetCategoryRepository;
        this.farmMembership = farmMembership;
        this.permissionChecker = permissionChecker;
        this.reminderProperties = reminderProperties;
    }

    // ==================================================== katalogi

    /**
     * Aina zote za mali - katalogi ya kimfumo, kwa mpangilio wa jina.
     *
     * Inalindwa na {@code manage_assets} ile ile ya kuandika, tofauti na
     * `species`/`feedTypes` ambazo kusoma kwake ni ruhusa nyepesi zaidi.
     * Sababu: huko orodha inahitajika na WORKER anayefanya kazi ya leo
     * (kuchagua chakula, kuchagua aina wakati wa kuweka vifaranga). Hapa
     * mtumiaji pekee wa orodha ni fomu ya kuandikisha mali, ambayo tayari
     * ni ya `manage_assets` - kuifungua zaidi kungefungua kitu kisicho na
     * anayekitumia.
     */
    @Transactional(readOnly = true)
    public List<AssetCategory> listCategories() {
        permissionChecker.require(PERMISSION);
        return assetCategoryRepository.findAllByOrderByNameAsc();
    }

    /**
     * Aina mpya kwenye katalogi.
     *
     * {@code require}, SI {@code requireFarmScope}: `asset_categories`
     * haina farm_id (V21). Ni sheria ile ile ya SpeciesService.create na
     * FeedService.createFeedType - kudai muktadha wa shamba kwa kitu
     * kisicho na shamba ni kuzuia usimamizi wa katalogi ya kimfumo kwa
     * sababu isiyohusiana nayo.
     */
    @Transactional
    public AssetCategory createCategory(String name) {
        permissionChecker.require(PERMISSION);

        AssetCategory category = new AssetCategory();
        category.setName(requireAvailableCategoryName(name));
        return assetCategoryRepository.save(category);
    }

    // ==================================================== daftari

    /**
     * Mali za MASHAMBA YOTE ya mwombaji, mpya kwanza.
     *
     * Orodha tupu ya mashamba haifiki database: `farm_id IN ()` si SQL
     * halali. Mtu aliyeidhinishwa asiye na shamba lolote (hali HALALI -
     * angalia JwtAuthFilter) anapata orodha tupu, si hitilafu: swali lake
     * lina jibu, na jibu ni "huna mali kwa sababu huna shamba". Hapa
     * HAKUNA NO_FARM_CONTEXT kwa makusudi - msimbo huo unamaanisha "ombi
     * lako halina maana bila shamba teule", na daftari la kampuni halina
     * shamba teule hata kidogo.
     */
    @Transactional(readOnly = true)
    public List<Asset> listAssets() {
        permissionChecker.require(PERMISSION);

        List<Integer> farmIds = callersFarmIds();
        if (farmIds.isEmpty()) {
            return List.of();
        }
        return assetRepository.findByFarm_FarmIdInOrderByAcquiredDateDescAssetIdDesc(farmIds);
    }

    /**
     * Kuandikisha mali mpya.
     *
     * farmId INATOKA KWA MTEJA, hivyo lazima ithibitishwe - lakini SI kwa
     * {@code permissionChecker.requireResourceInCallersFarm}, ambayo
     * inalinganisha na farmId MOJA ya principal. Mmiliki mwenye mashamba
     * mawili angezuiwa kuandikisha mali kwenye shamba lake la pili, kwa
     * sababu principal inashikilia la kwanza pekee (angalia javadoc ya
     * darasa). Ukaguzi ni uanachama halisi kutoka database - sheria ile
     * ile, chanzo pana zaidi.
     *
     * Shamba lisilo lake linajibiwa AccessDeniedException (FORBIDDEN), si
     * "halipo": ni jibu lile lile requireResourceInCallersFarm inalotoa,
     * na kutofautisha "halipo" na "si lako" kungemwambia mgeni ni
     * mashamba mangapi yapo.
     */
    @Transactional
    public Asset createAsset(String name, Integer farmId, Double cost, String acquiredDate,
                             String sizeLabel, Integer assetCategoryId) {
        permissionChecker.require(PERMISSION);

        Asset asset = new Asset();
        asset.setName(requireName(name));
        asset.setFarm(farmMembership.requireCallersFarm(farmId));
        asset.setCost(requireCost(cost));
        asset.setAcquiredDate(requireAcquiredDate(acquiredDate));
        asset.setSizeLabel(normaliseSizeLabel(sizeLabel));
        asset.setAssetCategory(requireCategory(assetCategoryId));
        return assetRepository.save(asset);
    }

    // ==================================================== uanachama

    /**
     * MASHAMBA YA MWOMBAJI - yale anayoyamiliki au kuwa mwanachama wake.
     *
     * HAKUNA {@code require(PERMISSION)} HAPA, na ndiyo tofauti pekee
     * yenye maana kati ya method hii na {@link #listAssets()}. Mali ni
     * data - bei ya kila kitu kampuni inachomiliki - hivyo inalindwa.
     * Mashamba ya mtu mwenyewe si data ya kampuni; ni jibu la "wewe ni
     * nani hapa", ambalo mwombaji tayari analijua. Kulifunga kwa
     * {@code manage_assets} kungemficha mtu mashamba YAKE MWENYEWE kwa
     * sababu isiyohusiana nayo hata kidogo.
     *
     * Kinachobaki ni {@code currentUser()} ndani ya {@link #callersFarmIds()}:
     * asiyeingia (login) anapata 401, si orodha tupu.
     *
     * Kazi yenyewe iko {@link FarmMembershipService#callersFarms()} -
     * uanachama ni swali la mashamba, si la mali, na tangu module ya
     * gharama (V23/V24) ni maswali MATATU yanayolihitaji. Linganisha na
     * FarmService.listAll, ambayo ni ya `manage_farms` na inarudisha
     * mashamba YOTE ya kampuni - swali TOFAUTI, si hili.
     */
    @Transactional(readOnly = true)
    public List<Farm> listCallersFarms() {
        return farmMembership.callersFarms();
    }

    /**
     * MASHAMBA YOTE ya mwombaji - uanachama (`farm_users`) PAMOJA na
     * umiliki (`farms.owner_user_id`).
     *
     * ILIKUWA HAPA, sasa iko {@link FarmMembershipService}. Ilihamishwa
     * module ya gharama (V23/V24) ilipohitaji jibu lile lile: nakala ya
     * pili ya swali hili ingekuwa nafasi ya pili ya kuvujisha shamba
     * lililofutwa (angalia FarmUserRepository.findFarmIdsByUserId, ambapo
     * `join fu.farm f` ya wazi ndiyo inayolizuia). Method inabaki hapa
     * kama jina la ndani ili maelezo ya daftari hili yasitawanyike.
     */
    private List<Integer> callersFarmIds() {
        return farmMembership.callersFarmIds();
    }

    // ==================================================== uthibitisho

    private String requireName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Jina la mali linahitajika.");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("Jina la mali lisizidi herufi " + NAME_MAX_LENGTH + ".");
        }
        return name;
    }

    /**
     * Bei ya kununulia - LAZIMA iwe zaidi ya sifuri.
     *
     * UKAGUZI NI MARA MBILI, kama SpeciesService.requirePositiveDecimal,
     * na wa pili ndio wenye maana isiyoonekana: `0.004` ni chanya, lakini
     * kwa NUMERIC(14,2) ni `0.00` - ambayo CHECK ya V21
     * (chk_assets_cost_positive) itaikataa kwenye database, ikirudi kama
     * sentensi ya jumla kuhusu vikwazo badala ya ujumbe unaotaja uga.
     *
     * Kikomo cha juu ni cha SAFU, si cha kibiashara: NUMERIC(14,2)
     * ikizidishwa inatoa `numeric field overflow` ya PostgreSQL.
     */
    private static BigDecimal requireCost(Double raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Bei ya mali inahitajika.");
        }
        if (raw.isNaN() || raw.isInfinite()) {
            throw new IllegalArgumentException("Bei ya mali si namba halali.");
        }
        if (raw <= 0) {
            throw new IllegalArgumentException("Bei ya mali lazima iwe zaidi ya sifuri.");
        }

        BigDecimal cost = BigDecimal.valueOf(raw).setScale(COST_SCALE, RoundingMode.HALF_UP);

        if (cost.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Bei ya mali ni ndogo mno - haiwezi kuwa chini ya 0.01.");
        }
        if (cost.compareTo(COST_MAX) > 0) {
            throw new IllegalArgumentException(
                    "Bei ya mali haiwezi kuzidi " + COST_MAX.toPlainString() + ".");
        }
        return cost;
    }

    /**
     * Tarehe ya kupata mali - LAZIMA, na SI YA BAADAYE.
     *
     * Tofauti na WaterQualityService.parseDate na
     * DailyTaskService.parseDate, tarehe hii HAINA chaguo-msingi la leo:
     * mali nyingi zinaandikishwa muda mrefu baada ya kununuliwa (daftari
     * linaanzishwa leo kwa vitu vya miaka mitatu iliyopita), hivyo "leo"
     * kimyakimya ingekuwa uongo unaoingia bila mtu kuchagua.
     *
     * Ya nyuma INARUHUSIWA na ndiyo hali ya kawaida kabisa. Ya baadaye
     * inakataliwa kwa sheria ile ile ya DailyTaskService.
     * requireNotInTheFuture: kitu ambacho bado hakijanunuliwa si mali -
     * ni mpango, na daftari la mpango si daftari la mali.
     *
     * "Leo" ni ya EAT (Africa/Nairobi), si ya server: kati ya saa 3
     * usiku na usiku wa manane, UTC tayari iko KESHO kwa mkulima wa
     * Dodoma - tarehe angeiandika ingekataliwa kama "ya baadaye". Ni
     * kanda ile ile DailyTaskService na Scheduler zinazotumia.
     */
    private LocalDate requireAcquiredDate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Tarehe ya kupata mali inahitajika.");
        }

        LocalDate acquired;
        try {
            acquired = LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Tarehe si sahihi. Tumia muundo YYYY-MM-DD.");
        }

        LocalDate today = LocalDate.now(reminderProperties.zoneId());
        if (acquired.isAfter(today)) {
            throw new IllegalArgumentException(
                    "Tarehe ya kupata mali haiwezi kuwa ya baadaye (" + acquired
                            + "). Leo ni " + today + ".");
        }
        return acquired;
    }

    /** Lebo tupu ni KUTOKUWA na lebo, si lebo isiyo na herufi. */
    private static String normaliseSizeLabel(String raw) {
        if (raw == null) {
            return null;
        }
        String label = raw.trim();
        if (label.isEmpty()) {
            return null;
        }
        if (label.length() > SIZE_LABEL_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Lebo ya ukubwa isizidi herufi " + SIZE_LABEL_MAX_LENGTH + ".");
        }
        return label;
    }

    /**
     * Aina ya mali, ikiwa BADO ipo.
     *
     * HAIKAGULIWI kwa shamba kwa makusudi: `asset_categories` ni katalogi
     * ya kimfumo isiyo na farm_id (V21) - ni sheria ile ile ya
     * CycleService.create kwa speciesId.
     */
    private AssetCategory requireCategory(Integer assetCategoryId) {
        if (assetCategoryId == null) {
            throw new IllegalArgumentException("Aina ya mali inahitajika.");
        }
        return assetCategoryRepository.findByAssetCategoryId(assetCategoryId)
                .orElseThrow(() -> new IllegalArgumentException("Aina ya mali haijulikani."));
    }

    /**
     * Jina la aina lililopunguzwa nafasi tupu, likiwa halijachukuliwa.
     *
     * Swali linahesabu hata aina ZILIZOFUTWA - angalia
     * AssetCategoryRepository.countByNameIncludingDeleted kwa kwa nini.
     */
    private String requireAvailableCategoryName(String raw) {
        String name = raw == null ? "" : raw.trim();

        if (name.isEmpty()) {
            throw new IllegalArgumentException("Jina la aina ya mali linahitajika.");
        }
        if (name.length() > CATEGORY_NAME_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Jina la aina ya mali lisizidi herufi " + CATEGORY_NAME_MAX_LENGTH + ".");
        }
        if (assetCategoryRepository.countByNameIncludingDeleted(name, null) > 0) {
            throw new ConflictException("Aina ya mali yenye jina hili tayari ipo.");
        }
        return name;
    }
}
