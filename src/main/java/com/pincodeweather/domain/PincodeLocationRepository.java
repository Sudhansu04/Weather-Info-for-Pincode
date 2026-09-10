package com.pincodeweather.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PincodeLocationRepository extends JpaRepository<PincodeLocation, String> {
}
