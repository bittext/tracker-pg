package com.svp.tracker.finance.service;

import com.svp.tracker.auth.domain.AppUser;
import com.svp.tracker.auth.repository.AppUserRepository;
import com.svp.tracker.config.RobinhoodAgenticAutoTradeProperties;
import com.svp.tracker.config.RobinhoodAgenticProperties;
import com.svp.tracker.finance.domain.RobinhoodAgenticApprovalNotification;
import com.svp.tracker.finance.domain.RobinhoodAgenticAutoTradeRun;
import com.svp.tracker.finance.domain.RobinhoodAgenticOrder;
import com.svp.tracker.finance.dto.RobinhoodAgenticAutoTradeEvaluateDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticAutoTradeRunDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticOrderDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticSettingsDto;
import com.svp.tracker.finance.dto.RobinhoodAgenticSettingsRequestDto;
import com.svp.tracker.finance.dto.admin.RobinhoodAgenticAdminActionResultDto;
import com.svp.tracker.finance.dto.admin.RobinhoodAgenticAdminConfigDto;
import com.svp.tracker.finance.dto.admin.RobinhoodAgenticAdminOrderRowDto;
import com.svp.tracker.finance.dto.admin.RobinhoodAgenticAdminStatsDto;
import com.svp.tracker.finance.dto.admin.RobinhoodAgenticAdminTrackerDto;
import com.svp.tracker.finance.dto.admin.RobinhoodAgenticApprovalNotificationDto;
import com.svp.tracker.finance.repository.RobinhoodAgenticApprovalNotificationRepository;
import com.svp.tracker.finance.repository.RobinhoodAgenticAutoTradeRunRepository;
import com.svp.tracker.finance.repository.RobinhoodAgenticConnectionRepository;
import com.svp.tracker.finance.repository.RobinhoodAgenticOrderRepository;
import com.svp.tracker.finance.repository.RobinhoodAgenticSettingsRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AdminRobinhoodAgenticService {

    private final RobinhoodAgenticProperties agenticProps;
    private final RobinhoodAgenticAutoTradeProperties autoTradeProps;
    private final RobinhoodAgenticAdminDefaultsService adminDefaultsService;
    private final RobinhoodAgenticOrderService orderService;
    private final RobinhoodAgenticAutoTradeService autoTradeService;
    private final RobinhoodAgenticApprovalNotificationService approvalNotificationService;
    private final RobinhoodAgenticConnectionRepository connectionRepository;
    private final RobinhoodAgenticOrderRepository orderRepository;
    private final RobinhoodAgenticSettingsRepository settingsRepository;
    private final RobinhoodAgenticAutoTradeRunRepository runRepository;
    private final RobinhoodAgenticApprovalNotificationRepository notificationRepository;
    private final AppUserRepository appUserRepository;

    @Transactional(readOnly = true)
    public RobinhoodAgenticAdminConfigDto config() {
        return new RobinhoodAgenticAdminConfigDto(
                agenticProps.enabled(),
                agenticProps.serviceConfigured(),
                agenticProps.executionEnabled(),
                autoTradeProps.enabled(),
                agenticProps.serviceBaseUrl(),
                agenticProps.syncCronEnabled(),
                agenticProps.syncCron(),
                agenticProps.defaultMaxOrderNotional(),
                autoTradeProps.pollFixedDelayMs());
    }

    @Transactional(readOnly = true)
    public RobinhoodAgenticAdminStatsDto stats() {
        Instant since24h = Instant.now().minus(24, ChronoUnit.HOURS);
        long notif24h = notificationRepository.countByCreatedAtAfter(since24h);
        long runs24h = runRepository.findAll().stream()
                .filter(r -> r.getStartedAt().isAfter(since24h))
                .count();
        return new RobinhoodAgenticAdminStatsDto(
                connectionRepository.count(),
                orderRepository.countByStatus("pending_approval"),
                orderRepository.countSince(since24h),
                runs24h,
                settingsRepository.findByAutoTradeEnabledTrueAndAutoTradeKillSwitchFalse().size(),
                notif24h);
    }

    @Transactional(readOnly = true)
    public RobinhoodAgenticAdminTrackerDto tracker() {
        List<RobinhoodAgenticOrder> pending = orderRepository.findByStatusOrderByCreatedAtDesc("pending_approval");
        Map<Long, AppUser> users = loadUsers(pending.stream().map(RobinhoodAgenticOrder::getOwnerUserId).distinct().toList());

        List<RobinhoodAgenticAdminOrderRowDto> pendingRows = new ArrayList<>();
        for (RobinhoodAgenticOrder o : pending) {
            pendingRows.add(toAdminOrderRow(o, users.get(o.getOwnerUserId())));
        }

        List<RobinhoodAgenticOrderDto> recent = orderRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(orderService::toOrderDto)
                .toList();

        List<RobinhoodAgenticAutoTradeRunDto> runs = runRepository.findTop30ByOrderByStartedAtDesc().stream()
                .map(this::toRunDto)
                .toList();

        List<RobinhoodAgenticApprovalNotificationDto> notifications =
                approvalNotificationService.recentNotifications().stream()
                        .map(this::toNotificationDto)
                        .toList();

        return new RobinhoodAgenticAdminTrackerDto(pendingRows, recent, runs, notifications);
    }

    @Transactional
    public RobinhoodAgenticAdminActionResultDto evaluateAll() {
        autoTradeService.evaluateAllScheduled();
        return RobinhoodAgenticAdminActionResultDto.ok("Scheduled auto-trade evaluation invoked for all enabled users");
    }

    @Transactional
    public RobinhoodAgenticAdminActionResultDto evaluateUser(long ownerUserId) {
        requireUser(ownerUserId);
        RobinhoodAgenticAutoTradeEvaluateDto result = autoTradeService.evaluateForUser(ownerUserId, false);
        return RobinhoodAgenticAdminActionResultDto.ok(result.message(), result);
    }

    @Transactional
    public RobinhoodAgenticOrderDto approveOrder(long ownerUserId, long orderId) {
        requireUser(ownerUserId);
        return orderService.approveOrderForUser(ownerUserId, orderId);
    }

    @Transactional
    public RobinhoodAgenticOrderDto rejectOrder(long ownerUserId, long orderId) {
        requireUser(ownerUserId);
        return orderService.rejectOrderForUser(ownerUserId, orderId);
    }

    @Transactional(readOnly = true)
    public RobinhoodAgenticSettingsDto settingsForUser(long ownerUserId) {
        requireUser(ownerUserId);
        return orderService.settingsForUser(ownerUserId);
    }

    @Transactional
    public RobinhoodAgenticSettingsDto saveSettingsForUser(long ownerUserId, RobinhoodAgenticSettingsRequestDto body) {
        requireUser(ownerUserId);
        return orderService.saveSettingsForUser(ownerUserId, body);
    }

    @Transactional
    public RobinhoodAgenticAdminActionResultDto applyDefaultsToUser(long ownerUserId) {
        requireUser(ownerUserId);
        adminDefaultsService.applyDefaultsToUser(ownerUserId);
        return RobinhoodAgenticAdminActionResultDto.ok("Admin default guardrails applied to user " + ownerUserId);
    }

    private void requireUser(long ownerUserId) {
        if (!appUserRepository.existsById(ownerUserId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
    }

    private Map<Long, AppUser> loadUsers(List<Long> ids) {
        Map<Long, AppUser> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        for (AppUser u : appUserRepository.findAllById(ids)) {
            map.put(u.getId(), u);
        }
        return map;
    }

    private RobinhoodAgenticAdminOrderRowDto toAdminOrderRow(RobinhoodAgenticOrder order, AppUser user) {
        String username = user != null ? user.getUsername() : ("user-" + order.getOwnerUserId());
        String email = user != null ? user.getEmail() : "";
        return new RobinhoodAgenticAdminOrderRowDto(
                orderService.toOrderDto(order), order.getOwnerUserId(), username, email);
    }

    private RobinhoodAgenticAutoTradeRunDto toRunDto(RobinhoodAgenticAutoTradeRun run) {
        return new RobinhoodAgenticAutoTradeRunDto(
                run.getId(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getStatus(),
                run.getTickersEvaluated(),
                run.getSignalsGenerated(),
                run.getOrdersReviewed(),
                run.getOrdersPlaced(),
                run.getMessage());
    }

    private RobinhoodAgenticApprovalNotificationDto toNotificationDto(RobinhoodAgenticApprovalNotification n) {
        return new RobinhoodAgenticApprovalNotificationDto(
                n.getId(),
                n.getOwnerUserId(),
                n.getOrderId(),
                n.getChannel(),
                n.getStatus(),
                n.getDestinationMasked(),
                n.getDetail(),
                n.getCreatedAt());
    }
}
