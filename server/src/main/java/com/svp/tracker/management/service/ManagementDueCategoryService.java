package com.svp.tracker.management.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.fitness.exception.NotFoundException;
import com.svp.tracker.management.domain.ManagementDueCategory;
import com.svp.tracker.management.dto.ManagementDueCategoryDto;
import com.svp.tracker.management.dto.ManagementDueCategoryWriteRequest;
import com.svp.tracker.management.repository.ManagementDueCategoryRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ManagementDueCategoryService {

    /** Highest first. New names land at the bottom until an admin moves them. */
    static final List<String> DEFAULTS = List.of(
            "Payrolls",
            "Loans",
            "Utilities",
            "Miscellaneous",
            "Interests",
            "Transfers",
            "Credit Cards",
            "Insurance Premiums",
            "Learnings",
            "Medicals");

    private final ManagementDueCategoryRepository repository;
    private final CurrentUserService currentUser;

    @Transactional
    public List<ManagementDueCategoryDto> list() {
        long owner = currentUser.requireUserId();
        if (repository.countByOwnerUserId(owner) == 0) {
            seed(owner);
        }
        return toDtos(repository.findByOwnerUserIdOrderBySortOrderDescNameAsc(owner));
    }

    @Transactional
    public ManagementDueCategoryDto create(ManagementDueCategoryWriteRequest req) {
        long owner = currentUser.requireUserId();
        String name = cleanName(req.name());
        if (repository.existsByOwnerUserIdAndNameIgnoreCase(owner, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That subcategory already exists");
        }
        int sort = lowestSort(owner) - 100;
        ManagementDueCategory row = new ManagementDueCategory();
        row.setOwnerUserId(owner);
        row.setName(name);
        row.setSortOrder(sort);
        row.setCreatedAt(Instant.now());
        repository.save(row);
        return toDto(row);
    }

    @Transactional
    public ManagementDueCategoryDto rename(long id, ManagementDueCategoryWriteRequest req) {
        long owner = currentUser.requireUserId();
        ManagementDueCategory row = requireOwned(id, owner);
        String name = cleanName(req.name());
        if (!row.getName().equalsIgnoreCase(name) && repository.existsByOwnerUserIdAndNameIgnoreCase(owner, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That subcategory already exists");
        }
        row.setName(name);
        repository.save(row);
        return toDto(row);
    }

    @Transactional
    public void delete(long id) {
        long owner = currentUser.requireUserId();
        ManagementDueCategory row = requireOwned(id, owner);
        repository.delete(row);
    }

    /** {@code up} moves the row toward the top of the descending list. */
    @Transactional
    public List<ManagementDueCategoryDto> move(long id, boolean up) {
        long owner = currentUser.requireUserId();
        List<ManagementDueCategory> rows = repository.findByOwnerUserIdOrderBySortOrderDescNameAsc(owner);
        int index = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).getId() != null && rows.get(i).getId() == id) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            throw new NotFoundException("Due subcategory not found: " + id);
        }
        int neighbor = up ? index - 1 : index + 1;
        if (neighbor < 0 || neighbor >= rows.size()) {
            return toDtos(rows);
        }
        ManagementDueCategory current = rows.get(index);
        ManagementDueCategory other = rows.get(neighbor);
        int currentSort = current.getSortOrder();
        int otherSort = other.getSortOrder();
        if (currentSort == otherSort) {
            otherSort = up ? currentSort + 1 : currentSort - 1;
        }
        current.setSortOrder(otherSort);
        other.setSortOrder(currentSort);
        repository.save(current);
        repository.save(other);
        return toDtos(repository.findByOwnerUserIdOrderBySortOrderDescNameAsc(owner));
    }

    private void seed(long owner) {
        int sort = DEFAULTS.size() * 100;
        Instant now = Instant.now();
        for (String name : DEFAULTS) {
            ManagementDueCategory row = new ManagementDueCategory();
            row.setOwnerUserId(owner);
            row.setName(name);
            row.setSortOrder(sort);
            row.setCreatedAt(now);
            repository.save(row);
            sort -= 100;
        }
    }

    private int lowestSort(long owner) {
        return repository.findByOwnerUserIdOrderBySortOrderDescNameAsc(owner).stream()
                .mapToInt(ManagementDueCategory::getSortOrder)
                .min()
                .orElse(100);
    }

    private ManagementDueCategory requireOwned(long id, long owner) {
        return repository
                .findByIdAndOwnerUserId(id, owner)
                .orElseThrow(() -> new NotFoundException("Due subcategory not found: " + id));
    }

    private static String cleanName(String raw) {
        String name = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name the subcategory");
        }
        return name;
    }

    private static List<ManagementDueCategoryDto> toDtos(List<ManagementDueCategory> rows) {
        return rows.stream().map(ManagementDueCategoryService::toDto).toList();
    }

    private static ManagementDueCategoryDto toDto(ManagementDueCategory row) {
        return new ManagementDueCategoryDto(row.getId(), row.getName(), row.getSortOrder());
    }
}
