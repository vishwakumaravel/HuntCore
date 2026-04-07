package com.huntcore.backendapi.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PublicServerQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public PublicServerQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PublicQueryRows.ServerSummaryRow> findAllSummaries() {
        return jdbcTemplate.query(
            """
                select
                    server_id,
                    received_at,
                    game_state,
                    queued_count,
                    ready_count,
                    active_match is not null as has_active_match,
                    latest_completed_match::text as latest_completed_match_json
                from server_state
                order by received_at desc
                """,
            (resultSet, rowNum) -> new PublicQueryRows.ServerSummaryRow(
                resultSet.getString("server_id"),
                resultSet.getTimestamp("received_at").toInstant().toEpochMilli(),
                resultSet.getString("game_state"),
                resultSet.getInt("queued_count"),
                resultSet.getInt("ready_count"),
                resultSet.getBoolean("has_active_match"),
                resultSet.getString("latest_completed_match_json")
            )
        );
    }

    public Optional<PublicQueryRows.ServerDetailRow> findByServerId(String serverId) {
        List<PublicQueryRows.ServerDetailRow> rows = jdbcTemplate.query(
            """
                select server_id, received_at, raw_payload::text as raw_heartbeat_json
                from server_state
                where server_id = ?
                """,
            (resultSet, rowNum) -> new PublicQueryRows.ServerDetailRow(
                resultSet.getString("server_id"),
                resultSet.getTimestamp("received_at").toInstant().toEpochMilli(),
                resultSet.getString("raw_heartbeat_json")
            ),
            serverId
        );
        return rows.stream().findFirst();
    }
}
