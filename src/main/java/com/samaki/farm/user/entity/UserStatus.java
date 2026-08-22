package com.samaki.farm.user.entity;

/**
 * Mzunguko wa maisha wa akaunti. HAUHUSIANI na is_deleted:
 *
 *   status     - akaunti iko hatua gani (hii)
 *   is_deleted - rekodi imefutwa kabisa (BaseEntity.softDelete)
 *
 * Mtu aliyefutwa hatoki kabisa kwenye query (@SQLRestriction); aliye
 * DISABLED bado anaonekana kwa wasimamizi na anaweza kurudishwa ACTIVE.
 */
public enum UserStatus {

    /** Amejisajili mwenyewe, bado hajaidhinishwa. Hawezi kuingia. */
    PENDING_APPROVAL,

    /** Ameidhinishwa. Anaweza kuingia - hata kama bado hana shamba wala role. */
    ACTIVE,

    /** Amezuiwa na msimamizi. Hawezi kuingia, lakini rekodi yake ipo. */
    DISABLED
}
