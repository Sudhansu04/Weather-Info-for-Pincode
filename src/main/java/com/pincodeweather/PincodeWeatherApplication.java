package com.pincodeweather;

import com.pincodeweather.config.OpenWeatherProperties;
import com.pincodeweather.config.WeatherProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({OpenWeatherProperties.class, WeatherProperties.class})
public class PincodeWeatherApplication {

    public static void main(String[] args) {
        SpringApplication.run(PincodeWeatherApplication.class, args);
    }
}
