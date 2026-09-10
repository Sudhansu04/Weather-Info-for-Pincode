package com.pincodeweather.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Top-level OpenAPI metadata served at {@code /v3/api-docs} and rendered by Swagger UI.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI pincodeWeatherOpenApi(WeatherProperties weatherProperties) {
        String description = """
                Returns weather for an Indian pincode on a given date, using the relational database as a cache in
                front of OpenWeather.

                **Endpoint:** `GET /api/v1/weather?pincode=411014&for_date=2020-10-15`

                **Caching strategy**
                * *Location* — latitude/longitude are geocoded once per pincode and stored in `pincode_location`.
                  Every later request for that pincode skips the Geocoding API.
                * *Weather* — one row per (pincode, date) in `weather_record`. Repeat requests for the same pair are
                  served from the database without calling the Current Weather API.
                * *Same-day refresh* — a row for **today** is re-fetched when it is older than
                  `weather.same-day-refresh-after` (currently %s) so the reading stays reasonably current.
                * *Past dates* — rows are immutable. If nothing was stored for a past date the API returns 404, because
                  the free Current Weather API has no history.
                * *Future dates* — rejected with 400. "Today" is evaluated in the `%s` zone.

                The response reports `location.source` and `weatherSource` (`DATABASE` or `OPENWEATHER`) so clients
                can observe which parts were cached.
                """.formatted(weatherProperties.sameDayRefreshAfter().toMinutes() + " minutes", weatherProperties.zone());

        return new OpenAPI()
                .info(new Info()
                        .title("Pincode Weather API")
                        .version("1.0.0")
                        .description(description)
                        .contact(new Contact().name("Pincode Weather API maintainers"))
                        .license(new License().name("MIT")));
    }
}
