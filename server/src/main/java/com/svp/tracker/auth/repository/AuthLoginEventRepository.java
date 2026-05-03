package com.svp.tracker.auth.repository;

import com.svp.tracker.auth.domain.AuthLoginEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuthLoginEventRepository extends JpaRepository<AuthLoginEvent, Long> {

    Page<AuthLoginEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query(
            """
            SELECT e FROM AuthLoginEvent e
            WHERE LOWER(e.usernameShown) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(e.clientIp) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(COALESCE(e.userAgent, '')) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(COALESCE(e.detail, '')) LIKE LOWER(CONCAT('%', :q, '%'))
            ORDER BY e.createdAt DESC
            """)
    Page<AuthLoginEvent> search(@Param("q") String q, Pageable pageable);

    @Query(
            """
            SELECT e FROM AuthLoginEvent e
            WHERE e.userId = :userId
               OR (e.userId IS NULL AND LOWER(TRIM(e.usernameShown)) = LOWER(:username))
            ORDER BY e.createdAt DESC
            """)
    Page<AuthLoginEvent> findMine(@Param("userId") long userId, @Param("username") String username, Pageable pageable);

    @Query(
            """
            SELECT e FROM AuthLoginEvent e
            WHERE (e.userId = :userId
               OR (e.userId IS NULL AND LOWER(TRIM(e.usernameShown)) = LOWER(:username)))
              AND (
                LOWER(e.clientIp) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(e.userAgent, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(e.detail, '')) LIKE LOWER(CONCAT('%', :q, '%'))
              )
            ORDER BY e.createdAt DESC
            """)
    Page<AuthLoginEvent> searchMine(
            @Param("userId") long userId, @Param("username") String username, @Param("q") String q, Pageable pageable);
}
