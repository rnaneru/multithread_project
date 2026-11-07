# Парсер вакансий hh.ru

Итоговый проект по многопоточности: Spring Boot-приложение собирает вакансии с живого hh.ru,
сохраняет их в H2 и отдаёт через REST API с фильтрацией, сортировкой и пагинацией.
Данные берутся из публичной RSS-ленты hh.ru, а детали (требования, описание, навыки, зарплата) дочитываются с HTML-страниц вакансий. Парсинг выполняют кастомные пулы потоков, виртуальные потоки и `StructuredTaskScope`, статистика — `ForkJoinPool`, а фоновая очистка —
`ScheduledExecutorService`.

---

## 1. Возможности

- Парсинг вакансий hh.ru по поисковому запросу: название, компания, город, зарплата, требования, описание, ключевые навыки, дата публикации, ссылка.
- Автоматический парсинг при старте приложения и по расписанию каждые 30 минут.
- Ручной запуск парсинга: асинхронно, синхронно и по прямой ссылке на вакансию.
- Хранение в in-memory H2, upsert по `externalId` — повторный парсинг не создаёт дубликаты.
- Выдача результатов: пагинация, сортировка по 6 полям, фильтры по городу, зарплате и тексту.
- Аналитика: топ городов, компаний и навыков, статистика зарплат.
- Отдельный расчёт статистики зарплат через `ForkJoinPool` + `RecursiveTask`.
- Диагностика: состояние пула парсера, история задач, ожидание новой пачки данных.
- Простая веб-панель на `/` для проверки без Postman.

---

## 2. Стек

| Компонент | Версия / роль |
|---|---|
| Java | 21+ (проверено на 21.0.12 и 26.0.2) |
| Spring Boot | 3.5.16, Spring MVC |
| Spring Data JPA | хранение вакансий |
| H2 | in-memory база `jdbc:h2:mem:vacancies` |
| Spring WebFlux | `WebClient` для запросов к hh.ru |
| Jsoup | разбор RSS и HTML |
| Lombok | 1.18.48, генерация кода |
| Maven Wrapper | сборка без установленного Maven |
| JUnit 5 + AssertJ | тесты |

---

## 3. Запуск

### Требования

- **JDK 21 или новее**;
- доступ в интернет — данные берутся с живого hh.ru;
- порт **8080** свободен.

### IntelliJ IDEA

1. Открыть папку `vacancy-parser` как Maven-проект.
2. Дождаться загрузки зависимостей.
3. Если `pom.xml` менялся — нажать **Reload All Maven Projects**.
4. Запустить `org.example.vacancyparser.VacancyParserApplication`.
5. Открыть <http://localhost:8080/>.

---

## 4. Пошаговая проверка

Все примеры ниже даны для `http://localhost:8080`. Если вы сменили порт — подставьте свой.

### Шаг 1. Статус автопарсинга

```powershell
curl.exe "http://localhost:8080/api/parse/jobs"
```

Ответ — массив задач, самая свежая первая:

```json
[
  {
    "jobId": "4aa0da71-9e73-401e-8fae-d7137bcacbca",
    "query": "Java",
    "area": 1,
    "requestedPages": 1,
    "enrichDetails": true,
    "status": "COMPLETED",
    "startedAt": "2026-11-05T13:02:20.254Z",
    "finishedAt": "2026-11-05T13:02:22.651Z",
    "durationMs": 2397,
    "pagesFetched": 1,
    "rssItems": 20,
    "uniqueItems": 20,
    "detailsFetched": 20,
    "detailsFailed": 0,
    "created": 20,
    "updated": 0,
    "warnings": [],
    "error": null
  }
]
```

### Шаг 2. Список вакансий

Откройте в браузере:

<http://localhost:8080/answer>

или

```powershell
curl.exe "http://localhost:8080/answer?page=0&size=5"
```

Структура ответа:

```json
{
  "content": [
    {
      "id": 1,
      "externalId": "138784116",
      "title": "Java-разработчик",
      "company": "ООО Пример",
      "city": "Москва",
      "salaryFrom": 200000.0,
      "salaryTo": 300000.0,
      "salaryCurrency": "RUR",
      "salaryRaw": "200 000 – 300 000 ₽",
      "requirements": "Опыт Java от 3 лет; Spring Boot; PostgreSQL",
      "description": "Разработка backend-сервисов...",
      "keySkills": "Java, Spring Boot, PostgreSQL",
      "publishedAt": "2026-11-05T09:15:00Z",
      "fetchedAt": "2026-11-05T13:02:22.640Z",
      "url": "https://hh.ru/vacancy/138784116",
      "source": "hh.ru"
    }
  ],
  "page": 0,
  "size": 5,
  "totalElements": 20,
  "totalPages": 4,
  "first": true,
  "last": false
}
```

