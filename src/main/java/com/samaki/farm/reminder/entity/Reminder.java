package com.samaki.farm.reminder.entity;

import com.samaki.farm.dailytask.entity.DailyTask;
import com.samaki.farm.user.entity.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.time.Instant;
import java.time.LocalDate;

/**
 * LOGI YA KUTUMA - "mtu HUYU alikumbushwa kuhusu kazi HII, kwa njia HII,
 * siku HII".
 *
 * =====================================================================
 * SI RATIBA - NI RUHUSA YA KUTUMA
 *
 * Jina "reminders" linadanganya kidogo: rekodi HAIPANGI kikumbusho cha
 * baadaye. Inaandikwa na scheduler MARA MOJA KABLA ya kutuma, na
 * UNIQUE(task_id, reminder_date, recipient_user_id, channel) ya V12
 * ndiyo inayoamua kama kutuma kunaruhusiwa kabisa:
 *
 *   INSERT imefaulu  -> hakuna aliyekwisha kutuma; tuma sasa.
 *   INSERT imegongana -> tayari imetumwa (au imejaribiwa); usiguse.
 *
 * Mpangilio huo (andika kwanza, tuma baadaye) ni wa MAKUSUDI. Ungekuwa
 * kinyume - tuma kisha andika - tiki iliyokatika katikati ingemwacha
 * mtu ameshapigiwa bila rekodi, na tiki inayofuata ingempigia tena.
 * Kwa SMS, "mara mbili" ni gharama halisi kwa kila ujumbe.
 *
 * HAKUNA KUJARIBU TENA baada ya kushindwa. Rekodi ya FAILED inabaki, na
 * UNIQUE inazuia tiki inayofuata kuijaribu upya. Hii ni bei ya kuchagua
 * "isitumwe mara mbili" badala ya "lazima itumwe": provider aliyeharibika
 * angepigiwa kila tiki, na namba ya simu isiyo sahihi ingezalisha gharama
 * milele. FAILED inaonekana kwenye jedwali kwa mtu wa kuiangalia - ndiyo
 * njia ya kujua, badala ya kujaribu kimyakimya.
 * =====================================================================
 *
 * HAIRITHI BaseEntity, sababu ile ile ya TaskCompletion: V2 iliongeza safu
 * za audit/soft-delete kwenye majedwali mengine na kuliruka hili, hivyo
 * kurithi kungefanya `ddl-auto: validate` ikatae kuanza. Kwa vitendo
 * hazikosekani - `send_time` na `sent_at` ndizo audit ya rekodi hii, na
 * logi ya kutuma haipaswi kufutwa hata kidogo.
 */
@Entity
@Table(name = "reminders")
@Data
@ToString(exclude = {"task", "recipient"})
public class Reminder {

    /** Njia - orodha ni ya CHECK ya V1, si mpya. Provider ni undani wa Java. */
    public static final String SMS = "SMS";
    public static final String PUSH = "PUSH";

    /** Imeandikwa, bado haijatumwa - hali ya rekodi mara tu baada ya kudaiwa. */
    public static final String PENDING = "PENDING";
    public static final String SENT = "SENT";
    public static final String FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reminder_id")
    private Integer reminderId;

    @ManyToOne
    @JoinColumn(name = "task_id")
    private DailyTask task;

    @Column(name = "channel", nullable = false)
    private String channel;

    /**
     * Saa ya TIKI iliyodai rekodi hii - yaani NIA. Ni safu ya V1 (NOT NULL),
     * na inatofautiana na `sentAt` ambayo ni MATOKEO: rekodi ya FAILED ina
     * sendTime lakini haina sentAt.
     */
    @Column(name = "send_time", nullable = false)
    private Instant sendTime;

    @Column(name = "status", nullable = false)
    private String status = PENDING;

    /** MTU aliyekumbushwa - si uanachama; wapokeaji wanaweza kuwa wengi kwa kazi moja. */
    @ManyToOne
    @JoinColumn(name = "recipient_user_id", nullable = false)
    private User recipient;

    /**
     * SIKU inayokumbushwa. Ni sehemu ya UNIQUE kwa sababu kiolezo cha
     * daily_tasks kinajirudia kila siku (angalia TaskCompletion) - bila
     * tarehe, kikumbusho cha jana kingezuia cha leo milele.
     */
    @Column(name = "reminder_date", nullable = false)
    private LocalDate reminderDate;

    /** Saa ya kufaulu. Null mpaka provider akubali - na kwenye FAILED inabaki null. */
    @Column(name = "sent_at")
    private Instant sentAt;
}
