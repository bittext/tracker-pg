package com.svp.tracker.finance.predicts.repository;

import com.svp.tracker.finance.predicts.domain.PredictsSourceHealth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PredictsSourceHealthRepository extends JpaRepository<PredictsSourceHealth, String> {}
