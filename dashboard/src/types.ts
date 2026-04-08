export interface CompletedMatchSummary {
  endedAtMillis: number;
  durationMillis: number;
  winner: string;
  reason: string;
  runnerName: string;
  hunterCount: number;
  poiName: string;
  poiDistanceBlocks: number;
  matchWorldBaseName: string;
  playerKills: Record<string, number>;
}

export interface ActiveMatchSummary {
  runnerName: string;
  hunterNames: string[];
  hunterCount: number;
  poiName: string;
  poiDistanceBlocks: number;
  matchWorldBaseName: string;
  startedAtMillis: number;
}

export interface HeartbeatPayload {
  capturedAtMillis: number;
  pluginVersion: string;
  serverSoftware: string;
  serverVersion: string;
  gameState: string;
  preparedMatchCount: number;
  scoutingActive: boolean;
  queuedRunnerCount: number;
  queuedHunterCount: number;
  spectatorCount: number;
  queuedCount: number;
  readyCount: number;
  headStartSecondsRemaining: number | null;
  pausedResumeState: string | null;
  activeMatch: ActiveMatchSummary | null;
  latestCompletedMatch: CompletedMatchSummary | null;
}

export interface ServerSummary {
  serverId: string;
  receivedAtMillis: number;
  gameState: string;
  queuedCount: number;
  readyCount: number;
  isOnline: boolean;
  lastHeartbeatAgeSeconds: number;
  hasActiveMatch: boolean;
  latestCompletedMatch: CompletedMatchSummary | null;
}

export interface ServerListResponse {
  servers: ServerSummary[];
}

export interface ServerDetail {
  serverId: string;
  receivedAtMillis: number;
  isOnline: boolean;
  lastHeartbeatAgeSeconds: number;
  heartbeat: HeartbeatPayload;
}

export interface MatchSummary {
  matchId: number;
  serverId: string;
  receivedAtMillis: number;
  endedAtMillis: number;
  durationMillis: number;
  winner: string;
  reason: string;
  runnerName: string;
  hunterCount: number;
  poiName: string;
  poiDistanceBlocks: number;
  matchWorldBaseName: string;
  playerKills: Record<string, number>;
}

export interface MatchListResponse {
  items: MatchSummary[];
  limit: number;
  offset: number;
  total: number;
}

export interface PlayerLeaderboardEntry {
  playerName: string;
  matchesPlayed: number;
  wins: number;
  losses: number;
  kills: number;
  winRatePercent: number;
  runnerMatches: number;
  hunterMatches: number;
  runnerWins: number;
  hunterWins: number;
}

export interface PlayerListResponse {
  items: PlayerLeaderboardEntry[];
  limit: number;
  offset: number;
  total: number;
  sort: PlayerSort;
}

export interface PlayerMatch {
  matchId: number;
  serverId: string;
  endedAtMillis: number;
  winner: string;
  reason: string;
  role: string;
  kills: number;
  wasWin: boolean;
  runnerName: string;
  hunterCount: number;
  poiName: string;
  matchWorldBaseName: string;
}

export interface PlayerDetail {
  playerName: string;
  matchesPlayed: number;
  wins: number;
  losses: number;
  kills: number;
  winRatePercent: number;
  runnerMatches: number;
  hunterMatches: number;
  runnerWins: number;
  hunterWins: number;
  recentMatches: PlayerMatch[];
}

export interface PlayerMatchHistoryResponse {
  playerName: string;
  items: PlayerMatch[];
  limit: number;
  offset: number;
  total: number;
}

export type PlayerSort = "wins" | "kills" | "matches" | "winRate";
