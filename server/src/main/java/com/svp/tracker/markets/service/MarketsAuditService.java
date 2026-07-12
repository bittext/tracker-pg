package com.svp.tracker.markets.service;

import com.svp.tracker.auth.security.TrackerUserPrincipal;
import com.svp.tracker.markets.domain.MarketsAuditLog;
import com.svp.tracker.markets.repository.MarketsAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MarketsAuditService {

    private final MarketsAuditLogRepository repository;

    @Transactional
    public void record(TrackerUserPrincipal user, String action, String method, String path, String clientIp) {
        MarketsAuditLog row = new MarketsAuditLog();
        row.setUserId(user.id());
        row.setUsername(user.username());
        row.setAction(action);
        row.setHttpMethod(method == null ? "" : method);
        row.setRequestPath(path == null ? "" : (path.length() > 512 ? path.substring(0, 512) : path));
        row.setClientIp(clientIp);
        repository.save(row);
    }
}
