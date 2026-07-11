package com.svp.tracker.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.RobinhoodRhDailyTrackerProperties;
import com.svp.tracker.finance.domain.RhDailyTrackerAiInsight;
import com.svp.tracker.finance.dto.RhDailyTrackerAiInsightRequestDto;
import com.svp.tracker.finance.dto.RobinhoodRhDailyTrackerReportDto;
import com.svp.tracker.finance.repository.RhDailyTrackerAiInsightRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class RhDailyTrackerAiInsightServiceTest {

    @Mock
    private RobinhoodRhDailyTrackerService dailyTrackerService;

    @Mock
    private RhDailyTrackerOpenAiClient openAiClient;

    @Mock
    private RhDailyTrackerAiInsightRepository insightRepository;

    @Mock
    private CurrentUserService currentUser;

    private ObjectMapper objectMapper;
    private RhDailyTrackerAiInsightService service;
    private RobinhoodRhDailyTrackerProperties propsDisabled;
    private RobinhoodRhDailyTrackerProperties propsConfigured;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        propsDisabled = new RobinhoodRhDailyTrackerProperties(
                "",
                "America/Chicago",
                21,
                "true",
                List.of(),
                java.util.Map.of(),
                true,
                new RobinhoodRhDailyTrackerProperties.Ai(false, "", "https://api.openai.com/v1", "gpt-4o-mini", 60000, 1200));
        propsConfigured = new RobinhoodRhDailyTrackerProperties(
                "",
                "America/Chicago",
                21,
                "true",
                List.of(),
                java.util.Map.of(),
                true,
                new RobinhoodRhDailyTrackerProperties.Ai(
                        true, "sk-test", "https://api.openai.com/v1", "gpt-4o-mini", 60000, 1200));
    }

    @Test
    void statusReflectsConfiguration() {
        service = new RhDailyTrackerAiInsightService(
                propsDisabled, dailyTrackerService, openAiClient, insightRepository, currentUser, objectMapper);
        var status = service.status();
        assertFalse(status.enabled());
        assertFalse(status.configured());

        service = new RhDailyTrackerAiInsightService(
                propsConfigured, dailyTrackerService, openAiClient, insightRepository, currentUser, objectMapper);
        status = service.status();
        assertTrue(status.enabled());
        assertTrue(status.configured());
    }

    @Test
    void generateWhenDisabledThrows() {
        service = new RhDailyTrackerAiInsightService(
                propsDisabled, dailyTrackerService, openAiClient, insightRepository, currentUser, objectMapper);
        when(currentUser.requireUserId()).thenReturn(1L);
        when(dailyTrackerService.buildReport(anyInt(), any())).thenReturn(emptyReport(2026));
        when(insightRepository.findByOwnerUserIdAndScopeAndPeriodKey(anyLong(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        assertThrows(
                ResponseStatusException.class,
                () -> service.generate(new RhDailyTrackerAiInsightRequestDto("MONTH", 2026, 7, null, null, false)));
        verify(openAiClient, never()).completeJson(anyString(), anyString());
    }

    @Test
    void cacheHitSkipsOpenAi() throws Exception {
        service = new RhDailyTrackerAiInsightService(
                propsConfigured, dailyTrackerService, openAiClient, insightRepository, currentUser, objectMapper);
        when(currentUser.requireUserId()).thenReturn(9L);
        when(dailyTrackerService.buildReport(eq(2026), eq(List.of(7)))).thenReturn(emptyReport(2026));

        var bundle = RhDailyTrackerAiFactsBuilder.build(
                objectMapper, "MONTH", "2026-07", "July 2026", List.of());
        RhDailyTrackerAiInsight cached = new RhDailyTrackerAiInsight();
        cached.setOwnerUserId(9L);
        cached.setScope("MONTH");
        cached.setPeriodKey("2026-07");
        cached.setFactsHash(bundle.factsHash());
        cached.setModel("gpt-4o-mini");
        cached.setUpdatedAt(Instant.parse("2026-07-01T12:00:00Z"));
        cached.setInsightJson(
                """
                {"scope":"MONTH","periodKey":"2026-07","periodLabel":"July 2026","generatedAt":"2026-07-01T12:00:00Z","model":"gpt-4o-mini","summary":"Quiet month","leanings":["Few trades"],"trends":["Flat"],"improvements":["Log more"],"nextActions":["Review weekly"]}
                """);

        when(insightRepository.findByOwnerUserIdAndScopeAndPeriodKey(9L, "MONTH", "2026-07"))
                .thenReturn(Optional.of(cached));

        var dto = service.generate(new RhDailyTrackerAiInsightRequestDto("MONTH", 2026, 7, null, null, false));
        assertTrue(dto.cached());
        assertEquals("Quiet month", dto.summary());
        verify(openAiClient, never()).completeJson(anyString(), anyString());
    }

    private static RobinhoodRhDailyTrackerReportDto emptyReport(int year) {
        return new RobinhoodRhDailyTrackerReportDto(
                year,
                7,
                List.of(7),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                "",
                List.of(),
                List.of(),
                List.of());
    }
}
