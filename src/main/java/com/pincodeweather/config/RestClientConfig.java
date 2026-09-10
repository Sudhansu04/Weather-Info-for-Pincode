package com.pincodeweather.config;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * {@link RestClient} beans for the OpenWeather APIs, one per base URL, with connect/read timeouts
 * taken from {@link OpenWeatherProperties}. Inject by name with {@code @Qualifier}.
 */
@Configuration
public class RestClientConfig {

    public static final String GEOCODING_REST_CLIENT = "openWeatherGeocodingRestClient";
    public static final String WEATHER_REST_CLIENT = "openWeatherWeatherRestClient";

    @Bean(GEOCODING_REST_CLIENT)
    public RestClient openWeatherGeocodingRestClient(RestClient.Builder builder, OpenWeatherProperties properties) {
        return builder
                .baseUrl(properties.geocodingBaseUrl())
                .requestFactory(requestFactory(properties))
                .build();
    }

    @Bean(WEATHER_REST_CLIENT)
    public RestClient openWeatherWeatherRestClient(RestClient.Builder builder, OpenWeatherProperties properties) {
        return builder
                .baseUrl(properties.weatherBaseUrl())
                .requestFactory(requestFactory(properties))
                .build();
    }

    private static ClientHttpRequestFactory requestFactory(OpenWeatherProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(properties.connectTimeout())
                .withReadTimeout(properties.readTimeout());
        return ClientHttpRequestFactoryBuilder.simple().build(settings);
    }
}
