package com.samaki.farm.reminder.services;

import com.samaki.farm.dailytask.entity.DailyTask;
import com.samaki.farm.dailytask.repository.DailyTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * UCHAGUZI: ni kazi zipi za shamba hili zinazostahili kikumbusho leo?
 *
 * =====================================================================
 * KWA NINI NI DARASA LAKE, SI SEHEMU YA SCHEDULER
 *
 * Hii ni method ya kawaida inayochukua shamba, tarehe, na saa - HAINA
 * saa ya mfumo ndani yake, na haina @Scheduled popote. Ndicho
 * kinachoifanya ijaribike: test inaweza kuuliza "saa 08:30 ya tarehe
 * fulani, ni zipi zinazostahili?" na kupata jibu la uhakika, bila
 * kusubiri saa halisi ifike wala kudanganya clock ya JVM.
 *
 * ReminderScheduler ndiyo inayojua saa ya sasa (angalia hapo), na
 * inaipeleka hapa kama parameter. Mgawanyo huo ndio unaotenganisha
 * "sheria" na "lini".
 * =====================================================================
 */
@Service
public class OutstandingTaskSelector {

    private final DailyTaskRepository taskRepository;

    public OutstandingTaskSelector(DailyTaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    /**
     * Kazi ZOTE za shamba ambazo bado hazijafanyika siku hiyo.
     *
     * Sheria ni ya Task Completions, si mpya hapa - angalia
     * DailyTaskRepository.findOutstandingForFarm. Kazi iliyofungwa (DONE)
     * HAIRUDI hapa kabisa; rekodi ya PENDING/MISSED/LATE inarudi, kwa
     * sababu kazi isiyofanyika ni kazi isiyofanyika.
     */
    @Transactional(readOnly = true)
    public List<DailyTask> outstanding(Integer farmId, LocalDate date) {
        return taskRepository.findOutstandingForFarm(farmId, date);
    }

    /**
     * Zilizobaki AMBAZO SAA YAKE TAYARI IMEPITA - ndizo zinazokumbushwa.
     *
     * `asOf` ni saa ya ndani ya EAT (angalia ReminderProperties.zone), na
     * kichujio ni `scheduledTime <= asOf`. Bila hiki, tiki ya saa 1
     * asubuhi ingemkumbusha mtu kuhusu kulisha kwa saa 11 jioni - kazi
     * ambayo bado haijachelewa hata kidogo, na ambayo kumkumbusha kwake
     * kunafundisha kupuuza vikumbusho vyote.
     *
     * Kichujio kiko hapa (Java), si kwenye query, kwa makusudi: kazi za
     * shamba moja ni chache (violezo vitatu kwa mzunguko), na kuweka
     * sheria mbili tofauti - "haijafanyika" na "saa imepita" - kwenye
     * SQL moja kungefanya query iliyoshikwa na tests iwe ngumu kusoma
     * bila kupata chochote.
     *
     * Kiolezo kisicho na `scheduled_time` (schema inairuhusu kuwa NOT
     * NULL, lakini hii ni ulinzi wa kawaida) kinaachwa nje: hakuna saa ya
     * kulinganisha, hivyo hakiwezi kuwa kimechelewa.
     */
    @Transactional(readOnly = true)
    public List<DailyTask> dueForReminder(Integer farmId, LocalDate date, LocalTime asOf) {
        return outstanding(farmId, date).stream()
                .filter(task -> task.getScheduledTime() != null)
                .filter(task -> !task.getScheduledTime().isAfter(asOf))
                .toList();
    }
}
