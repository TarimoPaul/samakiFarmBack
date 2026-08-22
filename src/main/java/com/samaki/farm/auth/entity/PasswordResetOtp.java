package com.samaki.farm.auth.entity;

import org.hibernate.annotations.SQLRestriction;

import com.samaki.farm.common.entity.BaseEntity;
import com.samaki.farm.farmuser.entity.FarmUser;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/**
 * OTP ya kubadilisha password (forgot-password kwa SMS, si email - login
 * hapa ni kwa phone). Msimbo wenyewe HAUHIFADHIWI wazi - codeHash pekee
 * (BCrypt, kupitia PasswordEncoder ile ile ya passwords), kama vile
 * password yenyewe isivyohifadhiwa wazi.
 */
@SQLRestriction("is_deleted = false")
@Entity
@Table(name = "password_reset_otps")
@Data
@EqualsAndHashCode(callSuper = false)
public class PasswordResetOtp extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "otp_id")
    private Long otpId;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private FarmUser user;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "used_at")
    private Instant usedAt;
}
