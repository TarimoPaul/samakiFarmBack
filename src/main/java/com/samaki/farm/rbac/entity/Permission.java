package com.samaki.farm.rbac.entity;

import org.hibernate.annotations.SQLRestriction;

import com.samaki.farm.common.entity.BaseEntity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@SQLRestriction("is_deleted = false")
@Entity
@Table(name = "permissions")
@Data
@EqualsAndHashCode(callSuper = false)
public class Permission extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "permission_id")
    private Integer permissionId;

    @Column(unique = true, nullable = false)
    private String code; // view_finance, edit_cycle, manage_users, mark_task_done, view_dashboard, manage_units

    // Module/group (kama Lsms Permission entity) - inaruhusu ruhusa kuonyeshwa
    // kwa mpangilio wa kihierarkia (moduli -> kikundi) kwenye UI ya kutengeneza roles.
    @Column(nullable = false)
    private String module; // mfano: FARM, FINANCE, UAA

    @Column(name = "group_name")
    private String groupName;

    private String description;
}
