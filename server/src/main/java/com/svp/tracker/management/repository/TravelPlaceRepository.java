package com.svp.tracker.management.repository;

import com.svp.tracker.management.domain.TravelPlace;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TravelPlaceRepository extends JpaRepository<TravelPlace, Long> {

    List<TravelPlace> findByTripIdOrderBySortOrderAscIdAsc(Long tripId);

    @Query(
            "SELECT DISTINCT p FROM TravelPlace p LEFT JOIN FETCH p.photos WHERE p.trip.id = :tripId ORDER BY p.sortOrder ASC, p.id ASC")
    List<TravelPlace> findByTripIdWithPhotos(@Param("tripId") long tripId);

    @Query("SELECT DISTINCT p FROM TravelPlace p JOIN FETCH p.trip t WHERE p.id = :id AND t.ownerUserId = :owner")
    Optional<TravelPlace> findByIdAndOwnerWithTrip(@Param("id") long id, @Param("owner") long ownerUserId);

    @Query(
            "SELECT DISTINCT p FROM TravelPlace p JOIN FETCH p.trip t WHERE t.ownerUserId = :owner ORDER BY t.startDate DESC, p.sortOrder ASC, p.id ASC")
    List<TravelPlace> findAllForOwnerWithTrip(@Param("owner") long ownerUserId);

    @Query(
            "SELECT DISTINCT p FROM TravelPlace p JOIN FETCH p.trip t WHERE t.ownerUserId = :owner AND ("
                    + "(p.visitDate IS NOT NULL AND p.visitDate >= :from AND p.visitDate <= :to) OR "
                    + "(p.visitDate IS NULL AND t.startDate <= :to AND (t.endDate IS NULL OR t.endDate >= :from))"
                    + ") ORDER BY t.startDate DESC, p.sortOrder ASC, p.id ASC")
    List<TravelPlace> findForOwnerInDateRangeWithTrip(
            @Param("owner") long ownerUserId, @Param("from") java.time.LocalDate from, @Param("to") java.time.LocalDate to);
}
