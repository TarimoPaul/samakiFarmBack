package com.samaki.farm.user.repository;

import com.samaki.farm.user.entity.User;
import com.samaki.farm.user.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    // Derived queries (SI findById) - hivyo @SQLRestriction ya User inatumika
    // na mtu aliyefutwa hapatikani kabisa. Angalia BaseEntity kwa maelezo.
    Optional<User> findByUserId(UUID userId);

    Optional<User> findByPhone(String phone);

    Optional<User> findByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    /** "Nionyeshe wanaosubiri idhini" - inatumia idx_users_status. */
    List<User> findByStatusOrderByCreatedAtAsc(UserStatus status);
}
