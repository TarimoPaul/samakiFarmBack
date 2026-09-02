package com.samaki.farm.common.notification;

/**
 * Abstraction ya kutuma push notification - dada ya SmsSender.
 *
 * Ipo ili module ya vikumbusho isijue provider hata kidogo: `users.push_token`
 * (FCM/Web-push, angalia V1) inapelekwa hapa, na nani anayeituma kweli - AWS
 * Pinpoint kwenye mpango wa deploy - ni bean inayochaguliwa wakati wa
 * kuanzisha app, si tawi la `if` ndani ya logic ya kutuma.
 *
 * NI HII inayofanya tests za vikumbusho ziwezekane bila mtandao: test
 * inaweka mock mahali pa bean hii na kuthibitisha KILE KILICHOITWA, badala
 * ya kutuma notification halisi kwa mtu.
 *
 * `title` na `body` ni tofauti kwa makusudi (si ujumbe mmoja kama SMS):
 * ndivyo push payload ya FCM/Pinpoint inavyoundwa, na kuunganisha vyote
 * viwili hapa kungeilazimu implementation kuvitenganisha kwa kubahatisha.
 */
public interface PushSender {
    void send(String pushToken, String title, String body);
}
