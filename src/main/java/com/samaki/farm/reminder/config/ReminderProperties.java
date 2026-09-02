package com.samaki.farm.reminder.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Usanidi wa vikumbusho - `app.reminders.*` (angalia application.yml).
 *
 * Kila kitu hapa ni cha KUBADILISHWA BILA KUJENGA UPYA: saa za kutuma,
 * kanda ya saa, saa za kimya, na swichi za kila njia. Sababu ni ya
 * vitendo: mfumo unaotuma SMS kwa watu halisi lazima uweze kuzimwa au
 * kupunguzwa haraka kutoka kwenye mazingira, si kwa deploy mpya.
 */
@Component
@ConfigurationProperties(prefix = "app.reminders")
@Data
public class ReminderProperties {

    /**
     * Swichi kuu. Ikiwa false, bean ya ReminderScheduler haitengenezwi
     * kabisa (angalia @ConditionalOnProperty huko) - hivyo majaribio
     * yanaweza kuita huduma moja kwa moja bila tiki ya saa ikiingilia
     * data yao katikati.
     */
    private boolean enabled = true;

    /**
     * Chaguo-msingi: saa 1 asubuhi, saa 6 mchana, saa 11 jioni (EAT) -
     * yaani baada ya kila kazi ya kiolezo cha CycleService (07:00 kulisha,
     * 08:00 maji, 17:00 kulisha) kuwa imeiva. Kikumbusho kinachotangulia
     * saa ya kazi si kikumbusho - ni kero.
     */
    private String cron = "0 0 7,12,17 * * *";

    /**
     * DODOMA NI EAT (UTC+3). Server inaweza kuwa popote - RDS/ECS kwa
     * kawaida ni UTC - hivyo saa ya cron LAZIMA itajwe kwa kanda, la
     * sivyo "saa 1 asubuhi" ingekuwa saa 10 alfajiri kwa mkulima.
     */
    private String zone = "Africa/Nairobi";

    /** Hakuna kutuma kuanzia hapa... */
    private LocalTime quietStart = LocalTime.of(21, 0);

    /** ...hadi hapa. Watu wanalala; SMS ya saa nane usiku si kikumbusho. */
    private LocalTime quietEnd = LocalTime.of(6, 0);

    private boolean smsEnabled = true;

    private boolean pushEnabled = true;

    public ZoneId zoneId() {
        return ZoneId.of(zone);
    }

    /**
     * Je, saa hii ni ya kimya?
     *
     * Dirisha linaruhusiwa KUVUKA USIKU wa manane (21:00 -> 06:00), ndio
     * hali ya kawaida - hivyo ulinganisho ni wa matawi mawili. Dirisha la
     * kawaida (mfano 12:00 -> 14:00) ni `start < end`; lililovuka ni
     * `start > end` na linakuwa kweli pande MBILI za usiku wa manane.
     *
     * `start == end` inamaanisha HAKUNA saa za kimya kabisa - ndiyo njia
     * ya kuzizima bila kuongeza swichi nyingine.
     */
    public boolean isQuietAt(LocalTime time) {
        if (quietStart == null || quietEnd == null || quietStart.equals(quietEnd)) {
            return false;
        }
        if (quietStart.isBefore(quietEnd)) {
            return !time.isBefore(quietStart) && time.isBefore(quietEnd);
        }
        return !time.isBefore(quietStart) || time.isBefore(quietEnd);
    }
}