### Шаг 3. Фильтры и сортировка

По городу и минимальной зарплате:

```powershell
curl.exe "http://localhost:8080/answer?page=0&size=10&city=Москва&minSalary=150000"
```

Поиск по названию, компании, требованиям и навыкам:

```powershell
curl.exe "http://localhost:8080/answer?search=Spring&sort=salary&direction=desc"
```

Сортировка по названию:

```powershell
curl.exe "http://localhost:8080/answer?sort=title&direction=asc&size=5"
```

Одна вакансия по внутреннему id:

```powershell
curl.exe "http://localhost:8080/api/vacancies/1"
```

Последние вакансии без пагинации:

```powershell
curl.exe "http://localhost:8080/api/vacancies/top?limit=3"
```

Если id не существует — `404`.

### Шаг 4. Сводная статистика

<http://localhost:8080/api/stats>

```powershell
curl.exe "http://localhost:8080/api/stats"
```

```json
{
  "totalVacancies": 20,
  "byCity": {
    "Москва": 11,
    "Санкт-Петербург": 4,
    "Екатеринбург": 2
  },
  "topCompanies": {
    "ООО Пример": 3,
    "АО Второй": 2
  },
  "salary": {
    "withSalary": 14,
    "average": 214500.0,
    "min": 80000.0,
    "max": 400000.0
  },
  "topSkills": [
    { "skill": "Java", "count": 18 },
    { "skill": "Spring Boot", "count": 12 }
  ]
}
```

### Шаг 5. Статистика зарплат через ForkJoinPool

<http://localhost:8080/api/stats/salary-forkjoin>

```powershell
curl.exe "http://localhost:8080/api/stats/salary-forkjoin"
```

```json
{
  "count": 14,
  "min": 80000.0,
  "max": 400000.0,
  "average": 214500.0,
  "total": 3003000.0
}
```

### Шаг 6. Состояние пула парсера

<http://localhost:8080/api/executor/stats>

```powershell
curl.exe "http://localhost:8080/api/executor/stats"
```

```json
{
  "corePoolSize": 4,
  "maxPoolSize": 8,
  "poolSize": 4,
  "activeThreads": 0,
  "queuedTasks": 0,
  "submittedTasks": 21,
  "completedTasks": 21
}
```

### Шаг 7. Ожидание новой пачки данных

Эндпоинт блокируется, пока не завершится следующая запись в базу, либо пока не истечёт таймаут:

```powershell
curl.exe "http://localhost:8080/api/vacancies/await?version=0&timeoutMs=30000"
```

Если данные уже есть:

```json
{
  "version": 1,
  "created": 20,
  "updated": 0,
  "arrived": true
}
```

Если за отведённое время ничего не пришло:

```json
{
  "version": 1,
  "created": 20,
  "updated": 0,
  "arrived": false
}
```

Чтобы увидеть ожидание вживую: запустите запрос с `version`, равной текущей, и параллельно
выполните ручной парсинг (шаг 8).

### Шаг 8. Ручной асинхронный парсинг

```powershell
curl.exe -X POST "http://localhost:8080/api/parse/run?query=Java&area=1&pages=1&enrichDetails=true"
```

Ответ `202 Accepted`:

```json
{
  "jobId": "b0f1c6d4-1f2a-4c6e-9a3d-7f0e2b6c9d11",
  "query": "Java",
  "area": 1,
  "requestedPages": 1,
  "enrichDetails": true,
  "status": "PENDING",
  "startedAt": "2026-11-05T13:20:00.000Z",
  "finishedAt": null,
  "durationMs": 0,
  "pagesFetched": 0,
  "rssItems": 0,
  "uniqueItems": 0,
  "detailsFetched": 0,
  "detailsFailed": 0,
  "created": 0,
  "updated": 0,
  "warnings": [],
  "error": null
}
```

То же самое через JSON-тело:

```powershell
curl.exe -X POST "http://localhost:8080/api/parse" `
  -H "Content-Type: application/json" `
  -d "{\"query\":\"Java\",\"area\":1,\"pages\":1,\"enrichDetails\":true}"
```

