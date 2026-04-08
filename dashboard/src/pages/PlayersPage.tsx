import { useDeferredValue, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { publicApi } from "../api";
import { EmptyState } from "../components/EmptyState";
import { formatPercent } from "../lib/format";
import type { PlayerSort } from "../types";

const PAGE_SIZE = 50;

export function PlayersPage() {
  const [sort, setSort] = useState<PlayerSort>("wins");
  const [search, setSearch] = useState("");
  const deferredSearch = useDeferredValue(search.trim().toLowerCase());

  const playersQuery = useQuery({
    queryKey: ["public-players", sort],
    queryFn: () => publicApi.getPlayers(sort, PAGE_SIZE, 0)
  });

  const filteredPlayers = useMemo(() => {
    const items = playersQuery.data?.items ?? [];
    if (!deferredSearch) {
      return items;
    }

    return items.filter((player) =>
      player.playerName.toLowerCase().includes(deferredSearch)
    );
  }, [deferredSearch, playersQuery.data?.items]);

  if (playersQuery.isLoading) {
    return <section className="panel">Loading player leaderboard...</section>;
  }

  if (playersQuery.isError) {
    return (
      <section className="panel error-panel">
        <h2>Could not load players</h2>
        <p>Check that the backend is reachable and has processed at least one match.</p>
      </section>
    );
  }

  const data = playersQuery.data;
  if (!data) {
    return <section className="panel">No player data returned yet.</section>;
  }

  if (data.items.length === 0) {
    return (
      <EmptyState
        title="No player stats yet"
        description="Finish one match and the leaderboard will populate from finalized match results."
      />
    );
  }

  return (
    <div className="page-stack">
      <section className="panel">
        <div className="section-header">
          <div>
            <p className="eyebrow">Lifetime stats</p>
            <h2>Player leaderboard</h2>
          </div>
          <p className="meta-line">{data.total} tracked players</p>
        </div>

        <div className="toolbar">
          <label className="toolbar-field">
            <span>Sort by</span>
            <select value={sort} onChange={(event) => setSort(event.target.value as PlayerSort)}>
              <option value="wins">Wins</option>
              <option value="kills">Kills</option>
              <option value="matches">Matches</option>
              <option value="winRate">Win rate</option>
            </select>
          </label>

          <label className="toolbar-field grow">
            <span>Filter players</span>
            <input
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Search by player name"
              type="search"
              value={search}
            />
          </label>
        </div>

        <div className="table-wrap">
          <table className="leaderboard-table">
            <thead>
              <tr>
                <th>Player</th>
                <th>Matches</th>
                <th>Wins</th>
                <th>Losses</th>
                <th>Kills</th>
                <th>Win rate</th>
                <th>Runner</th>
                <th>Hunters</th>
              </tr>
            </thead>
            <tbody>
              {filteredPlayers.map((player) => (
                <tr key={player.playerName}>
                  <td>
                    <Link className="table-link" to={`/players/${encodeURIComponent(player.playerName)}`}>
                      {player.playerName}
                    </Link>
                  </td>
                  <td>{player.matchesPlayed}</td>
                  <td>{player.wins}</td>
                  <td>{player.losses}</td>
                  <td>{player.kills}</td>
                  <td>{formatPercent(player.winRatePercent)}</td>
                  <td>
                    {player.runnerWins}/{player.runnerMatches}
                  </td>
                  <td>
                    {player.hunterWins}/{player.hunterMatches}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
