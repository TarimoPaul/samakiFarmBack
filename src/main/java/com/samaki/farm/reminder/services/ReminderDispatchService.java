package com.samaki.farm.reminder.services;

import com.samaki.farm.common.notification.PushSender;
import com.samaki.farm.common.notification.SmsSender;
import com.samaki.farm.dailytask.entity.DailyTask;
import com.samaki.farm.farm.entity.Farm;
import com.samaki.farm.farm.repository.FarmRepository;
import com.samaki.farm.reminder.config.ReminderProperties;
import com.samaki.farm.reminder.entity.Reminder;
import com.samaki.farm.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * KUTUMA vikumbusho: kazi zilizobaki -> wapokeaji -> njia zao zote.
 *
 * =====================================================================
 * MPANGILIO WA TIKI
 *
 *   kwa kila SHAMBA:
 *       kazi zilizobaki ambazo saa yake imepita   (OutstandingTaskSelector)
 *       wenye 'mark_task_done' kwenye shamba hilo (ReminderRecipientService)
 *       kwa kila (kazi, mtu, njia):
 *           dai rekodi   -> ikigongana, RUKA (tayari imetumwa)
 *           tuma         -> SENT au FAILED
 *
 * KWA KILA SHAMBA PEKEE YAKE, si query moja ya kazi zote za mfumo
 * ikichujwa baadaye. Ndicho kinachozuia kuvuja: kazi za shamba jingine
 * hazifiki mkononi hata kwa muda mfupi, hivyo hitilafu ya kichujio
 * haiwezi kuzifichua.
 *
 * HAKUNA @Transactional HAPA - kwa makusudi kabisa. Angalia
 * ReminderSendLog: tiki nzima ikiwa transaction moja, kushindwa kwa
 * kikumbusho kimoja kungefuta rekodi za vyote vilivyokwisha tumwa, na
 * tiki inayofuata ingevituma tena.
 *
 * NJIA MBILI, SI MBADALA. Mtu mwenye simu NA push_token anapata ZOTE
 * MBILI. Si "jaribu push, ukishindwa tuma SMS": uamuzi huo umesainiwa
 * kwenye batch, na sababu yake ni ya shambani - simu ya mkulima inaweza
 * kuwa haina mtandao wa data (push haifiki) ilhali SMS inafika, na
 * kinyume chake pale bando limeisha.
 * =====================================================================
 */
@Service
public class ReminderDispatchService {

    private static final Logger logger = LoggerFactory.getLogger(ReminderDispatchService.class);

    /** Kichwa cha push. SMS haina kichwa - ni ujumbe mmoja. */
    private static final String PUSH_TITLE = "Kumbusho la kazi";

    private final OutstandingTaskSelector selector;
    private final ReminderRecipientService recipients;
    private final ReminderSendLog sendLog;
    private final FarmRepository farmRepository;
    private final SmsSender smsSender;
    private final PushSender pushSender;
    private final ReminderProperties properties;

    public ReminderDispatchService(OutstandingTaskSelector selector,
                                   ReminderRecipientService recipients,
                                   ReminderSendLog sendLog,
                                   FarmRepository farmRepository,
                                   SmsSender smsSender,
                                   PushSender pushSender,
                                   ReminderProperties properties) {
        this.selector = selector;
        this.recipients = recipients;
        this.sendLog = sendLog;
        this.farmRepository = farmRepository;
        this.smsSender = smsSender;
        this.pushSender = pushSender;
        this.properties = properties;
    }

    /** Muhtasari wa tiki - unaingia kwenye logs, na tests zinausoma. */
    public record TickResult(int farms, int tasks, int sent, int failed, int skipped) {}

    /**
     * TIKI KAMILI: mashamba yote, kwa saa ya sasa ya EAT.
     *
     * Hii ndiyo pekee inayosoma saa ya mfumo. Kila kitu kingine kinapokea
     * tarehe na saa kama parameter, ndiyo maana kinajaribika bila clock.
     */
    public TickResult runTick() {
        ZonedDateTime now = ZonedDateTime.now(properties.zoneId());
        return runTickAt(now.toLocalDate(), now.toLocalTime());
    }

