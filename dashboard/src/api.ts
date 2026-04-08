import type {
  MatchListResponse,
  PlayerDetail,
  PlayerListResponse,
  PlayerMatchHistoryResponse,
  PlayerSort,
  ServerDetail,
  ServerListResponse
} from "./types";

const DEFAULT_API_BASE_URL = "http://127.0.0.1:8081";

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

function getApiBaseUrl(): string {
  const runtimeApiBaseUrl = window.__HUNTCORE_CONFIG__?.apiBaseUrl?.trim();

  return (
    runtimeApiBaseUrl ||
    import.meta.env.VITE_API_BASE_URL?.trim() ||
    DEFAULT_API_BASE_URL
  ).replace(/\/+$/, "");
}

async function fetchJson<T>(path: string): Promise<T> {
  const response = await fetch(`${getApiBaseUrl()}${path}`);
  if (!response.ok) {
    throw new ApiError(response.status, `Request failed with ${response.status}`);
  }
  return response.json() as Promise<T>;
}

export const publicApi = {
  get apiBaseUrl(): string {
    return getApiBaseUrl();
  },
  getServers(): Promise<ServerListResponse> {
    return fetchJson<ServerListResponse>("/api/v1/public/servers");
  },
  getServer(serverId: string): Promise<ServerDetail> {
    return fetchJson<ServerDetail>(
      `/api/v1/public/servers/${encodeURIComponent(serverId)}`
    );
  },
  getMatches(limit = 50, offset = 0): Promise<MatchListResponse> {
    return fetchJson<MatchListResponse>(
      `/api/v1/public/matches?limit=${limit}&offset=${offset}`
    );
  },
  getPlayers(sort: PlayerSort, limit = 50, offset = 0): Promise<PlayerListResponse> {
    return fetchJson<PlayerListResponse>(
      `/api/v1/public/players?sort=${encodeURIComponent(sort)}&limit=${limit}&offset=${offset}`
    );
  },
  getPlayer(playerName: string): Promise<PlayerDetail> {
    return fetchJson<PlayerDetail>(
      `/api/v1/public/players/${encodeURIComponent(playerName)}`
    );
  },
  getPlayerMatches(
    playerName: string,
    limit = 50,
    offset = 0
  ): Promise<PlayerMatchHistoryResponse> {
    return fetchJson<PlayerMatchHistoryResponse>(
      `/api/v1/public/players/${encodeURIComponent(playerName)}/matches?limit=${limit}&offset=${offset}`
    );
  }
};
