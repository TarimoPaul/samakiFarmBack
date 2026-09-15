package com.samaki.farm.finance.dto;

import com.samaki.farm.cost.repository.CostRepository;

import java.math.BigDecimal;

/**
 * Jumla ya aina moja ya gharama ndani ya ripoti ya faida.
 *
 * Ipo ili gharama ziweze kuonekana KWA AINA, si namba moja: aina
 * inayoundwa na mtumiaji iitwayo "Chakula" ingehesabu chakula mara mbili
 * pamoja na `feedCost` (angalia V23). Backend haiwezi kulitambua hilo;
 * inaonyesha mgawanyo, na UI inaonya.
 */
public record CostCategoryAmount(Integer costCategoryId, String name, BigDecimal amount) {

    public static CostCategoryAmount of(CostRepository.CategoryAmount row) {
        return new CostCategoryAmount(row.getCostCategoryId(), row.getName(), row.getAmount());
    }
}
