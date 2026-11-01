package com.svp.tracker.fitness.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AppleHealthExportParserTest {

    @Test
    void keepsWorkoutsAndBodyWeight() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <HealthData locale="en_US">
                  <Record type="HKQuantityTypeIdentifierBodyMass" sourceName="Renpho" unit="lb"
                    startDate="2026-10-28 07:00:00 -0500" endDate="2026-10-28 07:00:00 -0500" value="182.4"/>
                  <Record type="HKQuantityTypeIdentifierStepCount" sourceName="iPhone" unit="count"
                    startDate="2026-10-28 08:00:00 -0500" endDate="2026-10-28 09:00:00 -0500" value="1200"/>
                  <Workout workoutActivityType="HKWorkoutActivityTypeWalking" duration="25.4" durationUnit="min"
                    sourceName="Urevo" startDate="2026-10-28 18:00:00 -0500" endDate="2026-10-28 18:25:00 -0500">
                    <WorkoutStatistics type="HKQuantityTypeIdentifierDistanceWalkingRunning" sum="1.5" unit="mi"/>
                    <WorkoutStatistics type="HKQuantityTypeIdentifierActiveEnergyBurned" sum="140" unit="kcal"/>
                  </Workout>
                </HealthData>
                """;
        AppleHealthExportParser.Parsed parsed =
                AppleHealthExportParser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "export.xml");
        assertEquals(1, parsed.weights().size());
        assertEquals(new BigDecimal("182.400"), parsed.weights().get(0).weightLb());
        assertEquals(1, parsed.workouts().size());
        assertEquals("Walking", parsed.workouts().get(0).activity());
        assertEquals("Urevo", parsed.workouts().get(0).sourceName());
        assertEquals(25, parsed.workouts().get(0).durationMinutes());
        assertEquals(new BigDecimal("1.500"), parsed.workouts().get(0).distanceMiles());
        assertEquals(new BigDecimal("140.0"), parsed.workouts().get(0).energyKcal());
        assertTrue(parsed.workouts().get(0).day().toString().equals("2026-10-28"));
    }

    @Test
    void readsHealthAutoExportWeightAndWorkout() throws Exception {
        String json = """
                {
                  "data": {
                    "metrics": [
                      {
                        "name": "body_mass",
                        "units": "kg",
                        "data": [
                          {"qty": 80, "date": "2026-10-28 07:00:00 -0500", "source": "Renpho"}
                        ]
                      },
                      {
                        "name": "step_count",
                        "units": "count",
                        "data": [
                          {"qty": 1200, "date": "2026-10-28 08:00:00 -0500", "source": "iPhone"}
                        ]
                      }
                    ],
                    "workouts": [
                      {
                        "id": "ABC-123",
                        "name": "Walking",
                        "start": "2026-10-28 18:00:00 -0500",
                        "end": "2026-10-28 18:25:00 -0500",
                        "duration": 1524,
                        "source": {"name": "Urevo", "identifier": "com.urevo"},
                        "distance": {"qty": 1.5, "units": "mi"},
                        "activeEnergyBurned": {"qty": 140, "units": "kcal"}
                      }
                    ]
                  }
                }
                """;
        AppleHealthExportParser.Parsed parsed =
                AppleHealthExportParser.parseAutoExport(new ObjectMapper().readTree(json));
        assertEquals(1, parsed.weights().size());
        assertEquals(new BigDecimal("176.370"), parsed.weights().get(0).weightLb());
        assertEquals("Renpho", parsed.weights().get(0).sourceName());
        assertEquals(1, parsed.workouts().size());
        assertEquals("Walking", parsed.workouts().get(0).activity());
        assertEquals("Urevo", parsed.workouts().get(0).sourceName());
        assertEquals(25, parsed.workouts().get(0).durationMinutes());
        assertEquals(new BigDecimal("1.500"), parsed.workouts().get(0).distanceMiles());
        assertEquals(new BigDecimal("140.0"), parsed.workouts().get(0).energyKcal());
        assertEquals("2026-10-28", parsed.workouts().get(0).day().toString());
    }
}
