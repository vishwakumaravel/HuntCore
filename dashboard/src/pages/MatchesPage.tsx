import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { publicApi } from "../api";
import { EmptyState } from "../components/EmptyState";
import { formatDateTime, formatDuration } from "../lib/format";

const PAGE_SIZE = 25;

export function MatchesPage() {
  const [offset, setOffset] = useState(0);

  const matchesQuery = useQuery({
    queryKey: ["public-matches", PAGE_SIZE, offset],
    queryFn: () => publicApi.getMatches(PAGE_SIZE, offset)
  });

  if (matchesQuery.isLoading) {
    return <section className="panel">Loading recent matches...</section>;
  }

  if (matchesQuery.isError) {
    return (
      <section className="panel error-panel">
        <h2>Could not load matches</h2>
        <p>Check that the backend is reachable and has at least one stored match.</p>
      </section>
    );
  }

  const matchesResponse = matchesQuery.data;
  if (!matchesResponse) {
    return <section className="panel">No match data returned yet.</section>;
  }

  const { items, total } = matchesResponse;

  if (items.length === 0) {
    return (
      <EmptyState
        title="No finished matches yet"
        description="Complete one hunt and the backend will start building this history automatically."
      />
    );
  }

  return (
    <div className="page-stack">
      <section className="panel">
        <div className="section-header">
          <div>
            <p className="eyebrow">Recent hunts</p>
            <h2>Match history</h2>
          </div>
          <p className="meta-line">{total} total stored match{total === 1 ? "" : "es"}</p>
        </div>

        <div className="match-list">
          {items.map((match) => (
            <article className="match-card" key={match.matchId}>
              <div className="match-card-header">
                <div>
                  <p className="eyebrow">Match #{match.matchId}</p>
                  <h3>{match.winner}</h3>
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
                    <dt>Duration</dt>
                    <dd>{formatDuration(match.durationMillis)}</dd>
                  </div>
                  <div>
                    <dt>POI</dt>
                    <dd>
                      {match.poiName} ({match.poiDistanceBlocks} blocks)
                    </dd>
                  </div>
                </dl>
              </div>

              <div className="kill-strip">
                {Object.entries(match.playerKills).map(([playerName, kills]) => (
                  <span className="kill-chip" key={`${match.matchId}-${playerName}`}>
                    {playerName}: {kills}
                  </span>
                ))}
              </div>
            </article>
          ))}
        </div>
      </section>

      {total > PAGE_SIZE ? (
        <section className="pagination-row">
          <button
            className="action-button"
            disabled={offset === 0}
            onClick={() => setOffset((current) => Math.max(0, current - PAGE_SIZE))}
            type="button"
          >
            Previous
          </button>
          <button
            className="action-button"
            disabled={offset + PAGE_SIZE >= total}
            onClick={() => setOffset((current) => current + PAGE_SIZE)}
            type="button"
          >
            Next
          </button>
        </section>
      ) : null}
    </div>
  );
}
