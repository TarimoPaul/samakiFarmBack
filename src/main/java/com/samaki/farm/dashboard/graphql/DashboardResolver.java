package com.samaki.farm.dashboard.graphql;

import com.samaki.farm.dashboard.dto.DashboardDay;
import com.samaki.farm.dashboard.services.DashboardDayService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/** GraphQL mapping pekee - logic iko DashboardDayService. */
@Controller
public class DashboardResolver {

    private final DashboardDayService dashboardDayService;

    public DashboardResolver(DashboardDayService dashboardDayService) {
        this.dashboardDayService = dashboardDayService;
    }

    @QueryMapping
    public DashboardDay dashboardOnDate(@Argument String date) {
        return dashboardDayService.onDate(date);
    }
}
