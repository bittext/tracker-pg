package com.svp.tracker.management.spec;

import com.svp.tracker.management.domain.ManagementDayOneLog;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class ManagementDayOneSpecifications {

    private ManagementDayOneSpecifications() {}

    /** Non-admin: current user only. Admin: optional single-owner filter. */
    public static Specification<ManagementDayOneLog> ownerScope(boolean admin, long currentUserId, Long filterOwnerId) {
        return (root, query, cb) -> {
            if (!admin) {
                return cb.equal(root.get("ownerUserId"), currentUserId);
            }
            if (filterOwnerId != null) {
                return cb.equal(root.get("ownerUserId"), filterOwnerId);
            }
            return cb.conjunction();
        };
    }

    public static Specification<ManagementDayOneLog> loggedBetween(java.time.LocalDate from, java.time.LocalDate to) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("loggedOn"), from));
            }
            if (to != null) {
                p.add(cb.lessThanOrEqualTo(root.get("loggedOn"), to));
            }
            if (p.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    public static Specification<ManagementDayOneLog> textContains(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) {
                return cb.conjunction();
            }
            String pattern = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("entryText")), pattern),
                    cb.like(cb.lower(root.get("locationText")), pattern),
                    cb.like(cb.lower(root.get("weatherText")), pattern));
        };
    }

    /** Entry must include every tag id (AND). */
    public static Specification<ManagementDayOneLog> hasAllTags(List<Long> tagIds) {
        return (root, query, cb) -> {
            if (tagIds == null || tagIds.isEmpty()) {
                return cb.conjunction();
            }
            query.distinct(true);
            List<Predicate> existsPredicates = new ArrayList<>();
            for (Long tagId : tagIds) {
                Subquery<Long> sq = query.subquery(Long.class);
                Root<ManagementDayOneLog> subRoot = sq.from(ManagementDayOneLog.class);
                Join<Object, Object> tagJoin = subRoot.join("tags");
                sq.select(cb.literal(1L))
                        .where(cb.and(
                                cb.equal(root.get("id"), subRoot.get("id")), cb.equal(tagJoin.get("id"), tagId)));
                existsPredicates.add(cb.exists(sq));
            }
            return cb.and(existsPredicates.toArray(Predicate[]::new));
        };
    }
}
