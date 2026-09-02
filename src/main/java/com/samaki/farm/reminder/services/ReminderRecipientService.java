package com.samaki.farm.reminder.services;

import com.samaki.farm.farmuser.repository.FarmUserRepository;
import com.samaki.farm.user.entity.User;
import com.samaki.farm.user.entity.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * WAPOKEAJI: nani anakumbushwa kuhusu kazi za shamba hili?
 *
 * Jibu ni WENYE RUHUSA YA `mark_task_done` KWENYE SHAMBA HILO - ile ile
 * ruhusa DailyTaskService.complete inayodai ili mtu aweze kufunga kazi.
 * Kumkumbusha mtu asiyeweza kuifunga kungekuwa kumtaka afanye kitu
 * ambacho mfumo utamkatalia.
 *
 * SI assignee: `daily_tasks.assigned_role_id` ni NULL kwenye kila kazi
 * inayozalishwa (angalia CycleService.createDefaultTasks na test ya
 * Kundi D "assignedRoleName ni null"), hivyo hakuna mtu aliyeteuliwa wa
 * kumfuata. Uamuzi huu umeandikwa kwenye batch ya Reminders na
 * umesainiwa - si wa kubuniwa hapa.
 *
 * KWA SHAMBA MOJA kwa wakati mmoja. Scheduler inapitia mashamba kimoja
 * kimoja na kuita hii kwa kila moja, hivyo hakuna hatua ambapo wapokeaji
 * wa mashamba mawili wako kwenye orodha moja - ndicho kinachozuia SMS ya
 * shamba A kumfikia mtu wa shamba B.
 */
@Service
public class ReminderRecipientService {

    /** Ruhusa ya kufunga kazi - hivyo ndiyo ya kukumbushwa juu yake. */
    public static final String REMINDED_PERMISSION = "mark_task_done";

    private final FarmUserRepository farmUserRepository;

    public ReminderRecipientService(FarmUserRepository farmUserRepository) {
        this.farmUserRepository = farmUserRepository;
    }

    @Transactional(readOnly = true)
    public List<User> forFarm(Integer farmId) {
        return farmUserRepository.findMembersWithPermission(
                farmId, REMINDED_PERMISSION, UserStatus.ACTIVE);
    }
}
