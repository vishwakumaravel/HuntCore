package com.huntcore.backendapi.repository;

import com.huntcore.backendapi.ingest.HeartbeatPayload;
import com.huntcore.backendapi.model.StoredServerState;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ServerStateRepository {

    private final JdbcTemplate jdbcTemplate;

    public ServerStateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long upsert(
        String serverId,
        HeartbeatPayload payload,
        String rawPayload,
        String activeMatchJson,
        String latestCompletedMatchJson,
        Instant receivedAt
    ) {
        jdbcTemplate.update(
            """
                insert into server_state (
                    server_id,
                    received_at,
                    captured_at,
                    plugin_version,
                    server_software,
                    server_version,
                    game_state,
                    prepared_match_count,
                    scouting_active,
                    queued_runner_count,
                    queued_hunter_count,
                    spectator_count,
                    queued_count,
                    ready_count,
                    head_start_seconds_remaining,
                    paused_resume_state,
                    active_match,
                    latest_completed_match,
                    raw_payload
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb)
                on conflict (server_id) do update
                set received_at = excluded.received_at,
                    captured_at = excluded.captured_at,
                    plugin_version = excluded.plugin_version,
                    server_software = excluded.server_software,
                    server_version = excluded.server_version,
                    game_state = excluded.game_state,
                    prepared_match_count = excluded.prepared_match_count,
                    scouting_active = excluded.scouting_active,
                    queued_runner_count = excluded.queued_runner_count,
                    queued_hunter_count = excluded.queued_hunter_count,
                    spectator_count = excluded.spectator_count,
                    queued_count = excluded.queued_count,
                    ready_count = excluded.ready_count,
                    head_start_seconds_remaining = excluded.head_start_seconds_remaining,
                    paused_resume_state = excluded.paused_resume_state,
                    active_match = excluded.active_match,
                    latest_completed_match = excluded.latest_completed_match,
                    raw_payload = excluded.raw_payload
                """,
            serverId,
            Timestamp.from(receivedAt),
            Timestamp.from(Instant.ofEpochMilli(payload.capturedAtMillis())),
            payload.pluginVersion(),
            payload.serverSoftware(),
            payload.serverVersion(),
            payload.gameState(),
            payload.preparedMatchCount(),
            payload.scoutingActive(),
            payload.queuedRunnerCount(),
            payload.queuedHunterCount(),
            payload.spectatorCount(),
            payload.queuedCount(),
            payload.readyCount(),
            payload.headStartSecondsRemaining(),
            payload.pausedResumeState(),
            activeMatchJson,
            latestCompletedMatchJson,
            rawPayload
        );
        return receivedAt.toEpochMilli();
    }

    public List<StoredServerState> findAll() {
        return jdbcTemplate.query(
            """
                select server_id, received_at, game_state, raw_payload
                from server_state
                order by received_at desc
                """,
            (resultSet, rowNum) -> new StoredServerState(
                resultSet.getString("server_id"),
                resultSet.getTimestamp("received_at").toInstant().toEpochMilli(),
                resultSet.getString("game_state"),
                resultSet.getString("raw_payload")
            )
        );
    }

    public Optional<StoredServerState> findByServerId(String serverId) {
        List<StoredServerState> results = jdbcTemplate.query(
            """
                select server_id, received_at, game_state, raw_payload
                from server_state
                where server_id = ?
                """,
            (resultSet, rowNum) -> new StoredServerState(
                resultSet.getString("server_id"),
                resultSet.getTimestamp("received_at").toInstant().toEpochMilli(),
                resultSet.getString("game_state"),
                resultSet.getString("raw_payload")
            ),
            serverId
        );
        return results.stream().findFirst();
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject("select count(*) from server_state", Long.class);
        return count == null ? 0L : count;
    }
}
