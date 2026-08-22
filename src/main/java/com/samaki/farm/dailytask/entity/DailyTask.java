package com.samaki.farm.dailytask.entity;

import org.hibernate.annotations.SQLRestriction;

import com.samaki.farm.common.entity.BaseEntity;
import com.samaki.farm.cycle.entity.Cycle;
import com.samaki.farm.rbac.entity.Role;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalTime;

@SQLRestriction("is_deleted = false")
@Entity
@Table(name = "daily_tasks")
@Data
@EqualsAndHashCode(callSuper = false)
public class DailyTask extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "task_id")
    private Integer taskId;

    @ManyToOne
    @JoinColumn(name = "cycle_id")
    private Cycle cycle;

    @Column(name = "task_type")
    private String taskType; // Kulisha-Asubuhi / Kulisha-Jioni / Kuangalia Maji

    @Column(name = "scheduled_time")
    private LocalTime scheduledTime;

    private String frequency = "DAILY";

    @ManyToOne
    @JoinColumn(name = "assigned_role_id")
    private Role assignedRole;
}
