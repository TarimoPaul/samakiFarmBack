package com.samaki.farm.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Msingi wa pamoja wa audit + soft-delete kwa entities zote (kama Lsms
 * uaa.entity.BaseEntity, sehemu ya audit/soft-delete tu - SI id+uid dual-key
 * pattern yao, kwa sababu Farm/Role/Permission/Cycle/ProductionUnit/Species
 * tayari zinatumia Integer PK yao moja kwa moja kwenye DTOs/GraphQL/URLs).
 *
 * AUDIT: sasa ni Spring Data JPA Auditing (@CreatedDate/@LastModifiedDate/
 * @LastModifiedBy) badala ya @PrePersist/@PreUpdate za mkono. Sababu ya
 * kubadilisha: @LastModifiedBy ndiyo njia pekee ya kujaza updatedBy kwa
 * kiotomatiki - JPA callback ya kawaida haijui ni nani aliyeingia. Angalia
 * AuditingConfig kwa AuditorAware inayosoma AuthenticatedUser.
 *
 * Hakuna safu ya created_by kwenye database, hivyo @CreatedBy haipo -
 * updatedBy inajazwa hata wakati wa kuunda (ina maana "aliyegusa mwisho").
 *
 * SOFT-DELETE: entities zote zenye @SQLRestriction("is_deleted = false")
 * zinachujwa kiotomatiki kwenye query ZOTE za JPQL/derived/collection loads.
 * MPAKA HAPA: findById() ya Spring Data inatumia EntityManager.find()
 * (lookup ya moja kwa moja kwa PK), na Hibernate HAITUMII @SQLRestriction
 * hapo - hivyo findById inaweza kurudisha rekodi iliyofutwa. Njia zote
 * nyeti (login kwa phone, JwtAuthFilter.findByUserId, orodha za shamba) ni
 * derived queries, hivyo zinachujwa ipasavyo.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class BaseEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    /**
     * Soft-delete - kama Lsms BaseEntity.delete(). Baada ya kuita hii na
     * kuhifadhi, rekodi haitaonekana tena kwenye query za kawaida
     * (@SQLRestriction). deletedBy hapa ni ya mkono (si @LastModifiedBy) kwa
     * sababu inaeleza tukio mahususi la kufuta, si mabadiliko yoyote tu.
     */
    public void softDelete(UUID byUserId) {
        this.deleted = true;
        this.deletedAt = Instant.now();
        this.deletedBy = byUserId;
    }
}
