package com.samaki.farm.species.services;

import com.samaki.farm.auth.security.PermissionChecker;
import com.samaki.farm.common.exception.ConflictException;
import com.samaki.farm.species.entity.Species;
import com.samaki.farm.species.repository.SpeciesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Katalogi ya aina za samaki.
 *
 * Ilikuwa haifikiki kabisa kupitia API: hakuna query wala controller,
 * ilhali CreateCycleInput inadai speciesId (angalia
 * FRONTEND_BACKEND_AUDIT.md, D-3). Hivyo ukurasa wa kuunda mzunguko
 * haukuwa unajengeka - frontend haikuwa na njia ya kuorodhesha aina, na
 * kuandika 1 na 2 moja kwa moja kungevunjika mara aina mpya inapoongezwa.
 *
 * KUSOMA + KUUNDA (V20). Awali ilikuwa ya kusoma pekee, kwa hoja kwamba
 * aina zinatoka kwenye seed ya V1 na ni uamuzi wa kimfumo. Hoja hiyo
 * ilikuwa na shimo: mkulima anayefuga aina isiyo Sato wala Kambale
 * hakuwa na njia YOYOTE ya kuiongeza isipokuwa kugusa database. Sasa
 * ipo, ikilindwa na `manage_species` (OWNER/FARM_MANAGER pekee).
 *
 * HAKUNA kuhariri wala kufuta kwa makusudi - angalia createSpecies kwa
 * kwa nini kuhariri ni kitu kikubwa zaidi kuliko kinavyoonekana.
 *
 * HAICHUJWI kwa shamba - `species` haina farm_id: ni katalogi moja
 * inayoshirikiwa na mashamba yote (angalia CycleService.create, ambapo
 * speciesId nayo haikaguliwi kwa shamba kwa sababu hiyo hiyo).
 */
@Service
public class SpeciesService {

    /** `species.name` ni VARCHAR(80) (V1). */
    private static final int NAME_MAX_LENGTH = 80;

    /**
     * `growth_months_avg` ni NUMERIC(4,1) na `avg_harvest_weight_kg` ni
     * NUMERIC(6,2) (V1). Desimali ZINAHIFADHIWA - hazikatwi: miezi 6.5 ni
     * thamani halali na CycleService.expectedHarvestDate inaitegemea
     * (angalia D-7, ambapo .longValue() ilikata nusu-mwezi kimyakimya).
     */
    private static final int GROWTH_MONTHS_SCALE = 1;
    private static final int HARVEST_WEIGHT_SCALE = 2;

    /** precision - scale: NUMERIC(4,1) inaishia 999.9; NUMERIC(6,2) inaishia 9999.99. */
    private static final BigDecimal GROWTH_MONTHS_MAX = new BigDecimal("999.9");
    private static final BigDecimal HARVEST_WEIGHT_MAX = new BigDecimal("9999.99");

    private final SpeciesRepository speciesRepository;
    private final PermissionChecker permissionChecker;

    public SpeciesService(SpeciesRepository speciesRepository, PermissionChecker permissionChecker) {
        this.speciesRepository = speciesRepository;
        this.permissionChecker = permissionChecker;
    }

    /**
     * view_dashboard - ruhusa ile ile inayotumiwa na query nyingine zote za
     * kusoma (productionUnits/cycles/feed*). Aina za samaki si siri, lakini
     * kuiacha wazi kungefanya iwe query pekee ya GraphQL isiyo na ukaguzi.
     *
     * HAIBADILIKI na V20: kuandika ndiko kunakohitaji `manage_species`.
     * WORKER anayechagua aina wakati wa kuunda mzunguko lazima aendelee
     * kuiona orodha.
     */
    @Transactional(readOnly = true)
    public List<Species> listAll() {
        permissionChecker.require("view_dashboard");
        return speciesRepository.findAll();
    }

