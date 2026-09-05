package com.samaki.farm.feed.entity;

import com.samaki.farm.common.entity.BaseEntity;
import com.samaki.farm.cycle.entity.Cycle;
import com.samaki.farm.user.entity.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;

@SQLRestriction("is_deleted = false")
@Entity
@Table(name = "feeding_logs")
@Data
@EqualsAndHashCode(callSuper = false, of = "logId")
@ToString(exclude = {"cycle", "recordedBy", "feedType"})
public class FeedingLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Integer logId;

    @ManyToOne
    @JoinColumn(name = "cycle_id")
    private Cycle cycle;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    // KUKAZWA: ilikuwa maandishi huru YANAYORUHUSU null. Ulishaji usiotaja
    // chakula hauwezi kupunguza salio la aina yoyote, hivyo ungeacha stoo
    // ikidai kilo ambazo samaki tayari wamekula (V16).
    @ManyToOne
    @JoinColumn(name = "feed_type_id", nullable = false)
    private FeedType feedType;

    @Column(name = "quantity_kg", nullable = false)
    private BigDecimal quantityKg;

    @ManyToOne
    @JoinColumn(name = "recorded_by_user_id")
    // MTU aliyerekodi, si uanachama - recorded_by_user_id inaelekea `users`.
    private User recordedBy;
}
