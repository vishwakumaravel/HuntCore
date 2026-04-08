import { useQuery } from "@tanstack/react-query";
import { publicApi } from "../api";
import { EmptyState } from "../components/EmptyState";
import { StatCard } from "../components/StatCard";
import { formatDateTime, formatDuration, formatHeartbeatAge } from "../lib/format";

export function OverviewPage() {
  const serversQuery = useQuery({
    queryKey: ["public-servers"],
    queryFn: () => publicApi.getServers(),
    refetchInterval: 15_000
  });

  const primaryServerId = serversQuery.data?.servers[0]?.serverId ?? null;

  const serverDetailQuery = useQuery({
    queryKey: ["public-server", primaryServerId],
    queryFn: () => publicApi.getServer(primaryServerId!),
    enabled: primaryServerId !== null,
    refetchInterval: 15_000
  });

  if (serversQuery.isLoading) {
    return <section className="panel">Loading live server view...</section>;
  }

  if (serversQuery.isError) {
    return (
      <section className="panel error-panel">
        <h2>Could not load server overview</h2>
        <p>Check that `backend-api` is reachable from the dashboard.</p>
      </section>
    );
  }

  const servers = serversQuery.data?.servers ?? [];

  if (servers.length === 0) {
    return (
      <EmptyState
        title="No servers reporting yet"
        description="Start Paper with HuntCore backend sync enabled and this page will populate automatically."
      />
    );
  }

  const primaryServer = serverDetailQuery.data;
  const activeMatch = primaryServer?.heartbeat.activeMatch ?? null;
  const latestCompletedMatch =
    primaryServer?.heartbeat.latestCompletedMatch ??
    servers[0]?.latestCompletedMatch ??
    null;

  return (
    <div className="page-stack">
      <section className="overview-grid">
        {servers.map((server) => (
          <article className="panel server-summary-card" key={server.serverId}>
            <div className="server-summary-header">
              <div>
                <p className="eyebrow">{server.serverId}</p>
                <h2>{server.gameState.replaceAll("_", " ")}</h2>
              </div>
              <span className={server.isOnline ? "status-pill online" : "status-pill offline"}>
                {server.isOnline ? "Online" : "Offline"}
              </span>
            </div>

            <div className="stat-grid compact">
              <StatCard label="Queued" value={server.queuedCount} />
              <StatCard label="Ready" value={server.readyCount} accent="warm" />
              <StatCard
                label="Last heartbeat"
                value={formatHeartbeatAge(server.lastHeartbeatAgeSeconds)}
                accent="cool"
              />
            </div>

            <p className="meta-line">Updated {formatDateTime(server.receivedAtMillis)}</p>
          </article>
        ))}
      </section>

      {primaryServer ? (
        <section className="panel">
          <div className="section-header">
            <div>
              <p className="eyebrow">Primary server detail</p>
              <h2>{primaryServer.serverId}</h2>
            </div>
            <p className="meta-line">
              Heartbeat captured {formatDateTime(primaryServer.heartbeat.capturedAtMillis)}
            </p>
          </div>

          <div className="stat-grid">
            <StatCard label="Game state" value={primaryServer.heartbeat.gameState} />
            <StatCard
              accent="cool"
              label="Prepared matches"
              value={primaryServer.heartbeat.preparedMatchCount}
            />
            <StatCard
              accent="warm"
              label="Spectators"
              value={primaryServer.heartbeat.spectatorCount}
            />
            <StatCard
              detail={primaryServer.heartbeat.pluginVersion}
              label="Server software"
              value={primaryServer.heartbeat.serverSoftware}
            />
          </div>
        </section>
      ) : null}

      {activeMatch ? (
        <section className="panel spotlight-panel">
          <div className="section-header">
            <div>
              <p className="eyebrow">Active hunt</p>
              <h2>
                {activeMatch.runnerName} vs {activeMatch.hunterCount} hunter
                {activeMatch.hunterCount === 1 ? "" : "s"}
              </h2>
            </div>
            <p className="meta-line">Started {formatDateTime(activeMatch.startedAtMillis)}</p>
          </div>

          <div className="spotlight-grid">
            <div className="spotlight-block">
              <h3>Hunters</h3>
              <ul className="tag-list">
                {activeMatch.hunterNames.map((hunter) => (
                  <li key={hunter}>{hunter}</li>
                ))}
              </ul>
            </div>
            <div className="spotlight-block">
              <h3>POI</h3>
              <p>
                {activeMatch.poiName} at {activeMatch.poiDistanceBlocks} blocks
              </p>
              <p className="meta-line">{activeMatch.matchWorldBaseName}</p>
            </div>
          </div>
        </section>
      ) : latestCompletedMatch ? (
        <section className="panel spotlight-panel">
          <div className="section-header">
            <div>
              <p className="eyebrow">Latest completed match</p>
              <h2>{latestCompletedMatch.winner}</h2>
            </div>
            <p className="meta-line">
              Ended {formatDateTime(latestCompletedMatch.endedAtMillis)}
            </p>
          </div>

          <div className="stat-grid">
            <StatCard label="Runner" value={latestCompletedMatch.runnerName} />
            <StatCard
              accent="warm"
              label="Duration"
              value={formatDuration(latestCompletedMatch.durationMillis)}
            />
            <StatCard
              accent="cool"
              detail={`${latestCompletedMatch.poiDistanceBlocks} blocks away`}
              label="POI"
              value={latestCompletedMatch.poiName}
            />
          </div>

          <p className="match-reason">{latestCompletedMatch.reason}</p>
        </section>
      ) : null}
    </div>
  );
}
