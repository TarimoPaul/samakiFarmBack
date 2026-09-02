package com.samaki.farm.reminder.services;

import com.samaki.farm.reminder.entity.Reminder;
import com.samaki.farm.reminder.repository.ReminderRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Logi ya kutuma, ikiwa na MPAKA WAKE WA TRANSACTION.
 *
 * =====================================================================
 * KWA NINI NI BEAN YAKE, SI METHOD NDANI YA DISPATCH SERVICE
 *
 * Kila method hapa ni transaction YAKE FUPI inayojifunga papo hapo.
 * Tiki YENYEWE haina transaction hata kidogo, na hiyo ni sharti la
 * ustahimilivu: kama tiki nzima ingekuwa transaction moja, kikumbusho
 * kimoja kilichoshindwa mwishoni kingerudisha nyuma REKODI ZA VYOTE
 * vilivyokwisha tumwa - na tiki inayofuata ingevituma tena. Yaani
 * hitilafu moja ingegeuka kuwa SMS zilizojirudia kwa kila mtu.
 *
 * Spring inaweka transaction kupitia proxy, hivyo `this.claim(...)`
 * kutoka ndani ya darasa lile lile INGERUKA proxy na kutokuwa na
 * transaction kabisa. Ndiyo sababu hii ni bean tofauti inayodungwa
 * kwenye ReminderDispatchService, badala ya method binafsi.
 * =====================================================================
 */
@Component
public class ReminderSendLog {

    private final ReminderRepository reminderRepository;

    public ReminderSendLog(ReminderRepository reminderRepository) {
        this.reminderRepository = reminderRepository;
    }

    /**
     * Kudai ruhusa ya kutuma. `true` = ni yako, tuma; `false` = mtu
     * (au tiki iliyopita) tayari ameshughulikia hii - usiguse.
     *
     * Uamuzi ni wa database, si wa Java - angalia ReminderRepository.claim.
     */
    @Transactional
    public boolean claim(Integer taskId, String channel, UUID recipientId,
                         LocalDate reminderDate, Instant sendTime) {
        return reminderRepository.claim(taskId, channel, sendTime, recipientId, reminderDate) == 1;
    }

    /** Provider amekubali. `sentAt` ni saa ya MATOKEO, si ya tiki. */
    @Transactional
    public void recordSent(Integer taskId, String channel, UUID recipientId, LocalDate reminderDate) {
        reminderRepository.recordResult(taskId, channel, recipientId, reminderDate,
                Reminder.SENT, Instant.now());
    }

    /**
     * Provider ameshindwa. Rekodi INABAKI (haifutwi), hivyo tiki
     * inayofuata haitajaribu tena - angalia Reminder kwa hoja nzima.
     * `sent_at` inabaki null: hakuna kilichotumwa.
     */
    @Transactional
    public void recordFailed(Integer taskId, String channel, UUID recipientId, LocalDate reminderDate) {
        reminderRepository.recordResult(taskId, channel, recipientId, reminderDate,
                Reminder.FAILED, null);
    }
}
