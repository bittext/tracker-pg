package com.svp.tracker.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One recent Markets -> Research -> Financials lookup, per owner. */
@Entity
@Table(name = "finance_company_financials_search")
@Getter
@Setter
@NoArgsConstructor
public class FinanceCompanyFinancialsSearch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String symbol;

    @Column(name = "company_name", columnDefinition = "TEXT")
    private String companyName;

    @Column(name = "searched_at", nullable = false)
    private Instant searchedAt = Instant.now();
}
