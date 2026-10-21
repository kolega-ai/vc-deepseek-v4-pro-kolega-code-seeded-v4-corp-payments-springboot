package com.northbridge.paymentapi.repo;

import com.northbridge.paymentapi.domain.FxRateRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FxRateRepository extends JpaRepository<FxRateRow, UUID> {

    List<FxRateRow> findAllByOrderByCurrencyPairAscEffectiveAtDesc();

    @Query("""
            select r from FxRateRow r
            where r.currencyPair = :pair and r.effectiveAt <= :at
            order by r.effectiveAt desc
            """)
    List<FxRateRow> findEffective(@Param("pair") String pair, @Param("at") Instant at);

    default Optional<FxRateRow> latestEffective(String pair, Instant at) {
        List<FxRateRow> rows = findEffective(pair, at);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
