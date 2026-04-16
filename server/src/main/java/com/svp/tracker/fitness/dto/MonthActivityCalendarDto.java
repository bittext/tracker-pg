package com.svp.tracker.fitness.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MonthActivityCalendarDto {

    private int year;
    private int month;
    /** Days with at least one exercise log / strength set (ISO yyyy-MM-dd). */
    private List<String> daysWithStrengthTraining = new ArrayList<>();
    /** Days with a body-weight entry (ISO yyyy-MM-dd). */
    private List<String> daysWithWeightLogged = new ArrayList<>();
    /** Days with at least one exercise log — same as {@link #daysWithStrengthTraining}, sorted (for active-day KPI). */
    private List<String> activeDays = new ArrayList<>();
    /** yyyy-MM-dd -> total exercise duration minutes for that day. */
    private Map<String, Integer> exerciseDurationMinutesByDay = new TreeMap<>();
    /** yyyy-MM-dd -> body weight in kg for that day. */
    private Map<String, BigDecimal> bodyWeightKgByDay = new TreeMap<>();
}
