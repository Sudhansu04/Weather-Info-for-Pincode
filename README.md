# Pincode Weather API

A Spring Boot 3.5 / Java 21 REST service that returns the weather for an Indian PIN code on a given date.
It uses a relational database as a cache in front of OpenWeather so that each pincode is geocoded at most once
and each (pincode, date) pair is fetched from the weather provider at most once (plus an optional same-day refresh).

```
GET /api/v1/weather?pincode=411014&for_date=2020-10-15
```

## Contents

- [Architecture](#architecture)
- [Data model](#data-model)
- [Caching and optimisation strategy](#caching-and-optimisation-strategy)
- [API reference](#api-reference)
- [Running the application](#running-the-application)
- [Tooling: Swagger, H2 console, Postman](#tooling-swagger-h2-console-postman)
- [Running the tests](#running-the-tests)
- [Configuration](#configuration)
- [Design notes and trade-offs](#design-notes-and-trade-offs)

## Architecture

Classic layered application; each layer depends only on the one below it.

| Layer | Package | Responsibility |
|---|---|---|
| API | `com.pincodeweather.api` | `WeatherController` (validation of the raw request), `GlobalExceptionHandler` (error mapping), `api.dto` (response records) |
| Service | `com.pincodeweather.service` | `WeatherService` / `WeatherServiceImpl`: the cache-first algorithm, date rules, `DataSource` reporting |
| Clients | `com.pincodeweather.client` | Provider-agnostic `GeocodingClient` and `WeatherClient` interfaces; `client.openweather` implements them with Spring `RestClient` |
| Domain | `com.pincodeweather.domain` | JPA entities `PincodeLocation`, `WeatherRecord` and their Spring Data repositories |
| Config | `com.pincodeweather.config` | Typed, validated `openweather.*` / `weather.*` properties, `RestClient`, `Clock`, OpenAPI metadata |

Request flow:

```
 GET /api/v1/weather?pincode=411014&for_date=2020-10-15
            │
            ▼
 ┌─────────────────────┐   400 on malformed pincode / date, missing params
 │  WeatherController  │──────────────────────────────────────────────────►
 └─────────┬───────────┘
           ▼
 ┌─────────────────────┐   400 if for_date is in the future
 │   WeatherService    │──────────────────────────────────────────────────►
 └─────────┬───────────┘
           │  1. location for pincode
           ├──────────► pincode_location (DB) ──hit──► lat/long        source = DATABASE
           │                   │ miss
           │                   └──────► OpenWeather Geocoding API ──► persist ─► source = OPENWEATHER
           │                                   │ unknown pincode → 404
           │  2. weather for (pincode, for_date)
           ├──────────► weather_record (DB) ──hit & fresh──► weather   weatherSource = DATABASE
           │                   │ miss / stale same-day row
           │                   ├── for_date == today ──► OpenWeather Current Weather API ─► upsert ─► OPENWEATHER
           │                   └── for_date <  today ──► 404 (no history in the free API)
           ▼
 200 WeatherResponse { location, weather, units, location.source, weatherSource, fetchedAt }
```

## Data model

Schema is managed by Flyway (`src/main/resources/db/migration/V1__create_pincode_and_weather_tables.sql`);
Hibernate runs with `ddl-auto: validate`.

### `pincode_location` — geocoding cache (one row per pincode)

| Column | Type | Notes |
|---|---|---|
| `pincode` | VARCHAR(10) | **PK** |
| `latitude` | DOUBLE PRECISION | not null |
| `longitude` | DOUBLE PRECISION | not null |
| `place_name` | VARCHAR(255) | locality name from the geocoder, nullable |
| `country` | VARCHAR(2) | ISO 3166 alpha-2, not null |
| `created_at` | TIMESTAMP WITH TIME ZONE | when the row was geocoded |

### `weather_record` — weather cache (one row per pincode + date)

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT identity | **PK** |
| `pincode` | VARCHAR(10) | FK → `pincode_location.pincode`, indexed |
| `for_date` | DATE | calendar date in the configured zone |
| `temperature`, `feels_like`, `temp_min`, `temp_max` | DOUBLE PRECISION | °C (metric) |
| `pressure` | INTEGER | hPa |
| `humidity` | INTEGER | % |
| `weather_main` | VARCHAR(64) | e.g. `Clouds` |
| `weather_description` | VARCHAR(255) | e.g. `scattered clouds` |
| `wind_speed` | DOUBLE PRECISION | m/s |
| `wind_degree` | INTEGER | degrees |
| `cloudiness` | INTEGER | % |
| `visibility` | INTEGER | metres |
| `sunrise`, `sunset` | TIMESTAMP WITH TIME ZONE | nullable |
| `observed_at` | TIMESTAMP WITH TIME ZONE | provider observation time (`dt`), not null |
| `fetched_at` | TIMESTAMP WITH TIME ZONE | when we called the provider; drives same-day refresh |
| `source` | VARCHAR(32) | e.g. `OPENWEATHER_CURRENT` |

Constraint `uq_weather_record_pincode_date UNIQUE (pincode, for_date)` guarantees a single row per pair, so a
same-day refresh updates the existing row rather than inserting a duplicate.

## Caching and optimisation strategy

The goal is to minimise paid/rate-limited calls to OpenWeather while keeping same-day data reasonably fresh.

1. **Geocode once per pincode.** The Geocoding API is called only when `pincode_location` has no row for the
   pincode. The result is persisted and reused forever (coordinates of a PIN code do not change).
2. **Fetch weather once per (pincode, date).** A hit in `weather_record` short-circuits the provider call.
3. **Same-day refresh TTL.** If `for_date` is *today* and the stored row's `fetched_at` is older than
   `weather.same-day-refresh-after` (default 60 minutes), the Current Weather API is called again and the row is
   updated in place. Within the TTL, repeat calls are served from the database.
4. **Past dates are immutable.** A row for a past date is returned as-is and never refreshed; it is the observation
   that was captured on that day.
5. **Past date, no row → 404.** The free Current Weather API returns only *now*; it cannot answer "what was the
   weather on 2020-10-15". Rather than silently returning today's reading for a historic date, the API responds
   `404 Not Found` with an explanatory message (see [Design notes](#design-notes-and-trade-offs)).
6. **Future dates → 400.** "Today" is evaluated in `weather.zone` (default `Asia/Kolkata`).
7. **Observability of caching.** Every response carries `location.source` and `weatherSource` (`DATABASE` or
   `OPENWEATHER`) so callers and tests can verify that the second call for the same input is served from the cache.

## API reference

### `GET /api/v1/weather`

| Query parameter | Required | Format | Description |
|---|---|---|---|
| `pincode` | yes | `^[1-9][0-9]{5}$` | 6-digit Indian PIN code, e.g. `411014` |
| `for_date` | yes | ISO `yyyy-MM-dd` | Date to fetch. Must not be in the future |

#### Example request

```bash
curl -s "http://localhost:8080/api/v1/weather?pincode=411014&for_date=$(date +%F)" | jq
```

#### Example `200 OK`

```json
{
  "pincode": "411014",
  "forDate": "2020-10-15",
  "location": {
    "latitude": 18.5679,
    "longitude": 73.9143,
    "placeName": "Pune",
    "country": "IN",
    "source": "DATABASE"
  },
  "weather": {
    "temperature": 27.3,
    "feelsLike": 29.1,
    "tempMin": 26.0,
    "tempMax": 28.5,
    "pressure": 1010,
    "humidity": 65,
    "condition": "Clouds",
    "description": "scattered clouds",
    "windSpeed": 3.6,
    "windDegree": 250,
    "cloudiness": 40,
    "visibility": 10000,
    "sunrise": "2020-10-15T01:00:12Z",
    "sunset": "2020-10-15T12:35:44Z",
    "observedAt": "2020-10-15T04:00:00Z"
  },
  "units": { "temperature": "°C", "windSpeed": "m/s", "pressure": "hPa", "visibility": "m" },
  "weatherSource": "OPENWEATHER",
  "fetchedAt": "2020-10-15T04:05:06Z"
}
```

#### Error responses

All errors share one body shape; `violations` is present only for bean-validation failures.

```json
{
  "timestamp": "2020-10-15T04:05:06Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/weather",
  "violations": [ { "field": "pincode", "message": "pincode must be a 6-digit Indian PIN code" } ]
}
```

| Status | When | Example message |
|---|---|---|
| `400 Bad Request` | `pincode` missing or not a 6-digit PIN code | `Required parameter 'pincode' is missing` / violations on `pincode` |
| `400 Bad Request` | `for_date` missing or not ISO `yyyy-MM-dd` (e.g. `15-10-2020`, `2020-13-45`) | `for_date must be in ISO format yyyy-MM-dd` |
| `400 Bad Request` | `for_date` is in the future | `for_date must not be in the future` |
| `404 Not Found` | Geocoder does not know the pincode | `No location found for pincode 999999` |
| `404 Not Found` | Past date with no stored weather | `No stored weather for 411014 on 2020-10-15 ...` |
| `502 Bad Gateway` | OpenWeather returned an error or an unexpected payload | `OpenWeather: HTTP 500 from provider` |
| `503 Service Unavailable` | `OPENWEATHER_API_KEY` is not set and a live call was needed | `OpenWeather API key is not configured. Set the OPENWEATHER_API_KEY environment variable.` |
| `500 Internal Server Error` | Anything unexpected (details are logged, not returned) | `An unexpected error occurred. Please try again later.` |

## Running the application

### Prerequisites

- **JDK 21+** and Maven 3.9+ (or use `./mvnw` if present).
  If several JDKs are installed, point `JAVA_HOME` at 21 first:
  ```bash
  export JAVA_HOME=$(/usr/libexec/java_home -v 21)                                   # macOS, any vendor
  # or, with Homebrew OpenJDK:
  export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
  ```
- A free OpenWeather API key: <https://home.openweathermap.org/api_keys>

### Default profile (embedded file-based H2)

```bash
export OPENWEATHER_API_KEY=your_key_here
mvn spring-boot:run
```

The H2 database is written to `./data/weatherdb.mv.db`, so cached pincodes and weather survive restarts.
Without an API key the application still starts; the first request that needs a live call returns `503`.

### PostgreSQL profile

```bash
export OPENWEATHER_API_KEY=your_key_here
export DB_URL=jdbc:postgresql://localhost:5432/weatherdb
export DB_USERNAME=weather
export DB_PASSWORD=weather
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

Flyway creates the schema on first start in either database.

### Windows

Install a JDK 21+ (e.g. [Eclipse Temurin](https://adoptium.net/temurin/releases/?version=21) or `winget install EclipseAdoptium.Temurin.21.JDK`)
and Maven (`winget install Apache.Maven`, or unzip from <https://maven.apache.org/download.cgi> and add its `bin` folder to `Path`).
Open a **new** terminal afterwards so the updated `Path`/`JAVA_HOME` are picked up, then check:

```powershell
java -version
mvn -version
```

**PowerShell**

```powershell
$env:OPENWEATHER_API_KEY = "your_key_here"
mvn spring-boot:run
# optional: different port
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8090"
```

**Command Prompt (cmd.exe)**

```bat
set OPENWEATHER_API_KEY=your_key_here
mvn spring-boot:run
```

**Run the packaged jar**

```powershell
mvn clean package
$env:OPENWEATHER_API_KEY = "your_key_here"
java -jar target\pincode-weather-api-1.0.0.jar
```

To make the key permanent for your user account instead of per-terminal:
`setx OPENWEATHER_API_KEY your_key_here` (takes effect in new terminals only).

Notes:
- The H2 database file is created at `.\data\weatherdb.mv.db` relative to the folder you run from.
- If port 8080 is in use, pass `--server.port=8090` as shown above and use that port in Swagger/Postman URLs.
- If Windows Firewall prompts on first start, allowing access is only needed for requests from other machines; localhost works either way.

### Build a jar

```bash
mvn clean package
java -jar target/pincode-weather-api-1.0.0.jar
```

## Tooling: Swagger, H2 console, Postman

| Tool | URL / location |
|---|---|
| Swagger UI | <http://localhost:8080/swagger-ui.html> |
| OpenAPI JSON | <http://localhost:8080/v3/api-docs> |
| Health | <http://localhost:8080/actuator/health> |
| H2 console (default profile only) | <http://localhost:8080/h2-console> — JDBC URL `jdbc:h2:file:./data/weatherdb`, user `sa`, empty password |
| Postman collection | [`postman/PincodeWeather.postman_collection.json`](postman/PincodeWeather.postman_collection.json) |

The Postman collection defines `baseUrl` (default `http://localhost:8080`) and computes `{{today}}` /
`{{tomorrow}}` in Asia/Kolkata in a pre-request script. Run "Weather for pincode today" twice to see
`weatherSource` switch from `OPENWEATHER` to `DATABASE`. Each request carries assertions on the status code.

## Running the tests

```bash
mvn test
```

| Package | What is covered |
|---|---|
| `com.pincodeweather.api` | `@WebMvcTest` slice: request validation, every error mapping, the full JSON contract; unit test for `WeatherResponse.from` |
| `com.pincodeweather.service` | Cache-first algorithm, same-day TTL, past/future date rules (mocked repositories and clients) |
| `com.pincodeweather.client.openweather` | Geocoding and Current Weather clients against a mock HTTP server |
| `com.pincodeweather.config` | `RestClient` wiring |
| `com.pincodeweather.domain` | `@DataJpaTest` for the repositories and Flyway schema |
| `com.pincodeweather.integration` | End-to-end `@SpringBootTest` through the HTTP layer |

Run a single package, e.g. the API slice only:

```bash
mvn test -Dtest='com.pincodeweather.api.*Test'
```

## Configuration

All properties live in `src/main/resources/application.yml` and can be overridden with environment variables or
`-D` system properties.

### `openweather.*`

| Property | Default | Description |
|---|---|---|
| `openweather.api-key` | `${OPENWEATHER_API_KEY:}` | OpenWeather API key. Empty → live calls fail with `503` |
| `openweather.geocoding-base-url` | `https://api.openweathermap.org/geo/1.0` | Geocoding API base URL (`/zip` endpoint) |
| `openweather.weather-base-url` | `https://api.openweathermap.org/data/2.5` | Current Weather API base URL (`/weather` endpoint) |
| `openweather.units` | `metric` | `metric` (°C, m/s), `imperial` or `standard` |
| `openweather.connect-timeout` | `5s` | HTTP connect timeout |
| `openweather.read-timeout` | `10s` | HTTP read timeout |

### `weather.*`

| Property | Default | Description |
|---|---|---|
| `weather.default-country` | `IN` | ISO 3166 alpha-2 country appended to pincodes when geocoding |
| `weather.zone` | `Asia/Kolkata` | Zone that defines "today" when interpreting `for_date` |
| `weather.same-day-refresh-after` | `60m` | A row for *today* older than this is re-fetched. Past rows are never refreshed |

Other notable settings: `spring.datasource.*` (H2 by default, PostgreSQL under the `postgres` profile),
`spring.flyway.locations`, `springdoc.*` paths, `spring.h2.console.*`.

## Design notes and trade-offs

- **Why past dates can return 404.** The free OpenWeather *Current Weather* endpoint only answers "what is the
  weather right now". It has no history, so a request for a date on which nobody queried that pincode cannot be
  fulfilled honestly. Returning today's observation for a historic date would be misleading, so the service
  returns `404` with an explicit message. Any past date that *was* queried on the day is served from the
  database indefinitely.
- **Why "today" uses a configurable zone.** `for_date` is a calendar date; whether it is "today", "past" or
  "future" depends on the zone. Indian pincodes imply `Asia/Kolkata`, but this is a property so the service can be
  deployed elsewhere. The `Clock` bean makes the rule deterministic in tests.
- **Why a same-day TTL rather than "always cache".** Weather changes during the day. Caching today's reading for
  a bounded period (`weather.same-day-refresh-after`) balances freshness against API quota. Set it very large to
  effectively cache once per day, or very small to almost always fetch live.
- **Why separate tables.** Coordinates are stable and shared by every date, so `pincode_location` is written once
  and read many times, while `weather_record` grows by one row per pincode per day. The FK keeps them consistent.
- **Backfilling history: extend with One Call 3.0 "timemachine".** OpenWeather's paid One Call 3.0 API
  (`/data/3.0/onecall/timemachine?lat=&lon=&dt=`) returns historical data. Because the service depends on the
  `WeatherClient` interface, adding history support is a matter of implementing a second client (e.g.
  `OpenWeatherHistoryClient implements WeatherClient` with a `weatherAt(lat, lon, date)` capability) and letting
  `WeatherServiceImpl` call it instead of throwing `WeatherDataUnavailableException` for past dates. The stored
  row's `source` column would record `OPENWEATHER_TIMEMACHINE`.
- **Swapping the geocoder: Google Geocoding, India Post, a local CSV.** `GeocodingClient` is provider-agnostic
  (`Optional<GeoLocation> lookup(pincode, countryCode)`). Register another `@Component` implementation (or a
  composite that falls back from one provider to the next) and no service or controller code changes.
- **Validation split.** Syntax is validated in the controller with bean validation (`@Pattern`,
  `@DateTimeFormat`), producing structured `violations`. Semantics (future date, unknown pincode, no history) live
  in the service where the calendar/clock and repositories are available, so the rules are unit-testable without
  HTTP.
- **Concurrency.** Two simultaneous first requests for the same pincode/date could both call the provider; the
  unique constraint on `(pincode, for_date)` prevents duplicate rows, and the loser can re-read the winner's row.
  A distributed lock or `INSERT ... ON CONFLICT` would tighten this further if traffic warrants.
- **Persistence choice.** H2 in file mode keeps the default developer experience zero-config while persisting the
  cache across restarts; the `postgres` profile is a drop-in for production. Flyway owns the schema so both
  databases stay in sync.
