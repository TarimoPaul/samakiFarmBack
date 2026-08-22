package com.samaki.farm.common.notification;

/**
 * Abstraction ya kutuma SMS - inaruhusu kubadilisha provider (Africa's
 * Talking, AWS SNS, n.k. - angalia README "AWS Deployment") bila kugusa
 * logic ya OTP/reminders inayotumia hii.
 */
public interface SmsSender {
    void send(String phone, String message);
}
