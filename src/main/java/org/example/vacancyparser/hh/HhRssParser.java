package org.example.vacancyparser.hh;

import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class HhRssParser {

    private static final Pattern ITEM_PATTERN = Pattern.compile("<item>(.*?)</item>", Pattern.DOTALL);
    private static final Pattern TITLE_PATTERN = Pattern.compile("<title>(.*?)</title>", Pattern.DOTALL);
    private static final Pattern LINK_PATTERN = Pattern.compile("<link>(.*?)</link>", Pattern.DOTALL);
    private static final Pattern GUID_PATTERN = Pattern.compile("<guid[^>]*>(.*?)</guid>", Pattern.DOTALL);
    private static final Pattern DESCRIPTION_PATTERN = Pattern.compile(
            "<description>(?:<!\\[CDATA\\[)?(.*?)(?:]]>)?</description>", Pattern.DOTALL);
    private static final Pattern PUB_DATE_PATTERN = Pattern.compile("<pubDate>(.*?)</pubDate>", Pattern.DOTALL);
    private static final Pattern ID_PATTERN = Pattern.compile("/(\\d+)/?$");

    private static final DateTimeFormatter HH_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT);

    private static final String COMPANY_PREFIX = "Вакансия компании:";
    private static final String CREATED_PREFIX = "Создана:";
    private static final String REGION_PREFIX = "Регион:";
    private static final String SALARY_LABEL = "доход";

    public List<HhRssItem> parse(String xml) {
        List<HhRssItem> result = new ArrayList<>();
        Matcher itemMatcher = ITEM_PATTERN.matcher(xml);
        while (itemMatcher.find()) {
            String itemXml = itemMatcher.group(1);
            HhRssItem item = parseItem(itemXml);
            if (item != null) {
                result.add(item);
            }
        }
        return result;
    }

    private HhRssItem parseItem(String itemXml) {
        String title = clean(firstGroup(TITLE_PATTERN, itemXml));
        String link = clean(firstGroup(LINK_PATTERN, itemXml));
        String guid = clean(firstGroup(GUID_PATTERN, itemXml));
        String descriptionHtml = firstGroup(DESCRIPTION_PATTERN, itemXml);

        if (title.isBlank() || link.isBlank()) {
            return null;
        }

        String externalId = extractExternalId(link);
        if (externalId == null) {
            externalId = extractExternalId(guid);
        }
        if (externalId == null) {
            return null;
        }

        String company = null;
        String city = null;
        String salaryRaw = null;
        LocalDate createdDate = null;

        List<String> lines = Jsoup.parse(descriptionHtml == null ? "" : descriptionHtml).select("p").eachText();
        for (String line : lines) {
            String normalized = line.replace('\u00A0', ' ').trim();
            if (normalized.startsWith(COMPANY_PREFIX)) {
                company = afterLabel(normalized);
            } else if (normalized.startsWith(CREATED_PREFIX)) {
                createdDate = parseDate(afterLabel(normalized));
            } else if (normalized.startsWith(REGION_PREFIX)) {
                city = afterLabel(normalized);
            } else if (normalized.toLowerCase(Locale.ROOT).contains(SALARY_LABEL)) {
                salaryRaw = afterLabel(normalized);
            }
        }

        Instant publishedAt = parseInstant(firstGroup(PUB_DATE_PATTERN, itemXml));
        if (publishedAt == null && createdDate != null) {
            publishedAt = createdDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        }

        SalaryParser.Salary salary = SalaryParser.parse(salaryRaw);

        return new HhRssItem(
                externalId,
                title,
                company,
                city,
                salaryRaw,
                salary.from(),
                salary.to(),
                salary.currency(),
                publishedAt,
                link
        );
    }

    private static String firstGroup(Pattern pattern, String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String extractExternalId(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = ID_PATTERN.matcher(value.trim());
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String afterLabel(String line) {
        int index = line.indexOf(':');
        if (index < 0) {
            return null;
        }
        String value = line.substring(index + 1).trim();
        return value.isEmpty() ? null : value;
    }

    private static LocalDate parseDate(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), HH_DATE);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value.trim()).toInstant();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        return Parser.unescapeEntities(value, false).trim();
    }
}
