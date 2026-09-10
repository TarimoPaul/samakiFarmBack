package com.samaki.farm.asset.graphql;

import com.samaki.farm.asset.entity.Asset;
import com.samaki.farm.asset.entity.AssetCategory;
import com.samaki.farm.asset.services.AssetService;
import com.samaki.farm.farm.entity.Farm;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * GraphQL mapping pekee - logic iko AssetService.
 *
 * HAKUNA endpoint ya jumla (jumla ya shamba, jumla ya kampuni) kwa
 * makusudi: `assets` inarudisha mistari yenye shamba lake, na kuzijumlisha
 * ni kazi ya frontend. Backend ingelazimika kuchagua mgawanyo - kwa
 * shamba? kwa aina? kwa mwaka? - na kila skrini mpya ingedai endpoint
 * yake.
 */
@Controller
public class AssetResolver {

    private final AssetService assetService;

    public AssetResolver(AssetService assetService) {
        this.assetService = assetService;
    }

    @QueryMapping
    public List<Asset> assets() {
        return assetService.listAssets();
    }

    @QueryMapping
    public List<AssetCategory> assetCategories() {
        return assetService.listCategories();
    }

    /**
     * Mashamba ya mwombaji, kwa kichagua-shamba cha fomu ya kuandikisha.
     *
     * Ilikuwa haiwezekani kabla yake: `assets` inarudisha shamba la kila
     * mstari, hivyo kichagua-shamba kingeweza kumpa mwombaji shamba lake
     * BAADA tu ya shamba hilo kuwa na mali - na mali ya kwanza ndiyo
     * isiyoweza kuandikishwa. /api/farms si jibu: ni ya `manage_farms` na
     * inarudisha mashamba YOTE ya kampuni, ikiwemo asiyoyahusika nayo.
     */
    @QueryMapping
    public List<Farm> myFarms() {
        return assetService.listCallersFarms();
    }

    // Hoja tambulifu badala ya input type, kwa mfuatano ule ule wa
    // createSpecies/createFeedType.
    @MutationMapping
    public Asset createAsset(@Argument String name,
                             @Argument Integer farmId,
                             @Argument Double cost,
                             @Argument String acquiredDate,
                             @Argument String sizeLabel,
                             @Argument Integer assetCategoryId) {
        return assetService.createAsset(name, farmId, cost, acquiredDate, sizeLabel, assetCategoryId);
    }

    @MutationMapping
    public AssetCategory createAssetCategory(@Argument String name) {
        return assetService.createCategory(name);
    }
}