    /**
     * Tiki ya mashamba yote kwa tarehe/saa iliyotolewa.
     *
     * SAA ZA KIMYA zinakaguliwa HAPA, si kwenye cron. Cron inaweza
     * kubadilishwa kwenye mazingira (`app.reminders.cron`), na ratiba
     * iliyowekwa vibaya isingepaswa kuwaamsha watu saa nane usiku. Ni
     * ukaguzi wa mwisho, si wa kwanza.
     */
    public TickResult runTickAt(LocalDate date, LocalTime asOf) {
        if (properties.isQuietAt(asOf)) {
            logger.info("Vikumbusho: {} ni ndani ya saa za kimya ({}-{}) - tiki imerukwa.",
                    asOf, properties.getQuietStart(), properties.getQuietEnd());
            return new TickResult(0, 0, 0, 0, 0);
        }

        Counters counters = new Counters();
        List<Farm> farms = farmRepository.findAll();
        for (Farm farm : farms) {
            try {
                dispatchForFarm(farm.getFarmId(), date, asOf, counters);
            } catch (RuntimeException e) {
                // Shamba moja lililoharibika (data mbovu, query iliyokataliwa)
                // HALIZUII mengine. Tiki inayoacha mashamba mengine bila
                // vikumbusho kwa sababu ya moja ni mbaya zaidi kuliko
                // hitilafu yenyewe.
                logger.error("Vikumbusho: shamba {} limeshindikana - linarukwa.", farm.getFarmId(), e);
            }
        }
        return counters.toResult(farms.size());
    }

    /**
     * SHAMBA MOJA - ndiyo njia ambayo majaribio yanaitumia, kwa sababu
     * inachukua tarehe na saa moja kwa moja.
     */
    public TickResult runForFarm(Integer farmId, LocalDate date, LocalTime asOf) {
        Counters counters = new Counters();
        dispatchForFarm(farmId, date, asOf, counters);
        return counters.toResult(1);
    }

    private void dispatchForFarm(Integer farmId, LocalDate date, LocalTime asOf, Counters counters) {
        List<DailyTask> tasks = selector.dueForReminder(farmId, date, asOf);
        if (tasks.isEmpty()) {
            return;
        }
        List<User> people = recipients.forFarm(farmId);
        if (people.isEmpty()) {
            // Si hitilafu: shamba linaweza kuwa halina mtu mwenye ruhusa
            // bado. Ni onyo kwa sababu kazi zilizobaki zipo na hakuna
            // anayeambiwa - hali ambayo msimamizi anapaswa kuiona.
            logger.warn("Vikumbusho: shamba {} lina kazi {} zilizobaki lakini hakuna mwenye '{}'.",
                    farmId, tasks.size(), ReminderRecipientService.REMINDED_PERMISSION);
            return;
        }

        Instant sendTime = Instant.now();
        for (DailyTask task : tasks) {
            counters.tasks++;
            String message = message(task, date);
            for (User person : people) {
                if (properties.isSmsEnabled() && hasText(person.getPhone())) {
                    attempt(task, person, Reminder.SMS, date, sendTime, counters,
                            () -> smsSender.send(person.getPhone(), message));
                }
                if (properties.isPushEnabled() && hasText(person.getPushToken())) {
                    attempt(task, person, Reminder.PUSH, date, sendTime, counters,
                            () -> pushSender.send(person.getPushToken(), PUSH_TITLE, message));
                }
            }
        }
    }

    /**
     * Kikumbusho KIMOJA: dai, tuma, andika matokeo.
     *
     * Hitilafu ya provider INAKAMATWA hapa na kuandikwa, kisha
     * inaachwa. Mtu mmoja asiyefikika hapaswi kuwazuia wenzake wa
     * shamba lile lile kupata vikumbusho vyao - ndilo sharti la
     * "ustahimilivu" la batch hii.
     */
    private void attempt(DailyTask task, User person, String channel, LocalDate date,
                         Instant sendTime, Counters counters, Runnable dispatch) {
        Integer taskId = task.getTaskId();
        java.util.UUID recipientId = person.getUserId();

        if (!sendLog.claim(taskId, channel, recipientId, date, sendTime)) {
            counters.skipped++;
            logger.debug("Vikumbusho: ({}, {}, {}, {}) tayari ipo - imerukwa.",
                    taskId, date, recipientId, channel);
            return;
        }

        try {
            dispatch.run();
            sendLog.recordSent(taskId, channel, recipientId, date);
            counters.sent++;
        } catch (RuntimeException e) {
            counters.failed++;
            logger.error("Vikumbusho: {} kwa mtumiaji {} kuhusu kazi {} imeshindwa.",
                    channel, recipientId, taskId, e);
            sendLog.recordFailed(taskId, channel, recipientId, date);
        }
    }

    /**
     * Ujumbe ni wa KISWAHILI na unataja kazi, saa yake, na tarehe -
     * vitatu vinavyomwezesha mpokeaji kujua HASA kipi kinachomsubiri
     * bila kufungua app.
     */
    private String message(DailyTask task, LocalDate date) {
        return "Kumbusho: kazi '" + task.getTaskType() + "' ya saa " + task.getScheduledTime()
                + " ya tarehe " + date + " bado haijafanyika.";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** Hesabu za tiki. Ni ya ndani: TickResult ndiyo inayotoka nje. */
    private static final class Counters {
        private int tasks;
        private int sent;
        private int failed;
        private int skipped;

        private TickResult toResult(int farms) {
            return new TickResult(farms, tasks, sent, failed, skipped);
        }
    }
}
