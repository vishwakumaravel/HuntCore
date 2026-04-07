package com.huntcore.backendapi.repository;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PublicMatchQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public PublicMatchQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PublicQueryRows.MatchSummaryRow> findRecent(int limit, int offset) {
        return jdbcTemplate.query(
            """
                select
                    id,
                    server_id,
                    received_at,
                    ended_at,
                    duration_millis,
                    winner,
                    reason,
                    runner_name,
                    hunter_count,
                    poi_name,
                    poi_distance_blocks,
                    match_world_base_name,
                    raw_payload::text as raw_payload_text
                from matches
                order by ended_at desc, received_at desc
                limit ? offset ?
                """,
            (resultSet, rowNum) -> new PublicQueryRows.MatchSummaryRow(
                resultSet.getLong("id"),
                resultSet.getString("server_id"),
                resultSet.getTimestamp("received_at").toInstant().toEpochMilli(),
                resultSet.getTimestamp("ended_at").toInstant().toEpochMilli(),
                resultSet.getLong("duration_millis"),
                resultSet.getString("winner"),
                resultSet.getString("reason"),
                resultSet.getString("runner_name"),
                resultSet.getInt("hunter_count"),
                resultSet.getString("poi_name"),
                resultSet.getInt("poi_distance_blocks"),
                resultSet.getString("match_world_base_name"),
                resultSet.getString("raw_payload_text")
            ),
            limit,
            offset
        );
    }

    public long countAll() {
        Long count = jdbcTemplate.queryForObject("select count(*) from matches", Long.class);
        return count == null ? 0L : count;
    }
}
