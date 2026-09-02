package com.samaki.farm.reminder.repository;

import com.samaki.farm.reminder.entity.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReminderRepository extends JpaRepository<Reminder, Integer> {

    /**
     * KUDAI ruhusa ya kutuma - INSERT inayoshindwa KIMYA kama mtu huyu
     * tayari amekumbushwa kuhusu kazi hii, kwa njia hii, siku hii.
     *
     * Inarudisha 1 (imedaiwa - tuma sasa) au 0 (tayari ipo - usiguse).
     *
     * `ON CONFLICT DO NOTHING` badala ya kusoma-kisha-kuandika kwa
     * makusudi: ukaguzi wa Java ungeweza kupitwa na tiki mbili
     * zinazoendeshwa kwa wakati mmoja (instance mbili, au tiki
     * iliyochelewa ikikutana na inayofuata), na zote mbili zingepita.
     * Hapa uamuzi ni WA DATABASE, wa atomic, kwenye UNIQUE ile ile ya V12.
     *
     * Ni njia YA MOJA KWA MOJA (native), si `save()`, kwa sababu ile ile:
     * `save()` ingetupa DataIntegrityViolationException kwenye kugongana,
     * na kuikamata ndani ya transaction inayoendelea kungeiacha
     * imewekwa rollback-only - yaani kikumbusho kimoja kilichojirudia
     * kingeharibu tiki nzima.
     */
    @Modifying
    @Query(value = """
            INSERT INTO reminders (task_id, channel, send_time, status, recipient_user_id, reminder_date)
            VALUES (:taskId, :channel, :sendTime, 'PENDING', :recipientId, :reminderDate)
            ON CONFLICT (task_id, reminder_date, recipient_user_id, channel) DO NOTHING
            """, nativeQuery = true)
    int claim(@Param("taskId") Integer taskId,
              @Param("channel") String channel,
              @Param("sendTime") Instant sendTime,
              @Param("recipientId") UUID recipientId,
              @Param("reminderDate") LocalDate reminderDate);

    /**
     * Matokeo ya kutuma yanaandikwa kwenye rekodi ILE ILE iliyodaiwa -
     * inatafutwa kwa ufunguo wa asili (si kwa reminder_id), kwa sababu
     * `claim` hapo juu hairudishi id: INSERT ya `@Modifying` inarudisha
     * hesabu ya safu pekee.
     */
    @Modifying
    @Query(value = """
            UPDATE reminders SET status = :status, sent_at = :sentAt
            WHERE task_id = :taskId AND reminder_date = :reminderDate
              AND recipient_user_id = :recipientId AND channel = :channel
            """, nativeQuery = true)
    int recordResult(@Param("taskId") Integer taskId,
                     @Param("channel") String channel,
                     @Param("recipientId") UUID recipientId,
                     @Param("reminderDate") LocalDate reminderDate,
                     @Param("status") String status,
                     @Param("sentAt") Instant sentAt);

    Optional<Reminder> findByTask_TaskIdAndReminderDateAndRecipient_UserIdAndChannel(
            Integer taskId, LocalDate reminderDate, UUID recipientUserId, String channel);

    List<Reminder> findByReminderDateOrderByReminderIdAsc(LocalDate reminderDate);

    long countByReminderDate(LocalDate reminderDate);
}
