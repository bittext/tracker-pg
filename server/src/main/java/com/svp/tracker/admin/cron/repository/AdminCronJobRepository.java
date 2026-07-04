package com.svp.tracker.admin.cron.repository;

import com.svp.tracker.admin.cron.domain.AdminCronJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminCronJobRepository extends JpaRepository<AdminCronJob, String> {}