Проверить конкретную задачу:

```powershell
curl.exe "http://localhost:8080/api/parse/jobs/b0f1c6d4-1f2a-4c6e-9a3d-7f0e2b6c9d11"
```

### Шаг 9. Синхронный парсинг

Запрос ждёт завершения задачи (до `app.parser.sync-timeout-ms`, по умолчанию 120 секунд)
и возвращает итоговое состояние:

```powershell
curl.exe -X POST "http://localhost:8080/api/parse/run-sync?query=Java&area=1&pages=1&enrichDetails=true"
```

Если задача не успела завершиться, вернётся `200 OK` с промежуточным состоянием
(`status: RUNNING`) — это нормально, задачу можно дочитать через `/api/parse/jobs/{id}`.

### Шаг 10. Разбор одной вакансии по прямой ссылке

Возьмите любую актуальную ссылку с hh.ru, например:

```powershell
curl.exe -X POST "http://localhost:8080/api/parse/vacancy?url=https://hh.ru/vacancy/138784116"
```

Ответ `200 OK` — сохранённая вакансия в формате `VacancyDto`.
Поддерживаются региональные ссылки вида `https://tyumen.hh.ru/vacancy/123456`.
Некорректная ссылка → `400`, ошибка hh.ru → `502`.

### Шаг 11. Консоль H2

<http://localhost:8080/h2-console>

- JDBC URL: `jdbc:h2:mem:vacancies`
- User: `sa`
- Password: пустой

Запрос:

```sql
SELECT id, external_id, title, city, salary_from, salary_to FROM vacancies ORDER BY id;
```

### Шаг 12. Веб-панель

<http://localhost:8080/> — форма запуска парсинга, сводка, список задач и таблица результатов с постраничной навигацией. Все те же данные, что и в API, но без Postman.

---

## 5. API

Базовый URL: `http://localhost:8080`.

### Вакансии

| Метод | Эндпоинт | Описание |
|---|---|---|
| GET | `/` | Веб-панель |
| GET | `/answer` | Постраничный список с фильтрами и сортировкой |
| GET | `/api/vacancies` | То же, что `/answer` |
| GET | `/api/vacancies/{id}` | Вакансия по внутреннему id, `404` если нет |
| GET | `/api/vacancies/top?limit=10` | Последние вакансии, `limit` 1–1000 |
| GET | `/api/vacancies/count` | Количество вакансий |
| GET | `/api/vacancies/await?version=0&timeoutMs=30000` | Ожидание следующей записи в базу, таймаут до 120000 мс |

Параметры `/answer` и `/api/vacancies`:

| Параметр | По умолчанию | Описание |
|---|---|---|
| `page` | `0` | Номер страницы, начиная с 0 |
| `size` | `20` | Размер страницы, максимум 200 |
| `sort` | `publishedAt` | `publishedAt`, `createdAt`, `title`, `company`, `city`, `salary` |
| `direction` | `desc` | `asc` или `desc` |
| `city` | — | Подстрока названия города, без учёта регистра |
| `minSalary` | — | Минимальная зарплата |
| `search` | — | Подстрока в названии, компании, требованиях или навыках |

Для фильтра и сортировки по зарплате используется эффективное значение
`max(salaryFrom, salaryTo)`; вакансии без зарплаты в выборку по `minSalary` не попадают.

### Парсинг

| Метод | Эндпоинт | Описание |
|---|---|---|
| POST | `/api/parse` | Асинхронный запуск, тело `ParseRequest`, ответ `202` |
| POST | `/api/parse/run?query=&area=&pages=&enrichDetails=` | Асинхронный запуск через query-параметры |
| POST | `/api/parse/run-sync?query=&area=&pages=&enrichDetails=` | Запуск с ожиданием результата |
| POST | `/api/parse/vacancy?url=` | Разбор одной вакансии по ссылке hh.ru |
| GET | `/api/parse/jobs` | История задач, новые первыми, максимум 50 |
| GET | `/api/parse/jobs/{jobId}` | Одна задача, `404` если нет |

Тело `ParseRequest`:

| Поле | Ограничения | По умолчанию |
|---|---|---|
| `query` | не пустое, до 100 символов | `app.parser.default-query` = `Java` |
| `area` | 1–100000 | `app.parser.default-area` = `1` (Москва) |
| `pages` | 1–10 | `app.parser.default-pages` = `1` |
| `enrichDetails` | `true` / `false` | `true` |

