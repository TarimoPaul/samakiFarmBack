package com.samaki.farm.user.entity;

import com.samaki.farm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

/**
 * MTU - utambulisho na hali ya akaunti pekee.
 *
 * HANA shamba wala role: hivyo viko kwenye FarmUser (uanachama), kwa sababu
 * mtu mmoja anaweza kuwa kwenye mashamba zaidi ya moja, kila moja na role
 * yake. Angalia migration V5__unmerge_users_and_farm_users.sql.
 *
 * `status` na `is_deleted` ni vitu viwili tofauti - angalia UserStatus.
 */
@SQLRestriction("is_deleted = false")
@Entity
@Table(name = "users")
@Data
@EqualsAndHashCode(callSuper = false, of = "userId")
public class User extends BaseEntity {

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

    // PENDING_APPROVAL ndiyo default - kujisajili HAKUTOI ufikiaji.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.PENDING_APPROVAL;

    // ROOT bypass flag (kama Lsms): huru na role yoyote - ROOT haihitaji
    // uanachama wowote kuwa na ufikiaji kamili. Angalia PermissionChecker.
    @Column(name = "is_root", nullable = false)
    private Boolean isRoot = false;

    /** Ikiwa true, login inafanikiwa lakini mteja lazima aende kubadilisha password. */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;
}
