package com.gkcontas.scheduler.job;

import com.gkcontas.scheduler.model.Transaction;
import com.gkcontas.scheduler.repository.TransactionRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processes one chunk per transaction.
 *
 * <p>Lives in its own bean deliberately: calling a {@code @Transactional} method from
 * inside the same class bypasses the proxy and the annotation does nothing, so the
 * whole backlog would end up in one giant transaction. One commit per chunk keeps locks
 * short and means a crash halfway through loses one chunk, not the entire run.
 */
@Service
public class ReconciliationChunkProcessor {

    private final TransactionRepository transactionRepository;

    public ReconciliationChunkProcessor(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public int reconcileNextChunk(int chunkSize) {
        List<Transaction> pending = transactionRepository.findPending(PageRequest.of(0, chunkSize));
        Instant reconciledAt = Instant.now();
        // Dirty checking flushes these at commit; no explicit save call is needed.
        pending.forEach(transaction -> transaction.setReconciledAt(reconciledAt));
        return pending.size();
    }
}
