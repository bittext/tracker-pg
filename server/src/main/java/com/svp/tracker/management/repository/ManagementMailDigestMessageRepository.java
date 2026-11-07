package com.svp.tracker.management.repository;

import com.svp.tracker.management.domain.ManagementMailDigestMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagementMailDigestMessageRepository extends JpaRepository<ManagementMailDigestMessage, Long> {

    List<ManagementMailDigestMessage> findByDigestIdOrderByReceivedAtDesc(long digestId);

    void deleteByDigestId(long digestId);
}
