package com.samaki.farm.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Inawezesha @Scheduled kwenye app nzima - kwa sasa ReminderScheduler
 * pekee ndiye anayeitumia.
 *
 * HAIJAWEKWA MASHARTI hapa: bila bean yenye @Scheduled, kuiwasha
 * hakufanyi chochote (thread pool tupu). Sharti liko kwenye bean
 * yenyewe (angalia ReminderScheduler), ambapo linaeleweka - "vikumbusho
 * vimezimwa" - badala ya kuwa "ratiba nzima ya mfumo imezimwa".
 *
 * TAHADHARI YA INSTANCE NYINGI: kila instance ya app itaendesha tiki
 * yake. Hiyo HAILETI SMS zilizojirudia kwa sababu UNIQUE ya V12 ndiyo
 * inayoamua nani anatuma (angalia ReminderRepository.claim) - lakini
 * inamaanisha kazi ya bure. Ikifika hapo, njia ni scheduler lock
 * (ShedLock au sawa), si kuondoa kikwazo cha database.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
