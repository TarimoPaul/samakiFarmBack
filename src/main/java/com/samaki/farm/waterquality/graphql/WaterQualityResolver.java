package com.samaki.farm.waterquality.graphql;

import com.samaki.farm.waterquality.dto.LogWaterQualityInput;
import com.samaki.farm.waterquality.entity.WaterQualityLog;
import com.samaki.farm.waterquality.services.WaterQualityService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

/** GraphQL mapping pekee - logic iko WaterQualityService. */
@Controller
public class WaterQualityResolver {

    private final WaterQualityService waterQualityService;

    public WaterQualityResolver(WaterQualityService waterQualityService) {
        this.waterQualityService = waterQualityService;
    }

    @QueryMapping
    public List<WaterQualityLog> waterQualityLogs(@Argument Integer unitId) {
        return waterQualityService.listLogs(unitId);
    }

    @SchemaMapping(typeName = "WaterQualityLog", field = "recordedByName")
    public String recordedByName(WaterQualityLog log) {
        return log.getRecordedBy() == null ? null : log.getRecordedBy().getName();
    }

    @MutationMapping
    public WaterQualityLog logWaterQuality(@Argument LogWaterQualityInput input) {
        return waterQualityService.log(input);
    }
}
