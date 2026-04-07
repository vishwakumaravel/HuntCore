package com.huntcore.backendapi.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PublicPlayerStatsQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public PublicPlayerStatsQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long countPlayers() {
        Long count = jdbcTemplate.queryForObject("select count(distinct player_name) from match_players", Long.class);
        return count == null ? 0L : count;
    }

    public List<PublicQueryRows.PlayerStatsRow> findLeaderboard(PublicPlayerSort sort, int limit, int offset) {
        return jdbcTemplate.query(
            leaderboardQuery(sort),
            this::mapPlayerStatsRow,
            limit,
            offset
        );
    }

    public Optional<PublicQueryRows.PlayerStatsRow> findByPlayerName(String playerName) {
        List<PublicQueryRows.PlayerStatsRow> rows = jdbcTemplate.query(
            statsQuery() + " where stats.player_name = ?",
            this::mapPlayerStatsRow,
            playerName
        );
        return rows.stream().findFirst();
    }

    public List<PublicQueryRows.PlayerMatchRow> findPlayerMatches(String playerName, int limit, int offset) {
        return jdbcTemplate.query(
            """
                select
                    m.id,
                    m.server_id,
                    m.ended_at,
                    m.winner,
                    m.reason,
                    mp.role,
                    mp.kills,
                    m.runner_name,
                    m.hunter_count,
                    m.poi_name,
                    m.match_world_base_name
                from match_players mp
                join matches m on m.id = mp.match_id
                where mp.player_name = ?
                order by m.ended_at desc, m.received_at desc
                limit ? offset ?
                """,
            (resultSet, rowNum) -> new PublicQueryRows.PlayerMatchRow(
                resultSet.getLong("id"),
                resultSet.getString("server_id"),
                resultSet.getTimestamp("ended_at").toInstant().toEpochMilli(),
                resultSet.getString("winner"),
                resultSet.getString("reason"),
                resultSet.getString("role"),
                resultSet.getInt("kills"),
                resultSet.getString("runner_name"),
                resultSet.getInt("hunter_count"),
                resultSet.getString("poi_name"),
                resultSet.getString("match_world_base_name")
            ),
            playerName,
            limit,
            offset
        );
    }

    public long countPlayerMatches(String playerName) {
        Long count = jdbcTemplate.queryForObject(
            "select count(*) from match_players where player_name = ?",
            Long.class,
            playerName
        );
        return count == null ? 0L : count;
    }

    private PublicQueryRows.PlayerStatsRow mapPlayerStatsRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new PublicQueryRows.PlayerStatsRow(
            resultSet.getString("player_name"),
            resultSet.getLong("matches_played"),
            resultSet.getLong("wins"),
            resultSet.getLong("kills"),
            resultSet.getLong("runner_matches"),
            resultSet.getLong("hunter_matches"),
            resultSet.getLong("runner_wins"),
            resultSet.getLong("hunter_wins")
        );
    }

    private String leaderboardQuery(PublicPlayerSort sort) {
        return statsQuery()
            + " order by " + primarySortExpression(sort)
            + ", stats.wins desc"
            + ", stats.kills desc"
            + ", stats.matches_played desc"
            + ", stats.player_name asc"
            + " limit ? offset ?";
    }

    private String statsQuery() {
        return """
            select
                stats.player_name,
                stats.matches_played,
                stats.wins,
                stats.kills,
                stats.runner_matches,
                stats.hunter_matches,
                stats.runner_wins,
                stats.hunter_wins
            from (
                select
                    mp.player_name,
                    count(*) as matches_played,
                    sum(case
                        when (mp.role = 'RUNNER' and m.winner = 'Runner')
                            or (mp.role = 'HUNTER' and m.winner = 'Hunters')
                        then 1 else 0 end) as wins,
                    coalesce(sum(mp.kills), 0) as kills,
                    sum(case when mp.role = 'RUNNER' then 1 else 0 end) as runner_matches,
                    sum(case when mp.role = 'HUNTER' then 1 else 0 end) as hunter_matches,
                    sum(case when mp.role = 'RUNNER' and m.winner = 'Runner' then 1 else 0 end) as runner_wins,
                    sum(case when mp.role = 'HUNTER' and m.winner = 'Hunters' then 1 else 0 end) as hunter_wins
                from match_players mp
                join matches m on m.id = mp.match_id
                group by mp.player_name
            ) stats
            """;
    }

    private String primarySortExpression(PublicPlayerSort sort) {
        return switch (sort) {
            case KILLS -> "stats.kills desc";
            case MATCHES -> "stats.matches_played desc";
            case WIN_RATE -> """
                case
                    when stats.matches_played = 0 then 0
                    else stats.wins::numeric / stats.matches_played
                end desc
                """;
            case WINS -> "stats.wins desc";
        };
    }
}
