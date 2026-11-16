package com.urbanmove.fleet.infrastructure.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbanmove.fleet.domain.port.in.GetVehiclesUseCase;
import com.urbanmove.fleet.domain.port.in.ManageRidesUseCase;
import com.urbanmove.fleet.domain.port.in.ReserveVehicleUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.*;

@RestController
@RequestMapping("/api/v1/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    private final S3Client s3Client;
    private final String bucketName;
    private final ManageRidesUseCase manageRidesUseCase;
    private final GetVehiclesUseCase getVehiclesUseCase;

    public AdminController(
            S3Client s3Client,
            @Value("${cloud.aws.s3.bucket:urbanmove-data-lake}") String bucketName,
            ManageRidesUseCase manageRidesUseCase,
            GetVehiclesUseCase getVehiclesUseCase
    ) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.manageRidesUseCase = manageRidesUseCase;
        this.getVehiclesUseCase = getVehiclesUseCase;
    }

    @GetMapping("/datalake/objects")
    public ResponseEntity<List<Map<String, Object>>> getS3Objects() {
        try {
            ListObjectsV2Request req = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .build();
            List<S3Object> objects = s3Client.listObjectsV2(req).contents();
            List<Map<String, Object>> result = objects.stream().map(o -> {
                Map<String, Object> map = new HashMap<>();
                map.put("key", o.key());
                map.put("size", o.size());
                map.put("lastModified", o.lastModified().toString());
                return map;
            }).toList();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        var vehicles = getVehiclesUseCase.getAllVehicles();
        var rides = manageRidesUseCase.getAllRides();

        double totalRevenue = rides.stream().mapToDouble(r -> r.costAmount()).sum();
        double totalDistance = rides.stream().mapToDouble(r -> r.distanceKm()).sum();

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalVehicles", vehicles.size());
        metrics.put("availableVehicles", vehicles.stream().filter(v -> v.status().name().equals("AVAILABLE")).count());
        metrics.put("inUseVehicles", vehicles.stream().filter(v -> v.status().name().equals("IN_USE")).count());
        metrics.put("totalRides", rides.size());
        metrics.put("totalRevenue", Math.round(totalRevenue * 100.0) / 100.0);
        metrics.put("totalDistanceKm", Math.round(totalDistance * 100.0) / 100.0);

        return ResponseEntity.ok(metrics);
    }
}