### Аналитика и диагностика

| Метод | Эндпоинт | Описание |
|---|---|---|
| GET | `/api/stats` | Города, компании, зарплаты, топ-10 навыков |
| GET | `/api/stats/salary-forkjoin` | Статистика зарплат через `ForkJoinPool` + `RecursiveTask` |
| GET | `/api/executor/stats` | Состояние пула `hhParserExecutor` |
| GET | `/h2-console` | Консоль H2 |

### Ошибки

Все ошибки возвращаются в едином формате:

```json
{
  "timestamp": "2026-11-05T13:25:00.123Z",
  "status": 400,
  "error": "Bad Request",
  "message": "query не должен быть пустым"
}
```

| Код | Когда |
|---|---|
| `400` | Невалидные параметры или ссылка |
| `404` | Вакансия или задача не найдена |
| `409` | Очередь задач переполнена или парсинг остановлен |
| `502` | hh.ru недоступен или вернул ошибку |

---

## 6. Архитектура и многопоточность

### Поток выполнения

1. `VacancyScheduler` при `ApplicationReadyEvent` и по `@Scheduled` отправляет задачу в `ParseTaskQueue`.
2. Единственный потребитель `parse-orchestrator` забирает задачу из очереди и запускает её.
3. Страницы RSS загружаются параллельно через `CompletableFuture` на пуле `hhParserExecutor`.
4. Уникальные вакансии складываются в `ConcurrentHashMap`, порядок — в `ConcurrentLinkedQueue`.
5. Детали вакансий загружаются выбранным режимом (`virtual`, `platform`, `structured`)
   с ограничением параллелизма через `Semaphore`.
6. `VacancyIngestionService` выполняет upsert в H2 в одной транзакции.
7. После коммита инвалидируется кэш статистики и срабатывает `notifyAll` в `VacancyIngestionMonitor`.
8. `VacancyQueryService` фильтрует, сортирует и пагинирует через `parallelStream` по данным из H2.
9. `ParseMaintenanceService` каждые 5 минут чистит историю задач через `ScheduledExecutorService`.

### Режимы загрузки деталей

| Режим | Реализация | Как включить |
|---|---|---|
| `virtual` (по умолчанию) | `Executors.newVirtualThreadPerTaskExecutor()` | `app.parser.detail-mode=virtual` |
| `platform` | `CompletableFuture.runAsync` на `ThreadPoolExecutor` | `app.parser.detail-mode=platform` |
| `structured` | `StructuredTaskScope.ShutdownOnFailure` | `app.parser.detail-mode=structured` |

---

## 7. Настройки

Все параметры находятся в `src/main/resources/application.properties`
и переопределяются аргументами запуска, например
`--app.parser.max-details=50`.

### Приложение и БД

| Параметр | Значение | Описание |
|---|---|---|
| `server.port` | `8080` | HTTP-порт |
| `spring.datasource.url` | `jdbc:h2:mem:vacancies;...` | In-memory H2 |
| `spring.jpa.hibernate.ddl-auto` | `create-drop` | Схема пересоздаётся при старте, данные не сохраняются между запусками |
| `spring.h2.console.enabled` | `true` | Консоль `/h2-console` |

### Парсер

| Параметр | По умолчанию | Описание |
|---|---|---|
| `app.parser.rss-base-url` | `https://hh.ru/search/vacancy/rss` | RSS-лента поиска |
| `app.parser.vacancy-base-url` | `https://hh.ru/vacancy` | База ссылок на вакансии |
| `app.parser.user-agent` | браузерный UA | hh.ru отклоняет запросы без User-Agent |
| `app.parser.default-query` | `Java` | Запрос по умолчанию |
| `app.parser.default-area` | `1` | Регион по умолчанию (Москва) |
| `app.parser.default-pages` | `1` | Страниц RSS по умолчанию |
| `app.parser.max-pages` | `10` | Максимум страниц RSS |
| `app.parser.max-details` | `20` | Сколько детальных страниц дочитывать за задачу |
| `app.parser.detail-concurrency` | `4` | Одновременных запросов за деталями |
| `app.parser.detail-delay-ms` | `100` | Пауза перед запросом деталей |
| `app.parser.detail-mode` | `virtual` | `virtual`, `platform` или `structured` |
| `app.parser.request-timeout-ms` | `25000` | Таймаут запроса к hh.ru |
| `app.parser.max-attempts` | `3` | Попыток на один запрос |
| `app.parser.executor-core-size` | `4` | Core-размер `hhParserExecutor` |
| `app.parser.executor-max-size` | `8` | Максимальный размер пула |
| `app.parser.executor-queue-capacity` | `200` | Очередь пула и ёмкость `ParseTaskQueue` |
| `app.parser.startup-pages` | `1` | Страниц при старте |
| `app.parser.schedule-pages` | `1` | Страниц по расписанию |
| `app.parser.startup-enabled` | `true` | Парсить при старте |
| `app.parser.schedule-enabled` | `true` | Парсить по расписанию |
| `app.parser.schedule-initial-delay-ms` | `900000` | Первый плановый запуск через 15 минут |
| `app.parser.schedule-delay-ms` | `1800000` | Интервал планового запуска — 30 минут |
| `app.parser.sync-timeout-ms` | `120000` | Ожидание в `/api/parse/run-sync` |

