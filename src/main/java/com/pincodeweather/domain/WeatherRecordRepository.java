package com.pincodeweather.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface WeatherRecordRepository extends JpaRepository<WeatherRecord, Long> {

    Optional<WeatherRecord> findByPincodeAndForDate(String pincode, LocalDate forDate);
}
