package com.svp.tracker.fitness.service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

/**
 * Reads an Apple Health {@code export.xml} (or the zip that contains it) and keeps workouts plus body-weight samples.
 */
public final class AppleHealthExportParser {

    static final int MAX_WORKOUTS = 50_000;
    static final int MAX_WEIGHTS = 100_000;

    private static final DateTimeFormatter APPLE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XX");
    private static final BigDecimal LB_PER_KG = new BigDecimal("2.2046226218");
    private static final String BODY_MASS = "HKQuantityTypeIdentifierBodyMass";

    private AppleHealthExportParser() {}

    public static Parsed parse(InputStream in, String fileName) throws Exception {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".zip")) {
            try (ZipInputStream zip = new ZipInputStream(in)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String entryName = entry.getName().replace('\\', '/');
                    if (entryName.endsWith("export.xml") && !entryName.endsWith("export_cda.xml")) {
                        return parseXml(zip);
                    }
                }
            }
            throw new IllegalArgumentException("The zip does not contain export.xml.");
        }
        return parseXml(in);
    }

    static Parsed parseXml(InputStream in) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        XMLStreamReader reader = factory.createXMLStreamReader(in);
        List<ParsedWorkout> workouts = new ArrayList<>();
        List<ParsedWeight> weights = new ArrayList<>();
        boolean truncated = false;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event != XMLStreamConstants.START_ELEMENT) {
                continue;
            }
            String local = reader.getLocalName();
            if ("Record".equals(local)) {
                if (weights.size() >= MAX_WEIGHTS) {
                    truncated = true;
                    continue;
                }
                ParsedWeight weight = readWeight(reader);
                if (weight != null) {
                    weights.add(weight);
                }
            } else if ("Workout".equals(local)) {
                if (workouts.size() >= MAX_WORKOUTS) {
                    truncated = true;
                    skipElement(reader);
                    continue;
                }
                workouts.add(readWorkout(reader));
            }
        }
        reader.close();
        return new Parsed(workouts, weights, truncated);
    }

    private static ParsedWeight readWeight(XMLStreamReader reader) {
        if (!BODY_MASS.equals(attr(reader, "type"))) {
            return null;
        }
        BigDecimal pounds = toPounds(attr(reader, "value"), attr(reader, "unit"));
        OffsetDateTime when = parseTime(attr(reader, "startDate"));
        if (pounds == null || when == null || pounds.signum() <= 0) {
            return null;
        }
        String source = clip(attr(reader, "sourceName"), 128);
        String fingerprint = fingerprint(
                BODY_MASS, source, attr(reader, "startDate"), attr(reader, "endDate"), attr(reader, "value"));
        return new ParsedWeight(fingerprint, when.toLocalDate(), when.toInstant(), pounds, source);
    }

    private static ParsedWorkout readWorkout(XMLStreamReader reader) throws Exception {
        String rawType = attr(reader, "workoutActivityType");
        String source = clip(attr(reader, "sourceName"), 128);
        String startText = attr(reader, "startDate");
        String endText = attr(reader, "endDate");
        String durationText = attr(reader, "duration");
        OffsetDateTime start = parseTime(startText);
        OffsetDateTime end = parseTime(endText);
        int minutes = durationMinutes(durationText, attr(reader, "durationUnit"));
        BigDecimal miles = toMiles(attr(reader, "totalDistance"), attr(reader, "totalDistanceUnit"));
        BigDecimal kcal = toKcal(attr(reader, "totalEnergyBurned"), attr(reader, "totalEnergyBurnedUnit"));
        int depth = 1;
        while (reader.hasNext() && depth > 0) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
                if (depth == 2 && "WorkoutStatistics".equals(reader.getLocalName())) {
                    String statType = attr(reader, "type");
                    String sum = attr(reader, "sum");
                    String unit = attr(reader, "unit");
                    if (statType != null && statType.contains("Distance")) {
                        BigDecimal parsed = toMiles(sum, unit);
                        if (parsed != null) {
                            miles = parsed;
                        }
                    } else if (statType != null && statType.contains("EnergyBurned")) {
                        BigDecimal parsed = toKcal(sum, unit);
                        if (parsed != null) {
                            kcal = parsed;
                        }
                    }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
        if (start == null) {
            start = OffsetDateTime.now();
        }
        String fingerprint = fingerprint(rawType, source == null ? "" : source, startText, endText, durationText);
        return new ParsedWorkout(
                fingerprint,
                activityLabel(rawType),
                source,
                start.toInstant(),
                end == null ? null : end.toInstant(),
                start.toLocalDate(),
                minutes,
                miles,
                kcal);
    }

    private static void skipElement(XMLStreamReader reader) throws Exception {
        int depth = 1;
        while (reader.hasNext() && depth > 0) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
    }

    static String activityLabel(String raw) {
        String value = raw == null ? "" : raw;
        String prefix = "HKWorkoutActivityType";
        if (value.startsWith(prefix)) {
            value = value.substring(prefix.length());
        }
        if (value.isBlank()) {
            return "Workout";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) {
                out.append(' ');
            }
            out.append(c);
        }
        String label = out.toString().trim();
        return label.length() > 80 ? label.substring(0, 80) : label;
    }

    private static String attr(XMLStreamReader reader, String name) {
        String value = reader.getAttributeValue(null, name);
        return value == null ? "" : value.trim();
    }

    private static OffsetDateTime parseTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text, APPLE_TIME);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static int durationMinutes(String value, String unit) {
        BigDecimal amount = decimal(value);
        if (amount == null) {
            return 0;
        }
        String u = unit == null ? "min" : unit.toLowerCase(Locale.ROOT);
        BigDecimal minutes = switch (u) {
            case "s", "sec", "secs", "second", "seconds" -> amount.divide(new BigDecimal("60"), 4, RoundingMode.HALF_UP);
            case "h", "hr", "hour", "hours" -> amount.multiply(new BigDecimal("60"));
            default -> amount;
        };
        return minutes.setScale(0, RoundingMode.HALF_UP).intValue();
    }

    static BigDecimal toPounds(String value, String unit) {
        BigDecimal amount = decimal(value);
        if (amount == null) {
            return null;
        }
        String u = unit == null ? "lb" : unit.toLowerCase(Locale.ROOT);
        if (u.startsWith("kg")) {
            amount = amount.multiply(LB_PER_KG);
        }
        return amount.setScale(3, RoundingMode.HALF_UP);
    }

    static BigDecimal toMiles(String value, String unit) {
        BigDecimal amount = decimal(value);
        if (amount == null) {
            return null;
        }
        String u = unit == null ? "mi" : unit.toLowerCase(Locale.ROOT);
        if (u.startsWith("km")) {
            amount = amount.multiply(new BigDecimal("0.621371"));
        } else if (u.equals("m") || u.startsWith("meter")) {
            amount = amount.multiply(new BigDecimal("0.000621371"));
        }
        return amount.setScale(3, RoundingMode.HALF_UP);
    }

    static BigDecimal toKcal(String value, String unit) {
        BigDecimal amount = decimal(value);
        if (amount == null) {
            return null;
        }
        String u = unit == null ? "kcal" : unit.toLowerCase(Locale.ROOT);
        if (u.equals("kj")) {
            amount = amount.divide(new BigDecimal("4.184"), 4, RoundingMode.HALF_UP);
        }
        return amount.setScale(1, RoundingMode.HALF_UP);
    }

    private static BigDecimal decimal(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String clip(String text, int max) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static String fingerprint(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String joined = String.join("|", parts);
            return HexFormat.of().formatHex(digest.digest(joined.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    public record Parsed(List<ParsedWorkout> workouts, List<ParsedWeight> weights, boolean truncated) {}

    public record ParsedWorkout(
            String fingerprint,
            String activity,
            String sourceName,
            Instant startedAt,
            Instant endedAt,
            LocalDate day,
            int durationMinutes,
            BigDecimal distanceMiles,
            BigDecimal energyKcal) {}

    public record ParsedWeight(
            String fingerprint, LocalDate loggedOn, Instant measuredAt, BigDecimal weightLb, String sourceName) {}
}
