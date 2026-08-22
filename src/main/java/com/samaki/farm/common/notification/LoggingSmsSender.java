package com.samaki.farm.common.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * STUB - inaandika SMS kwenye logs badala ya kuituma kweli. Hii inaruhusu
 * OTP flow (PasswordResetService) kufanya kazi/kujaribiwa kabla ya kuwa na
 * akaunti/credentials za Africa's Talking (au provider nyingine). BADILISHA
 * hii na implementation halisi (AfricasTalkingSmsSender au sawa) kabla ya
 * production - kwa sasa mtumiaji HAPATI SMS halisi.
 */
@Component
public class LoggingSmsSender implements SmsSender {

    private static final Logger logger = LoggerFactory.getLogger(LoggingSmsSender.class);

    @Override
    public void send(String phone, String message) {
        logger.warn("[SMS-STUB - HAIJATUMWA KWELI] Kwenda {}: {}", phone, message);
    }
}
