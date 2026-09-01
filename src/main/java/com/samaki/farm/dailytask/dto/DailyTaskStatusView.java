package com.samaki.farm.dailytask.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * KIOLEZO cha kazi kikiwa kimeunganishwa na hali yake KWA SIKU MOJA.
 *
 * =====================================================================
 * HII NDIYO MKATABA UNAOSOMWA NA REMINDERS
 *
 * `done` ndiyo jibu kamili la swali "kazi hii bado inasubiri?" - ni
 * boolean, si tafsiri ya maandishi, kwa sababu ndicho kitakachoamua
 * kama mtu anapigiwa simu au la.
 *
 * Inatokana na sheria MOJA (angalia DailyTaskService.statusFor):
 *
 *     done = kuna rekodi ya task_completions kwa (task_id, tarehe)
 *            YENYE status = 'DONE'
 *
 * Kila kitu kingine ni OUTSTANDING - ikiwemo rekodi iliyopo lakini
 * ikiwa PENDING/MISSED/LATE. Hilo ni la makusudi: rekodi iliyoandikwa
 * MISSED bado ni kazi isiyofanyika, na Reminders inapaswa kuikumbusha.
 * =====================================================================
 *
 * `status` ni ile ile ya safu ya task_completions rekodi ikiwepo
 * (DONE/PENDING/MISSED/LATE), na "OUTSTANDING" pale isipokuwepo kabisa.
 * Ina maelezo zaidi kuliko `done`, na ndiyo sababu zote mbili zipo:
 * mteja anayeonyesha orodha anataka kutofautisha "haijaguswa" na
 * "imekosekana", ilhali Reminders inahitaji bendera moja isiyo na
 * utata.
 *
 * `scheduledTime` inarudishwa ili mtumiaji wa mkataba huu aweze
 * kukokotoa "overdue" (outstanding NA saa imepita) mwenyewe. HAIKOKOTOLEWI
 * hapa kwa sababu inahitaji kanda ya saa - Dodoma ni EAT (UTC+3) - na
 * uamuzi huo ni wa Reminders, si wa module hii.
 *
 * `assignedRoleName` inaweza kuwa null, na kwa data iliyopo NI null
 * kila mahali: CycleService.createDefaultTasks haiweki assigned_role_id
 * kabisa. Reminders itahitaji uamuzi kuhusu hilo (angalia ripoti).
 */
public record DailyTaskStatusView(
        Integer taskId,
        Integer cycleId,
        String taskType,
        LocalTime scheduledTime,
        String frequency,
        String assignedRoleName,
        LocalDate date,
        String status,
        boolean done,
        Instant completedAt,
        String completedByName,
        String notes
) {}
