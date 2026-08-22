package com.samaki.farm.farmuser.entity;

import org.hibernate.annotations.SQLRestriction;

import com.samaki.farm.common.entity.BaseEntity;
import com.samaki.farm.farm.entity.Farm;
import com.samaki.farm.rbac.entity.Role;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.UUID;

/**
 * Mtumiaji wa mfumo - utambulisho (phone/email/password) PAMOJA na muktadha
 * wake wa shamba (farm + role) kwenye entity moja.
 *
 * Awali hizi zilikuwa mbili: `User` (mtu) na `FarmUser` (join-table ya
 * uanachama farm<->user<->role). Zimeunganishwa (angalia migration
 * V4__merge_users_into_farm_users.sql) - mtu mmoja sasa ana shamba MOJA na
 * role MOJA. Multi-farm haiwezekani tena kimuundo, lakini haikuwa
 * inatumika kivitendo: kila mahali code ilikuwa inachukua scopes.get(0) tu.
 *
 * farm na role ni NULLable kwa sababu ya ROOT: isRoot=true anaingia na
 * kufanya kazi bila kuunganishwa na shamba lolote (angalia JwtAuthFilter/
 * PermissionChecker) - ufikiaji wake unatoka kwenye flag, si uhusiano.
 */
@SQLRestriction("is_deleted = false")
@Entity
@Table(name = "farm_users")
@Data
// of = "userId" pekee: bila hii, @Data ingejumuisha farm/role kwenye
// equals/hashCode - na kuzilazimisha lazy proxies kupakiwa kila
// wakati entity inapowekwa kwenye Set/Map.
@EqualsAndHashCode(callSuper = false, of = "userId")
// Farm.owner ni FarmUser na FarmUser.farm ni Farm - bila exclude hii,
// toString ya pande zote mbili ingeingia kwenye mzunguko usioisha.
@ToString(exclude = {"farm", "role"})
public class FarmUser extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id")
    private UUID userId;

    private String name;

    @Column(unique = true, nullable = false)
    private String phone;

    @Column(unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "push_token")
    private String pushToken;

    private String status = "ACTIVE";

    // ROOT bypass flag (kama Lsms): huru na role yoyote - ROOT haihitaji
    // farm/role kuwa na ufikiaji kamili. Angalia PermissionChecker/JwtAuthFilter.
    @Column(name = "is_root", nullable = false)
    private Boolean isRoot = false;

    @ManyToOne
    @JoinColumn(name = "farm_id")
    private Farm farm;

    @ManyToOne
    @JoinColumn(name = "role_id")
    private Role role;
}
