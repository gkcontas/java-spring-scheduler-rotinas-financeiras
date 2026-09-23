package com.gkcontas.scheduler.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.gkcontas.scheduler.model.Account;
import com.gkcontas.scheduler.model.Invoice;
import com.gkcontas.scheduler.model.InvoiceStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class InterestAccrualChunkProcessorTest {

    private static final BigDecimal DAILY_RATE = new BigDecimal("0.001");

    @Test
    void shouldComputeInterestRoundedToTwoDecimals() {
        BigDecimal interest = InterestAccrualChunkProcessor.interestFor(invoiceOf("1000.00"), DAILY_RATE);

        assertThat(interest).isEqualByComparingTo("1.00");
        assertThat(interest.scale()).isEqualTo(2);
    }

    @Test
    void shouldRoundHalfUpRatherThanTruncate() {
        // 1234.56 * 0.001 = 1.23456 -> 1.23
        assertThat(InterestAccrualChunkProcessor.interestFor(invoiceOf("1234.56"), DAILY_RATE))
                .isEqualByComparingTo("1.23");

        // 1235.00 * 0.001 = 1.235 -> 1.24 with HALF_UP, 1.23 if it truncated
        assertThat(InterestAccrualChunkProcessor.interestFor(invoiceOf("1235.00"), DAILY_RATE))
                .isEqualByComparingTo("1.24");
    }

    @Test
    void shouldNeverProduceMorePrecisionThanTheCurrencyHas() {
        // Left unrounded this would be 0.0000001, and cent-level drift starts exactly
        // there: a fraction that survives into the next multiplication.
        BigDecimal interest = InterestAccrualChunkProcessor.interestFor(invoiceOf("0.0001"), DAILY_RATE);

        assertThat(interest.scale()).isEqualTo(2);
        assertThat(interest).isEqualByComparingTo("0.00");
    }

    private static Invoice invoiceOf(String amount) {
        return new Invoice(
                new Account("Holder", BigDecimal.ZERO),
                LocalDate.now().minusDays(10),
                new BigDecimal(amount),
                InvoiceStatus.OPEN);
    }
}
