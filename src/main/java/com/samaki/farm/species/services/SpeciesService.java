package com.samaki.farm.species.services;

import com.samaki.farm.auth.security.PermissionChecker;
import com.samaki.farm.species.entity.Species;
import com.samaki.farm.species.repository.SpeciesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Katalogi ya aina za samaki - KUSOMA pekee.
 *
 * Ilikuwa haifikiki kabisa kupitia API: hakuna query wala controller,
 * ilhali CreateCycleInput inadai speciesId (angalia
 * FRONTEND_BACKEND_AUDIT.md, D-3). Hivyo ukurasa wa kuunda mzunguko
 * haukuwa unajengeka - frontend haikuwa na njia ya kuorodhesha aina, na
 * kuandika 1 na 2 moja kwa moja kungevunjika mara aina mpya inapoongezwa.
 *
 * HAKUNA kuunda/kuhariri: aina zinatoka kwenye seed ya V1 na ni uamuzi wa
 * kimfumo, si kazi ya kila siku ya shamba.
 *
 * HAICHUJWI kwa shamba - `species` haina farm_id: ni katalogi moja
 * inayoshirikiwa na mashamba yote (angalia CycleService.create, ambapo
 * speciesId nayo haikaguliwi kwa shamba kwa sababu hiyo hiyo).
 */
@Service
public class SpeciesService {

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
     */
    @Transactional(readOnly = true)
    public List<Species> listAll() {
        permissionChecker.require("view_dashboard");
        return speciesRepository.findAll();
    }
}
