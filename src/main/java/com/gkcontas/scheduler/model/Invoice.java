package com.gkcontas.scheduler.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "interest_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal interestAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private InvoiceStatus status;

    /**
     * Idempotency marker for the interest routine: interest is charged at most once per
     * invoice per day, no matter how many times the routine runs.
     */
    @Column(name = "interest_applied_on")
    private LocalDate interestAppliedOn;

    public Invoice(Account account, LocalDate dueDate, BigDecimal amount, InvoiceStatus status) {
        this.account = account;
        this.dueDate = dueDate;
        this.amount = amount;
        this.status = status;
        this.interestAmount = BigDecimal.ZERO;
    }
}
