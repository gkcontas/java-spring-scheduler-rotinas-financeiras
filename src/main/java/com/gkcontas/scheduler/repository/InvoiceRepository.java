package com.gkcontas.scheduler.repository;

import com.gkcontas.scheduler.model.Invoice;
import com.gkcontas.scheduler.model.InvoiceStatus;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    /**
     * The {@code interestAppliedOn} check is what makes the routine safe to re-run: an
     * invoice already charged today is excluded, so a retry after a partial failure
     * resumes rather than double-charging.
     */
    @Query("""
            SELECT i FROM Invoice i
            WHERE i.status = :status
              AND i.dueDate < :today
              AND (i.interestAppliedOn IS NULL OR i.interestAppliedOn < :today)
            ORDER BY i.id
            """)
    List<Invoice> findOverdueNeedingInterest(@Param("status") InvoiceStatus status,
                                             @Param("today") LocalDate today,
                                             Pageable pageable);
}
