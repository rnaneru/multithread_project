package org.example.vacancyparser.hh;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HhRssParserTest {

    private final HhRssParser parser = new HhRssParser();

    @Test
    void parsesItemWithAllFields() {
        String xml = """
                <?xml version='1.0' encoding='utf-8'?>
                <rss version="2.0"><channel><title>HeadHunter Vacancy</title>
                <item>
                    <pubDate>2026-11-01T12:08:35.936+03:00</pubDate>
                    <title>Java-разработчик middle</title>
                    <link>https://hh.ru/vacancy/138784116</link>
                    <guid isPermaLink="true">https://hh.ru/vacancy/138784116</guid>
                    <description><![CDATA[<p>Вакансия компании: Платформа ОФД</p>
                    <p>Создана: 01.11.2026</p>
                    <p>Регион: Москва</p>
                    <p>Предполагаемый уровень месячного дохода: от 60 000 ₽</p>]]></description>
                </item>
                </channel></rss>
                """;

        List<HhRssItem> items = parser.parse(xml);

        assertThat(items).hasSize(1);
        HhRssItem item = items.get(0);
        assertThat(item.externalId()).isEqualTo("138784116");
        assertThat(item.title()).isEqualTo("Java-разработчик middle");
        assertThat(item.company()).isEqualTo("Платформа ОФД");
        assertThat(item.city()).isEqualTo("Москва");
        assertThat(item.salaryFrom()).isEqualTo(60_000);
        assertThat(item.salaryRaw()).isEqualTo("от 60 000 ₽");
        assertThat(item.publishedAt()).isEqualTo(Instant.parse("2026-11-01T09:08:35.936Z"));
        assertThat(item.url()).isEqualTo("https://hh.ru/vacancy/138784116");
    }

    @Test
    void skipsBrokenItems() {
        String xml = "<rss><channel><item><title></title></item></channel></rss>";

        assertThat(parser.parse(xml)).isEmpty();
    }
}
