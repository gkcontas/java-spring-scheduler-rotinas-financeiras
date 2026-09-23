package com.gkcontas.scheduler.job;

import com.gkcontas.scheduler.model.Invoice;
import com.gkcontas.scheduler.model.InvoiceStatus;
import com.gkcontas.scheduler.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InterestAccrualChunkProcessor {

    private final InvoiceRepository invoiceRepository;

    public InterestAccrualChunkProcessor(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional
    public int applyInterestToNextChunk(LocalDate today, BigDecimal dailyRate, int chunkSize) {
        List<Invoice> overdue = invoiceRepository.findOverdueNeedingInterest(
                InvoiceStatus.OPEN, today, PageRequest.of(0, chunkSize));

        for (Invoice invoice : overdue) {
            invoice.setInterestAmount(invoice.getInterestAmount().add(interestFor(invoice, dailyRate)));
            // Stamping the date is what closes the loop: the query above will not pick
            // this invoice up again today, so the chunk cursor always moves forward.
            invoice.setInterestAppliedOn(today);
        }
        return overdue.size();
    }

    /**
     * Rounds to two decimals HALF_UP on every accrual. Money never carries more
     * precision than the currency has, and leaving the rounding to the end of a chain of
     * multiplications is how cent-level discrepancies appear.
     */
    static BigDecimal interestFor(Invoice invoice, BigDecimal dailyRate) {
        return invoice.getAmount().multiply(dailyRate).setScale(2, RoundingMode.HALF_UP);
    }
}
