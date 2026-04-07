package com.huntcore.backendapi.repository;

import com.huntcore.backendapi.ingest.CompletedMatchPayload;
import com.huntcore.backendapi.model.MatchPlayerRow;
import com.huntcore.backendapi.model.StoredMatch;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class MatchRepository {

    private final JdbcTemplate jdbcTemplate;

    public MatchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<StoredMatch> findByFingerprint(String fingerprint) {
        List<StoredMatch> matches = jdbcTemplate.query(
            """
                select id, server_id, received_at, raw_payload
                from matches
                where payload_fingerprint = ?
                """,
            (resultSet, rowNum) -> new StoredMatch(
                resultSet.getLong("id"),
                resultSet.getString("server_id"),
                resultSet.getTimestamp("received_at").toInstant().toEpochMilli(),
                resultSet.getString("raw_payload")
            ),
            fingerprint
        );
        return matches.stream().findFirst();
    }

    public StoredMatch insert(String serverId, CompletedMatchPayload payload, String rawPayload, Instant receivedAt, List<MatchPlayerRow> players) {
        String fingerprint = fingerprint(rawPayload);
        Optional<StoredMatch> existing = findByFingerprint(fingerprint);
        if (existing.isPresent()) {
            return existing.get();
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(
                """
                    insert into matches (
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
                        payload_fingerprint,
                        raw_payload
                    )
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                    """,
                new String[] {"id"}
            );
            preparedStatement.setString(1, serverId);
            preparedStatement.setTimestamp(2, Timestamp.from(receivedAt));
            preparedStatement.setTimestamp(3, Timestamp.from(Instant.ofEpochMilli(payload.endedAtMillis())));
            preparedStatement.setLong(4, payload.durationMillis());
            preparedStatement.setString(5, payload.winner());
            preparedStatement.setString(6, payload.reason());
            preparedStatement.setString(7, payload.runnerName());
            preparedStatement.setInt(8, payload.hunterCount());
            preparedStatement.setString(9, payload.poiName());
            preparedStatement.setInt(10, payload.poiDistanceBlocks());
            preparedStatement.setString(11, payload.matchWorldBaseName());
            preparedStatement.setString(12, fingerprint);
            preparedStatement.setString(13, rawPayload);
            return preparedStatement;
        }, keyHolder);

        Number generatedId = extractGeneratedId(keyHolder);
        if (generatedId == null) {
            throw new IllegalStateException("Match insert did not return a generated id.");
        }

        long matchId = generatedId.longValue();
        insertMatchPlayers(matchId, players);
        return new StoredMatch(matchId, serverId, receivedAt.toEpochMilli(), rawPayload);
    }

    public List<StoredMatch> findRecent(int limit) {
        int clampedLimit = Math.max(1, Math.min(limit, 200));
        return jdbcTemplate.query(
            """
                select id, server_id, received_at, raw_payload
                from matches
                order by received_at desc
                limit ?
                """,
            (resultSet, rowNum) -> new StoredMatch(
                resultSet.getLong("id"),
                resultSet.getString("server_id"),
                resultSet.getTimestamp("received_at").toInstant().toEpochMilli(),
                resultSet.getString("raw_payload")
            ),
            clampedLimit
        );
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject("select count(*) from matches", Long.class);
        return count == null ? 0L : count;
    }

    private void insertMatchPlayers(long matchId, List<MatchPlayerRow> players) {
        jdbcTemplate.batchUpdate(
            """
                insert into match_players (match_id, player_name, role, kills)
                values (?, ?, ?, ?)
                """,
            players,
            players.size(),
            (preparedStatement, player) -> {
                preparedStatement.setLong(1, matchId);
                preparedStatement.setString(2, player.playerName());
                preparedStatement.setString(3, player.role());
                preparedStatement.setInt(4, player.kills());
            }
        );
    }

    private Number extractGeneratedId(KeyHolder keyHolder) {
        Number directKey = keyHolder.getKey();
        if (directKey != null) {
            return directKey;
        }

        Map<String, Object> keys = keyHolder.getKeys();
        if (keys == null) {
            return null;
        }

        Object id = keys.get("id");
        if (id instanceof Number number) {
            return number;
        }

        return null;
    }

    private String fingerprint(String rawPayload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
