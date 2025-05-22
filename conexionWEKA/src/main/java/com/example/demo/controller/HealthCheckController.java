package com.example.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;

import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthCheckController {

    @Value("${server.port:8080}")
    private String serverPort;
    
    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("port", serverPort);
        response.put("timestamp", System.currentTimeMillis());
        response.put("tempDir", System.getProperty("java.io.tmpdir"));
        return ResponseEntity.ok(response);
    }
}
