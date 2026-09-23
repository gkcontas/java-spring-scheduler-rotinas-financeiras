package com.gkcontas.scheduler.repository;

import com.gkcontas.scheduler.model.Transaction;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * Always ordered by id so chunks advance deterministically instead of re-reading
     * rows the database happened to return in a different order.
     */
    @Query("SELECT t FROM Transaction t WHERE t.reconciledAt IS NULL ORDER BY t.id")
    List<Transaction> findPending(Pageable pageable);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.reconciledAt IS NULL")
    long countPending();
}
