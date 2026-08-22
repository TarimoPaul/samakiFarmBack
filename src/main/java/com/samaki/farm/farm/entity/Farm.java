package com.samaki.farm.farm.entity;

import org.hibernate.annotations.SQLRestriction;

import com.samaki.farm.common.entity.BaseEntity;
import com.samaki.farm.farmuser.entity.FarmUser;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@SQLRestriction("is_deleted = false")
@Entity
@Table(name = "farms")
@Data
@EqualsAndHashCode(callSuper = false)
public class Farm extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "farm_id")
    private Integer farmId;

    private String name;
    private String location;

    @ManyToOne
    @JoinColumn(name = "owner_user_id")
    private FarmUser owner;
}
