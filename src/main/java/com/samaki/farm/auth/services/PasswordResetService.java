package com.samaki.farm.auth.services;

import com.samaki.farm.farmuser.entity.FarmUser;
import com.samaki.farm.auth.entity.PasswordResetOtp;
import com.samaki.farm.common.notification.SmsSender;
import com.samaki.farm.auth.repository.PasswordResetOtpRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Forgot-password kwa SMS OTP (kama Lsms, badala ya email link - login hapa
 * ni kwa phone, si email). Msimbo (6-digit) unahifadhiwa DB-ni kama hash
 * pekee (PasswordEncoder ile ile ya passwords), kamwe wazi.
 *
 * AuthController ndiyo inayoamua ujumbe wa jibu kwa mteja (generic kila
 * wakati - "kama namba hii ipo...") - service hii haifichui popote kama
 * phone fulani ipo kwenye mfumo, kuzuia user enumeration.
 */
@Component
public class PasswordResetService {

    private static final int OTP_DIGITS = 6;
    private static final Duration OTP_TTL = Duration.ofMinutes(10);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 5;

    private final PasswordResetOtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final SmsSender smsSender;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(PasswordResetOtpRepository otpRepository, PasswordEncoder passwordEncoder,
                                 SmsSender smsSender) {
        this.otpRepository = otpRepository;
        this.passwordEncoder = passwordEncoder;
        this.smsSender = smsSender;
    }

    /**
     * Inatengeneza na kutuma OTP mpya - lakini kimya (no-op) kama tayari
     * kuna OTP halali iliyotumwa hivi karibuni (RESEND_COOLDOWN), badala ya
     * kutupa error - hii inazuia SMS spam BILA kufichua tofauti ya tabia
     * kwa mteja (angalia comment ya class kuhusu user enumeration).
     */
    @Transactional
    public void issueOtp(FarmUser user) {
        Instant now = Instant.now();
        Optional<PasswordResetOtp> latest =
                otpRepository.findFirstByUser_UserIdAndUsedAtIsNullOrderByCreatedAtDesc(user.getUserId());

        boolean recentlyIssued = latest
                .filter(otp -> otp.getExpiresAt().isAfter(now))
                .filter(otp -> otp.getCreatedAt().isAfter(now.minus(RESEND_COOLDOWN)))
                .isPresent();
        if (recentlyIssued) {
            return;
        }

        String code = generateCode();

        PasswordResetOtp otp = new PasswordResetOtp();
        otp.setUser(user);
        otp.setCodeHash(passwordEncoder.encode(code));
        otp.setExpiresAt(now.plus(OTP_TTL));
        otpRepository.save(otp);

        smsSender.send(user.getPhone(),
                "Msimbo wako wa kubadilisha password ni " + code + " (utaisha muda baada ya dakika 10).");
    }

    /** Inathibitisha OTP na kuitumia (mara moja tu) - true ikiwa sahihi/halali. */
    @Transactional
    public boolean verifyAndConsume(FarmUser user, String submittedCode) {
        Optional<PasswordResetOtp> otpOpt =
                otpRepository.findFirstByUser_UserIdAndUsedAtIsNullOrderByCreatedAtDesc(user.getUserId());
        if (otpOpt.isEmpty()) {
            return false;
        }

        PasswordResetOtp otp = otpOpt.get();
        if (otp.getExpiresAt().isBefore(Instant.now()) || otp.getAttempts() >= MAX_ATTEMPTS) {
            return false;
        }

        if (!passwordEncoder.matches(submittedCode, otp.getCodeHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            otpRepository.save(otp);
            return false;
        }

        otp.setUsedAt(Instant.now());
        otpRepository.save(otp);
        return true;
    }

    private String generateCode() {
        return String.format("%0" + OTP_DIGITS + "d", random.nextInt(1_000_000));
    }
}
