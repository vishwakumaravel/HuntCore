import { useQuery } from "@tanstack/react-query";
import { Link, useParams } from "react-router-dom";
import { ApiError, publicApi } from "../api";
import { EmptyState } from "../components/EmptyState";
import { StatCard } from "../components/StatCard";
import { formatCountLabel, formatDateTime, formatPercent } from "../lib/format";

export function PlayerPage() {
  const { playerName } = useParams<{ playerName: string }>();
  const decodedPlayerName = playerName ? decodeURIComponent(playerName) : null;

  const playerQuery = useQuery({
    queryKey: ["public-player", decodedPlayerName],
    queryFn: () => publicApi.getPlayer(decodedPlayerName!),
    enabled: decodedPlayerName !== null
  });

  const historyQuery = useQuery({
    queryKey: ["public-player-history", decodedPlayerName],
    queryFn: () => publicApi.getPlayerMatches(decodedPlayerName!, 25, 0),
    enabled: decodedPlayerName !== null
  });

  if (!decodedPlayerName) {
    return (
      <EmptyState
        title="Player not specified"
        description="Choose a player from the leaderboard to view their stats page."
      />
    );
  }

  if (playerQuery.isLoading || historyQuery.isLoading) {
    return <section className="panel">Loading player profile...</section>;
  }

  if (playerQuery.isError) {
    const error = playerQuery.error;
    if (error instanceof ApiError && error.status === 404) {
      return (
        <EmptyState
          title="Player not found"
          description="This player does not have stored stats yet."
        />
      );
    }

    return (
      <section className="panel error-panel">
        <h2>Could not load player detail</h2>
        <p>Check that the backend is reachable and the player exists.</p>
      </section>
    );
  }

  if (historyQuery.isError) {
    return (
      <section className="panel error-panel">
        <h2>Could not load player match history</h2>
        <p>Try refreshing after the backend finishes processing recent matches.</p>
      </section>
    );
  }

  const player = playerQuery.data;
  const history = historyQuery.data;

  if (!player || !history) {
    return <section className="panel">No player data returned yet.</section>;
  }

  return (
    <div className="page-stack">
      <section className="panel">
        <div className="section-header">
          <div>
            <p className="eyebrow">Player profile</p>
            <h2>{player.playerName}</h2>
          </div>
          <Link className="back-link" to="/players">
            Back to leaderboard
          </Link>
        </div>

        <div className="stat-grid">
          <StatCard label="Matches played" value={player.matchesPlayed} />
          <StatCard accent="warm" label="Wins" value={player.wins} />
          <StatCard label="Losses" value={player.losses} />
          <StatCard accent="cool" label="Kills" value={player.kills} />
          <StatCard label="Win rate" value={formatPercent(player.winRatePercent)} />
          <StatCard
            detail={`${formatCountLabel(player.runnerWins, "runner win")}`}
            label="Runner matches"
            value={formatCountLabel(player.runnerMatches, "runner match")}
          />
          <StatCard
            detail={`${formatCountLabel(player.hunterWins, "hunter win")}`}
            label="Hunter matches"
            value={formatCountLabel(player.hunterMatches, "hunter match")}
          />
        </div>
      </section>

      <section className="panel">
        <div className="section-header">
          <div>
            <p className="eyebrow">Recent matches</p>
            <h2>{history.total} total result{history.total === 1 ? "" : "s"}</h2>
          </div>
        </div>

        {history.items.length === 0 ? (
          <p className="meta-line">No stored match history for this player yet.</p>
        ) : (
          <div className="match-list">
            {history.items.map((match) => (
              <article className="match-card" key={`${match.matchId}-${match.role}`}>
                <div className="match-card-header">
                  <div>
                    <p className="eyebrow">{match.role}</p>
                    <h3>{match.wasWin ? "Win" : "Loss"}</h3>
                  </div>
                  <p className="meta-line">{formatDateTime(match.endedAtMillis)}</p>
                </div>

                <div className="match-card-body">
                  <p className="match-reason">{match.reason}</p>
                  <dl className="detail-grid">
                    <div>
                      <dt>Runner</dt>
                      <dd>{match.runnerName}</dd>
                    </div>
                    <div>
                      <dt>Hunters</dt>
                      <dd>{match.hunterCount}</dd>
                    </div>
                    <div>
                      <dt>Kills</dt>
                      <dd>{match.kills}</dd>
                    </div>
                    <div>
                      <dt>POI</dt>
                      <dd>{match.poiName}</dd>
                    </div>
                    <div>
                      <dt>Winner</dt>
                      <dd>{match.winner}</dd>
                    </div>
                    <div>
                      <dt>World</dt>
                      <dd>{match.matchWorldBaseName}</dd>
                    </div>
                  </dl>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
