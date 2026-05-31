package com.svp.tracker.config;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Parses and validates inboxes for Contact us feedback. */
public final class FeedbackEmailAddresses {

    /** Roughly {@code @Email} — enough to reject garbage while accepting normal addresses. */
    private static final Pattern EMAIL =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private FeedbackEmailAddresses() {}

    public static List<String> parseCommaList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    public static boolean isValid(String email) {
        return email != null && EMAIL.matcher(email).matches();
    }

    public static List<String> validOnly(List<String> candidates) {
        Set<String> out = new LinkedHashSet<>();
        for (String c : candidates) {
            if (isValid(c)) {
                out.add(c);
            }
        }
        return List.copyOf(out);
    }

    public static List<String> minusExcluded(List<String> addresses, List<String> excluded) {
        if (excluded.isEmpty()) {
            return addresses;
        }
        Set<String> block = new LinkedHashSet<>(excluded);
        return addresses.stream().filter(a -> !block.contains(a)).toList();
    }
}
