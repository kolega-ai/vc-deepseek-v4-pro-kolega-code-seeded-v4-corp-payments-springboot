package com.northbridge.paymentapi.repo;

import com.northbridge.paymentapi.domain.FeeScheduleRow;
import com.northbridge.paymentapi.domain.PaymentRail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FeeScheduleRepository extends JpaRepository<FeeScheduleRow, UUID> {

    List<FeeScheduleRow> findAllByOrderByProductTypeAscEffectiveFromDesc();

    @Query("""
            select f from FeeScheduleRow f
            where f.productType = :rail and f.currency = :currency
              and f.effectiveFrom <= :at
            order by f.effectiveFrom desc
            """)
    List<FeeScheduleRow> findActive(@Param("rail") PaymentRail rail,
                                    @Param("currency") String currency,
                                    @Param("at") Instant at);
}
