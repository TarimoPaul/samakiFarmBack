package com.samaki.farm.dailytask.dto;

/**
 * Kuweka kazi ya kila siku kuwa imekamilika.
 *
 * `completionDate` ikiachwa wazi inatumia leo - mtindo ule ule wa
 * LogFeedingInput/LogWaterQualityInput. Inaruhusiwa kutumwa kwa sababu
 * mfanyakazi anayeandika jioni kazi aliyoifanya asubuhi (au kesho yake,
 * mtandao ukikatika) ni jambo la kawaida shambani.
 *
 * HAKUNA `status` hapa kwa makusudi. Mutation hii ina maana MOJA -
 * "imefanyika" - na ndiyo maana ruhusa yake inaitwa `mark_task_done`.
 * MISSED/LATE ziko kwenye schema ya V1 na huduma inajua kuzisoma, lakini
 * kumruhusu mteja kuchagua status yoyote kungegeuza swali la Reminders
 * ("nani hajafanya?") kuwa lisilo na jibu la uhakika.
 */
public record CompleteTaskInput(
        Integer taskId,
        String completionDate,
        String notes
) {}
