package com.samaki.farm.dailytask.graphql;

import com.samaki.farm.dailytask.dto.CompleteTaskInput;
import com.samaki.farm.dailytask.dto.DailyTaskStatusView;
import com.samaki.farm.dailytask.services.DailyTaskService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

/** GraphQL mapping pekee - logic iko DailyTaskService. */
@Controller
public class DailyTaskResolver {

    private final DailyTaskService dailyTaskService;

    public DailyTaskResolver(DailyTaskService dailyTaskService) {
        this.dailyTaskService = dailyTaskService;
    }

    /**
     * Kazi za mzunguko kwa siku moja, kila moja ikiwa na `done` yake -
     * ndio mkataba ambao Reminders itausoma.
     */
    @QueryMapping
    public List<DailyTaskStatusView> dailyTasks(@Argument Integer cycleId, @Argument String date) {
        return dailyTaskService.statusForCycle(cycleId, date);
    }

    @MutationMapping
    public DailyTaskStatusView completeTask(@Argument CompleteTaskInput input) {
        return dailyTaskService.complete(input);
    }
}
