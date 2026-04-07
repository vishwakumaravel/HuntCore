package com.huntcore.backendapi.web;

import com.huntcore.backendapi.repository.MatchRepository;
import com.huntcore.backendapi.repository.ServerStateRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final ServerStateRepository serverStateRepository;
    private final MatchRepository matchRepository;

    public HealthController(ServerStateRepository serverStateRepository, MatchRepository matchRepository) {
        this.serverStateRepository = serverStateRepository;
        this.matchRepository = matchRepository;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse(
            "ok",
            "huntcore-backend-api",
            serverStateRepository.count(),
            matchRepository.count()
        );
    }

    public record HealthResponse(String status, String service, long heartbeatCount, long matchCount) {
    }
}
