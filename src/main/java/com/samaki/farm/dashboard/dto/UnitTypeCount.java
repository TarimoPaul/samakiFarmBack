package com.samaki.farm.dashboard.dto;

/**
 * Vitengo vya aina moja vilivyokuwepo tarehe iliyoulizwa.
 *
 * Aina ni safu isiyobadilika baada ya kitengo kuundwa - hakuna msimbo
 * unaoiandika tena - hivyo aina ya leo ndiyo iliyokuwa siku ile. Kilichobadilika
 * ni VIPI vilivyokuwepo, na hilo linatoka kwa `created_at`/`deleted_at`.
 */
public record UnitTypeCount(String type, long count) {
}
