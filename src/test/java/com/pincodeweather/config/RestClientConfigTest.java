package com.pincodeweather.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RestClientConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
            .withBean(OpenWeatherProperties.class, () -> new OpenWeatherProperties(
                    "key", "https://geo.example/geo/1.0", "https://weather.example/data/2.5",
                    "metric", Duration.ofSeconds(2), Duration.ofSeconds(3)))
            .withUserConfiguration(RestClientConfig.class);

    @Test
    void exposesOneRestClientBeanPerOpenWeatherApi() {
        runner.run(context -> {
            assertThat(context).hasBean(RestClientConfig.GEOCODING_REST_CLIENT);
            assertThat(context).hasBean(RestClientConfig.WEATHER_REST_CLIENT);
            assertThat(context.getBeansOfType(RestClient.class)).hasSize(2);
            assertThat(context.getBean(RestClientConfig.GEOCODING_REST_CLIENT))
                    .isNotSameAs(context.getBean(RestClientConfig.WEATHER_REST_CLIENT));
        });
    }
}
