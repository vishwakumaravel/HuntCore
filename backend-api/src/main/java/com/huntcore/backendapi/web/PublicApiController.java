package com.huntcore.backendapi.web;

import com.huntcore.backendapi.service.PublicStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
public class PublicApiController {

    private final PublicStatsService publicStatsService;

    public PublicApiController(PublicStatsService publicStatsService) {
        this.publicStatsService = publicStatsService;
    }

    @GetMapping("/servers")
    public PublicApiResponses.ServerListResponse listServers() {
        return publicStatsService.listServers();
    }

    @GetMapping("/servers/{serverId}")
    public PublicApiResponses.ServerDetail getServer(@PathVariable String serverId) {
        return publicStatsService.getServer(serverId);
    }

    @GetMapping("/matches")
    public PublicApiResponses.MatchListResponse listMatches(
        @RequestParam(defaultValue = "50") int limit,
        @RequestParam(defaultValue = "0") int offset
    ) {
        return publicStatsService.listMatches(limit, offset);
    }

    @GetMapping("/players")
    public PublicApiResponses.PlayerListResponse listPlayers(
        @RequestParam(defaultValue = "wins") String sort,
        @RequestParam(defaultValue = "50") int limit,
        @RequestParam(defaultValue = "0") int offset
    ) {
        return publicStatsService.listPlayers(sort, limit, offset);
    }

    @GetMapping("/players/{playerName}")
    public PublicApiResponses.PlayerDetail getPlayer(@PathVariable String playerName) {
        return publicStatsService.getPlayer(playerName);
    }

    @GetMapping("/players/{playerName}/matches")
    public PublicApiResponses.PlayerMatchHistoryResponse getPlayerMatches(
        @PathVariable String playerName,
        @RequestParam(defaultValue = "50") int limit,
        @RequestParam(defaultValue = "0") int offset
    ) {
        return publicStatsService.getPlayerMatches(playerName, limit, offset);
    }
}
