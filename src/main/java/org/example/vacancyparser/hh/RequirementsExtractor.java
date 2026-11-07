package org.example.vacancyparser.hh;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class RequirementsExtractor {

    private static final Pattern REQUIREMENTS_HEADING = Pattern.compile(
            "(?iu)(требован|ожида|что важно|что мы жд[её]м|как мы жд[её]м|квалификац|навык|умени|must\\s*have|you\\s+have|requirements)",
            Pattern.UNICODE_CASE);

    private static final Pattern OTHER_HEADING = Pattern.compile(
            "(?iu)(обязанност|задач|предлаг|предлож|услови|льгот|бонус|о компании|о проекте|плюс|будет плюсом|benefits|responsibilit|offer|about)",
            Pattern.UNICODE_CASE);

    private static final Set<String> HEADING_TAGS = Set.of("h1", "h2", "h3", "h4", "h5", "strong", "b");
    private static final Set<String> ITEM_TAGS = Set.of("li", "p");

    private static final int MAX_ITEMS = 15;
    private static final int MAX_ITEM_LENGTH = 300;

    private RequirementsExtractor() {
    }

    public static String extract(Element descriptionRoot, String keySkills) {
        if (descriptionRoot == null) {
            return normalizeSkills(keySkills);
        }

        List<String> items = new ArrayList<>();
        boolean collecting = false;
        Elements elements = descriptionRoot.getAllElements();

        for (Element element : elements) {
            String tag = element.tagName().toLowerCase(Locale.ROOT);
            String ownText = element.ownText().replace('\u00A0', ' ').trim();
            if (ownText.isEmpty()) {
                continue;
            }

            boolean heading = HEADING_TAGS.contains(tag) || ownText.endsWith(":");
            if (heading) {
                if (REQUIREMENTS_HEADING.matcher(ownText).find()) {
                    collecting = true;
                } else if (OTHER_HEADING.matcher(ownText).find()) {
                    collecting = false;
                }
                continue;
            }

            if (collecting && ITEM_TAGS.contains(tag)) {
                String item = normalizeItem(ownText);
                if (!item.isBlank() && items.size() < MAX_ITEMS) {
                    items.add(item);
                }
            }
        }

        Set<String> unique = new LinkedHashSet<>(items);
        if (unique.isEmpty()) {
            return normalizeSkills(keySkills);
        }
        return truncate(String.join("; ", unique));
    }

    private static String normalizeSkills(String keySkills) {
        if (keySkills == null || keySkills.isBlank()) {
            return null;
        }
        return truncate("Ключевые навыки: " + keySkills);
    }

    private static String normalizeItem(String value) {
        String result = value.replaceAll("\\s+", " ").trim();
        while (result.startsWith("-") || result.startsWith("—") || result.startsWith("•")) {
            result = result.substring(1).trim();
        }
        if (result.length() > MAX_ITEM_LENGTH) {
            result = result.substring(0, MAX_ITEM_LENGTH) + "...";
        }
        return result;
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }
}