Пример запуска с другими настройками:

```powershell
java -jar target\vacancy-parser-1.0.0.jar `
  --server.port=8081 `
  --app.parser.detail-mode=platform `
  --app.parser.max-details=50 `
  --app.parser.schedule-enabled=false
```

---

## 8. Структура проекта

```text
vacancy-parser/
├── pom.xml
├── mvnw / mvnw.cmd
└── src/
    ├── main/
    │   ├── java/org/example/vacancyparser/
    │   │   ├── VacancyParserApplication.java
    │   │   ├── config/
    │   │   │   ├── ExecutorConfig.java        # пулы: hhParserExecutor, orchestrator, scheduler
    │   │   │   ├── ParserProperties.java      # настройки app.parser.*
    │   │   │   └── WebClientConfig.java       # WebClient с редиректами и таймаутами
    │   │   ├── controller/
    │   │   │   ├── AnswerController.java      # /answer и /api/vacancies
    │   │   │   ├── ExecutorController.java    # /api/executor/stats
    │   │   │   ├── ParseController.java       # /api/parse/**
    │   │   │   ├── StatsController.java       # /api/stats/**
    │   │   │   └── VacancyAwaitController.java# /api/vacancies/await
    │   │   ├── domain/Vacancy.java            # JPA-сущность
    │   │   ├── dto/                           # ответы и запросы API
    │   │   ├── hh/
    │   │   │   ├── HhClient.java              # HTTP + ретраи
    │   │   │   ├── HhRssParser.java           # разбор RSS
    │   │   │   ├── HhVacancyDetailsParser.java# разбор HTML-страницы
    │   │   │   ├── SalaryParser.java          # разбор зарплаты
    │   │   │   ├── RequirementsExtractor.java # требования из описания
    │   │   │   ├── PlatformDetailLoader.java  # режим platform
    │   │   │   ├── VirtualThreadDetailLoader.java # режим virtual
    │   │   │   └── StructuredDetailLoader.java    # режим structured
    │   │   ├── repository/VacancyRepository.java
    │   │   ├── service/
    │   │   │   ├── ParseJobService.java       # оркестрация, очередь, история задач
    │   │   │   ├── ParseTaskQueue.java        # ReentrantLock + Condition
    │   │   │   ├── ParseMaintenanceService.java
    │   │   │   ├── SalaryStatisticsService.java
    │   │   │   ├── StatsService.java          # кэш под ReadWriteLock
    │   │   │   ├── VacancyIngestionService.java
    │   │   │   ├── VacancyIngestionMonitor.java
    │   │   │   ├── VacancyQueryService.java   # parallelStream
    │   │   │   ├── VacancyScheduler.java
    │   │   │   └── VacancyUrlService.java
    │   │   ├── util/TraceContext.java         # ThreadLocal
    │   │   └── web/GlobalExceptionHandler.java
    │   └── resources/
    │       ├── application.properties
    │       └── static/index.html              # веб-панель
    └── test/java/...                          # 16 тестов
```

---

## 9. Тесты

```powershell
.\mvnw.cmd test
```

Покрыто 16 тестами:

- разбор RSS и HTML;
- извлечение требований и ключевых навыков;
- парсинг зарплат в разных форматах;
- bounded-очередь `ParseTaskQueue` (backpressure и остановка);
- `ForkJoinPool`-статистика зарплат;
- `wait/notifyAll` в `VacancyIngestionMonitor`.

Ожидаемый результат:

```text
Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```
