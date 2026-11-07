package org.example.vacancyparser.hh;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SalaryParser {

    private static final Pattern NUMBER = Pattern.compile("\\d[\\d\\s\\u00A0\\u202F]*");
    private static final Pattern FROM_MARKER = Pattern.compile("(?:^|\\s)от(?=\\s)");
    private static final Pattern TO_MARKER = Pattern.compile("(?:^|\\s)до(?=\\s)");

    private SalaryParser() {
    }

    public record Salary(Double from, Double to, String currency) {
    }

    public static Salary parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new Salary(null, null, null);
        }

        String normalized = raw.replace('\u00A0', ' ').replace('\u202F', ' ').trim();
        String lower = normalized.toLowerCase(Locale.ROOT);

        if (lower.contains("не указан") || lower.contains("не указана") || lower.contains("не указано")) {
            return new Salary(null, null, null);
        }

        String currency = detectCurrency(lower);
        List<Double> numbers = extractNumbers(normalized);

        if (numbers.isEmpty()) {
            return new Salary(null, null, currency);
        }
        if (numbers.size() >= 2) {
            double first = numbers.get(0);
            double second = numbers.get(1);
            return new Salary(Math.min(first, second), Math.max(first, second), currency);
        }

        double value = numbers.get(0);
        if (FROM_MARKER.matcher(lower).find()) {
            return new Salary(value, null, currency);
        }
        if (TO_MARKER.matcher(lower).find()) {
            return new Salary(null, value, currency);
        }
        return new Salary(value, value, currency);
    }

    private static List<Double> extractNumbers(String value) {
        List<Double> result = new ArrayList<>();
        Matcher matcher = NUMBER.matcher(value);
        while (matcher.find()) {
            String digits = matcher.group().replaceAll("\\D", "");
            if (!digits.isBlank()) {
                try {
                    result.add(Double.parseDouble(digits));
                } catch (NumberFormatException ignored) {

                }
            }
        }
        return result;
    }

    private static String detectCurrency(String lower) {
        if (lower.contains("₽") || lower.contains("руб") || lower.contains("rub") || lower.contains("rur")) {
            return "RUB";
        }
        if (lower.contains("$") || lower.contains("usd") || lower.contains("доллар")) {
            return "USD";
        }
        if (lower.contains("€") || lower.contains("eur") || lower.contains("евро")) {
            return "EUR";
        }
        if (lower.contains("₸") || lower.contains("kzt") || lower.contains("тенге")) {
            return "KZT";
        }
        if (lower.contains("byn") || lower.contains("бел")) {
            return "BYN";
        }
        if (lower.contains("uzs") || lower.contains("сум")) {
            return "UZS";
        }
        if (lower.contains("₾") || lower.contains("gel") || lower.contains("лари")) {
            return "GEL";
        }
        return null;
    }
}
