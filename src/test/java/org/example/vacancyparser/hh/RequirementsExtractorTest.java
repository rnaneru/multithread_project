package org.example.vacancyparser.hh;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequirementsExtractorTest {

    @Test
    void extractsItemsFromRequirementsSection() {
        String html = """
                <div>
                    <p><strong>Ключевые задачи:</strong></p>
                    <ul><li>Разрабатывать сервис на Java</li></ul>
                    <p><strong>Требования:</strong></p>
                    <ul>
                        <li>Опыт работы с Java от 3 лет</li>
                        <li>Знание Spring Boot и JPA</li>
                    </ul>
                    <p><strong>Что предлагаем:</strong></p>
                    <ul><li>ДМС и гибкий график</li></ul>
                </div>
                """;
        Element root = Jsoup.parseBodyFragment(html).body();

        String requirements = RequirementsExtractor.extract(root, "Java, Spring");

        assertThat(requirements)
                .contains("Опыт работы с Java от 3 лет")
                .contains("Знание Spring Boot и JPA")
                .doesNotContain("ДМС и гибкий график");
    }

    @Test
    void stopsAtOfferSectionWithNestedStrong() {
        String html = """
                <div>
                    <h3><strong>Требования</strong></h3>
                    <ul><li>Опыт коммерческой разработки на Java от 5 лет</li></ul>
                    <h3><strong><strong>Мы готовы предложить:</strong></strong></h3>
                    <ul><li>Гибридный формат работы</li><li>ДМС с первого дня</li></ul>
                </div>
                """;
        Element root = Jsoup.parseBodyFragment(html).body();

        String requirements = RequirementsExtractor.extract(root, null);

        assertThat(requirements)
                .contains("Опыт коммерческой разработки на Java от 5 лет")
                .doesNotContain("Гибридный формат работы")
                .doesNotContain("ДМС");
    }

    @Test
    void fallsBackToKeySkills() {
        Element root = Jsoup.parseBodyFragment("<div><p>Просто текст</p></div>").body();

        String requirements = RequirementsExtractor.extract(root, "Java, PostgreSQL");

        assertThat(requirements).isEqualTo("Ключевые навыки: Java, PostgreSQL");
    }

    @Test
    void returnsNullWhenNothingFound() {
        Element root = Jsoup.parseBodyFragment("<div><p>Просто текст</p></div>").body();

        assertThat(RequirementsExtractor.extract(root, null)).isNull();
    }
}
