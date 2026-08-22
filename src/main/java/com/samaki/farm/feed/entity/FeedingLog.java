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
@ToString(exclude = {"cycle", "recordedBy"})
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

    @Column(name = "feed_type")
    private String feedType;

    @Column(name = "quantity_kg", nullable = false)
    private BigDecimal quantityKg;

    @ManyToOne
    @JoinColumn(name = "recorded_by_user_id")
    // MTU aliyerekodi, si uanachama - recorded_by_user_id inaelekea `users`.
    private User recordedBy;
}
