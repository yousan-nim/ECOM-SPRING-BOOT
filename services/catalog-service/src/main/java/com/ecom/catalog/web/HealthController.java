package com.ecom.catalog.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "System", description = "Catalog system health.")
@SecurityRequirements
public class HealthController {

    @GetMapping("/ping")
    @Operation(summary = "Liveness ping")
    public Map<String, Object> ping() {
        return Map.of(
                "status", "ok",
                "service", "catalog-service",
                "timestamp", Instant.now().toString()
        );
    }
}