    /**
     * Aina mpya kwenye katalogi.
     *
     * `require`, SI `requireFarmScope`: species haina farm_id. Kudai
     * muktadha wa shamba hapa kungezuia usimamizi wa katalogi ya kimfumo
     * kwa sababu isiyohusiana nayo - ni sheria ile ile ya listAll na ya
     * FeedService.createFeedType.
     *
     * `manage_species` (V20) na si `view_dashboard` ya kusoma: kuandika
     * hapa kunaonekana kwa MASHAMBA YOTE, hivyo si kitendo cha kila
     * mwenye ruhusa ya kuona ripoti.
     *
     * HAKUNA updateSpecies inayoandamana nayo, na hilo ni chaguo:
     * `growthMonthsAvg` inakokotoa `expectedHarvestDate` ya KILA mzunguko
     * unaoielekea, ikiwemo ya mizunguko iliyokwisha anza. Kuihariri
     * kungebadilisha tarehe za utabiri zilizokwisha onyeshwa kwa
     * mkulima, kimyakimya - swali linalohitaji uamuzi wake mwenyewe, si
     * mutation ya kimya. Aina iliyoandikwa vibaya inaongezwa upya kwa
     * jina sahihi.
     */
    @Transactional
    public Species create(String name, Double growthMonthsAvg, Double avgHarvestWeightKg) {
        permissionChecker.require("manage_species");

        Species species = new Species();
        species.setName(requireAvailableName(name, null));
        species.setGrowthMonthsAvg(requirePositiveDecimal(
                growthMonthsAvg, GROWTH_MONTHS_SCALE, GROWTH_MONTHS_MAX, "Muda wa kukua (miezi)"));
        species.setAvgHarvestWeightKg(requirePositiveDecimal(
                avgHarvestWeightKg, HARVEST_WEIGHT_SCALE, HARVEST_WEIGHT_MAX, "Uzito wa wastani wa mavuno (kg)"));
        return speciesRepository.save(species);
    }

    /**
     * Jina lililopunguzwa nafasi tupu, likiwa halali na halijachukuliwa.
     *
     * Ukaguzi upo hapa - si kwenye bean validation - kwa sababu unahitaji
     * database, na kwa sababu jibu la "limechukuliwa?" linategemea hata
     * aina ZILIZOFUTWA (angalia
     * SpeciesRepository.countByNameIncludingDeleted).
     */
    private String requireAvailableName(String raw, Integer selfId) {
        String name = raw == null ? "" : raw.trim();

        if (name.isEmpty()) {
            throw new IllegalArgumentException("Jina la aina ya samaki linahitajika.");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Jina la aina ya samaki lisizidi herufi " + NAME_MAX_LENGTH + ".");
        }
        if (speciesRepository.countByNameIncludingDeleted(name, selfId) > 0) {
            throw new ConflictException("Aina ya samaki yenye jina hili tayari ipo.");
        }
        return name;
    }

    /**
     * Namba chanya iliyowekwa kwenye ukubwa wa safu husika.
     *
     * UKAGUZI NI MARA MBILI, na wa pili ndio wenye maana isiyoonekana:
     * thamani inakaguliwa ikiwa mbichi, KISHA baada ya kuwekwa scale.
     * `0.04` ni chanya, lakini kwa NUMERIC(4,1) ni `0.0` - aina yenye muda
     * wa kukua wa sifuri, ambayo CycleService.requireGrowthMonths
     * ITAIKATAA milele. Bila ukaguzi wa pili, katalogi ingekubali kubeba
     * aina isiyoweza KAMWE kutumika kwenye mzunguko, na kosa lingeonekana
     * mbali na chanzo chake.
     *
     * HALF_UP, si kukata: `.longValue()` ndiyo iliyokuwa ikigeuza miezi
     * 6.5 kuwa 6 (D-7). Kukata hapa kungerudisha hitilafu ile ile mahali
     * pengine - safari hii kwenye data yenyewe, si kwenye hesabu.
     *
     * Kikomo cha juu ni cha SAFU, si cha kibaolojia: NUMERIC(4,1)
     * ikizidishwa inatoa `numeric field overflow` ya PostgreSQL - CONFLICT
     * yenye sentensi isiyoeleweka badala ya ujumbe unaotaja uga.
     */
    private static BigDecimal requirePositiveDecimal(Double raw, int scale, BigDecimal max, String jina) {
        if (raw == null || raw <= 0) {
            throw new IllegalArgumentException("Thamani ya '" + jina + "' lazima iwe zaidi ya sifuri.");
        }
        if (raw.isNaN() || raw.isInfinite()) {
            throw new IllegalArgumentException("Thamani ya '" + jina + "' si namba halali.");
        }

        BigDecimal value = BigDecimal.valueOf(raw).setScale(scale, RoundingMode.HALF_UP);

        if (value.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Thamani ya '" + jina + "' ni ndogo mno - haiwezi kuwa chini ya "
                            + BigDecimal.ONE.movePointLeft(scale).toPlainString() + ".");
        }
        if (value.compareTo(max) > 0) {
            throw new IllegalArgumentException(
                    "Thamani ya '" + jina + "' haiwezi kuzidi " + max.toPlainString() + ".");
        }
        return value;
    }
}
