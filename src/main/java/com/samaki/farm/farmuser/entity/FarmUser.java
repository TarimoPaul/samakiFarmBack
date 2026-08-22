package com.samaki.farm.farmuser.entity;

import com.samaki.farm.common.entity.BaseEntity;
import com.samaki.farm.farm.entity.Farm;
import com.samaki.farm.rbac.entity.Role;
import com.samaki.farm.user.entity.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.SQLRestriction;

import java.io.Serializable;
import java.util.UUID;

/**
 * UANACHAMA - "mtu HUYU, kwenye shamba HILI, ana role HII".
 *
 * PK ni (user_id, farm_id): mtu anaweza kuwa na uanachama kwenye mashamba
 * mengi, lakini role MOJA kwa kila shamba.
 *
 * role inaruhusiwa kuwa null: kuidhinishwa (User.status = ACTIVE) na kupewa
 * role ni vitu viwili tofauti (Part A #4). Mtu anaweza kuingia akiwa bado
 * hana ruhusa yoyote na kuona ukurasa mtupu.
 */
@SQLRestriction("is_deleted = false")
@Entity
@Table(name = "farm_users")
@IdClass(FarmUser.FarmUserId.class)
@Data
@EqualsAndHashCode(callSuper = false, of = {"user", "farm"})
@ToString(exclude = {"user", "farm", "role"})
public class FarmUser extends BaseEntity {

    @Id
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Id
    @ManyToOne
    @JoinColumn(name = "farm_id")
    private Farm farm;

    @ManyToOne
    @JoinColumn(name = "role_id")
    private Role role;

    /**
     * Aina za field hapa ni za VITAMBULISHO vya entity husika (UUID kwa User,
     * Integer kwa Farm), si entity zenyewe - ndivyo @IdClass inavyotaka.
     */
    @Data
    public static class FarmUserId implements Serializable {
        private UUID user;
        private Integer farm;
    }
}
