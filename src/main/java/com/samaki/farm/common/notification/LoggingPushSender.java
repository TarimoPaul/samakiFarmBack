package com.samaki.farm.common.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * STUB - inaandika push kwenye logs badala ya kuituma kweli, sawasawa na
 * LoggingSmsSender.
 *
 * Inaruhusu scheduler ya vikumbusho kuendeshwa na kujaribiwa kabla ya kuwa
 * na akaunti/credentials za AWS Pinpoint. KWA SASA MTUMIAJI HAPATI PUSH
 * HALISI - badilisha hii na PinpointPushSender kabla ya production
 * (angalia README, "Kabla ya production").
 *
 * Logi ni WARN, si INFO, kwa sababu ile ile ya LoggingSmsSender: mfumo
 * unaodhani unatuma vikumbusho ilhali haufanyi hivyo lazima uonekane
 * kwenye logs za kawaida, si kufichwa nyuma ya kiwango cha debug.
 */
@Component
public class LoggingPushSender implements PushSender {

    private static final Logger logger = LoggerFactory.getLogger(LoggingPushSender.class);

    @Override
    public void send(String pushToken, String title, String body) {
        logger.warn("[PUSH-STUB - HAIJATUMWA KWELI] Kwenda token {}: {} - {}",
                mask(pushToken), title, body);
    }

    /**
     * Push token ni siri ya kifaa: mwenye token anaweza kutuma notification
     * kwa mtumiaji huyo. Logs zinasomwa na watu wengi kuliko database,
     * hivyo inaandikwa kwa kifupi - ya kutosha kutofautisha vifaa, si
     * kutosha kuitumia.
     */
    private String mask(String pushToken) {
        if (pushToken == null || pushToken.length() <= 6) {
            return "***";
        }
        return pushToken.substring(0, 6) + "...";
    }
}
