package com.samaki.farm.reminder.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * KICHOCHEO cha saa - na hakuna kingine.
 *
 * =====================================================================
 * DARASA HILI HALINA LOGIC HATA MOJA
 *
 * Ni method moja inayoita ReminderDispatchService.runTick(). Sheria zote
 * - nani, nini, lini, mara ngapi - ziko kwenye huduma zinazojaribika bila
 * kusubiri saa. Ndiyo maana @Scheduled iko hapa peke yake: kitu chochote
 * kilichoandikwa ndani ya method yenye @Scheduled kinakuwa kigumu
 * kujaribiwa mara moja, kwa sababu njia pekee ya kukifikia ni kusubiri
 * cron ifike.
 * =====================================================================
 *
 * `zone` ni ya LAZIMA, si mapambo: server ya production (ECS/RDS) kwa
 * kawaida iko UTC, hivyo cron isiyo na kanda ingempigia mkulima wa
 * Dodoma saa 10 alfajiri. Angalia ReminderProperties.zone.
 *
 * @ConditionalOnProperty inaifanya bean hii ISITENGENEZWE kabisa pale
 * `app.reminders.enabled=false` - ndivyo majaribio yanavyoendeshwa
 * (angalia application-test.yml). Bila hiyo, tiki ingeweza kuamka
 * katikati ya test na kuandika rekodi za `reminders` ambazo test
 * haikuziomba - na hitilafu hiyo ingeonekana mara moja kwa mia,
 * ikitegemea saa.
 */
@Component
@ConditionalOnProperty(name = "app.reminders.enabled", havingValue = "true", matchIfMissing = true)
public class ReminderScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ReminderScheduler.class);

    private final ReminderDispatchService dispatchService;

    public ReminderScheduler(ReminderDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @Scheduled(cron = "${app.reminders.cron}", zone = "${app.reminders.zone}")
    public void tick() {
        ReminderDispatchService.TickResult result = dispatchService.runTick();
        logger.info("Tiki ya vikumbusho: mashamba {}, kazi {}, zimetumwa {}, zimeshindwa {}, "
                        + "zimerukwa (tayari zipo) {}.",
                result.farms(), result.tasks(), result.sent(), result.failed(), result.skipped());
    }
}
