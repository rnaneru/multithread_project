package org.example.vacancyparser.hh;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class HhVacancyDetailsParser {

    private static final String SELECTOR_TITLE = "[data-qa=vacancy-title]";
    private static final String SELECTOR_COMPANY = "[data-qa=vacancy-company-name]";
    private static final String SELECTOR_ADDRESS = "[data-qa=vacancy-view-raw-address]";
    private static final String SELECTOR_LOCATION = "[data-qa=vacancy-view-location]";
    private static final String SELECTOR_DESCRIPTION = "[data-qa=vacancy-description]";
    private static final String SELECTOR_SKILLS = "[data-qa=skills-element]";
    private static final String SELECTOR_SALARY = "[data-qa=vacancy-salary]";

    private final ObjectMapper objectMapper;

    public HhVacancyDetails parse(String html, String pageUrl) {
        Document document = Jsoup.parse(html, pageUrl);

        String title = text(document, SELECTOR_TITLE);
        String company = text(document, SELECTOR_COMPANY);
        String city = extractCity(document);
        String salaryRaw = extractSalaryRaw(document);
        SalaryParser.Salary salary = SalaryParser.parse(salaryRaw);
        String keySkills = extractKeySkills(document);

        Element descriptionRoot = extractDescriptionRoot(document);
        String description = descriptionRoot != null ? truncate(descriptionRoot.text(), 4000) : null;
        String requirements = RequirementsExtractor.extract(descriptionRoot, keySkills);

        Instant publishedAt = null;
        JsonNode jobPosting = findJobPosting(document);
        if (jobPosting != null) {
            if (title == null) {
                title = textOrNull(jobPosting, "title");
            }
            if (company == null) {
                JsonNode organization = jobPosting.path("hiringOrganization");
                company = textOrNull(organization, "name");
            }
            if (publishedAt == null) {
                publishedAt = parseInstant(textOrNull(jobPosting, "datePosted"));
            }
            if (descriptionRoot == null) {
                String jsonDescription = textOrNull(jobPosting, "description");
                if (jsonDescription != null) {
                    Element jsonRoot = Jsoup.parseBodyFragment(jsonDescription).body();
                    description = truncate(jsonRoot.text(), 4000);
                    requirements = RequirementsExtractor.extract(jsonRoot, keySkills);
                }
            }
        }

        return new HhVacancyDetails(
                title,
                company,
                city,
                salaryRaw,
                salary.from(),
                salary.to(),
                salary.currency(),
                requirements,
                description,
                keySkills,
                publishedAt
        );
    }

    private static String text(Document document, String selector) {
        Element element = document.selectFirst(selector);
        if (element == null) {
            return null;
        }
        String value = element.text().replace('\u00A0', ' ').trim();
        return value.isEmpty() ? null : value;
    }

    private static String extractCity(Document document) {
        String address = text(document, SELECTOR_ADDRESS);
        if (address != null) {
            int comma = address.indexOf(',');
            String city = comma > 0 ? address.substring(0, comma) : address;
            return city.replace('\u00A0', ' ').trim();
        }
        return text(document, SELECTOR_LOCATION);
    }

    private static String extractSalaryRaw(Document document) {
        String salary = text(document, SELECTOR_SALARY);
        if (salary != null) {
            return salary;
        }

        Element title = document.selectFirst(SELECTOR_TITLE);
        if (title == null) {
            return null;
        }
        Element titleContainer = title.parent();
        if (titleContainer == null) {
            return null;
        }
        for (Element span : titleContainer.select("span")) {
            String value = span.text().replace('\u00A0', ' ').trim();
            String lower = value.toLowerCase(Locale.ROOT);
            if (value.isEmpty() || lower.contains("не указан")) {
                continue;
            }
            if (value.matches(".*\\d.*") && (value.contains("₽") || value.contains("$") || value.contains("€")
                    || lower.contains("руб") || lower.contains("usd") || lower.contains("eur"))) {
                return value;
            }
        }
        return null;
    }

    private static String extractKeySkills(Document document) {
        Set<String> skills = new LinkedHashSet<>();
        for (Element element : document.select(SELECTOR_SKILLS)) {
            String skill = element.text().replace('\u00A0', ' ').trim();
            if (!skill.isEmpty()) {
                skills.add(skill);
            }
        }
        return skills.isEmpty() ? null : String.join(", ", skills);
    }

    private static Element extractDescriptionRoot(Document document) {
        Element element = document.selectFirst(SELECTOR_DESCRIPTION);
        return element != null ? element : null;
    }

    private JsonNode findJobPosting(Document document) {
        List<JsonNode> candidates = new ArrayList<>();
        for (Element script : document.select("script[type=application/ld+json]")) {
            try {
                candidates.add(objectMapper.readTree(script.data()));
            } catch (Exception e) {
                log.debug("Не удалось разобрать JSON-LD: {}", e.getMessage());
            }
        }
        for (JsonNode candidate : candidates) {
            if ("JobPosting".equals(candidate.path("@type").asText())) {
                return candidate;
            }
            JsonNode graph = candidate.path("@graph");
            if (graph.isArray()) {
                for (JsonNode node : graph) {
                    if ("JobPosting".equals(node.path("@type").asText())) {
                        return node;
                    }
                }
            }
        }
        return null;
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static Instant parseInstant(String value) {
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
