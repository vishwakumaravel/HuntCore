create table if not exists server_state (
    server_id varchar(100) primary key,
    received_at timestamptz not null,
    captured_at timestamptz not null,
    plugin_version varchar(64) not null,
    server_software varchar(64) not null,
    server_version text not null,
    game_state varchar(32) not null,
    prepared_match_count integer not null,
    scouting_active boolean not null,
    queued_runner_count integer not null,
    queued_hunter_count integer not null,
    spectator_count integer not null,
    queued_count integer not null,
    ready_count integer not null,
    head_start_seconds_remaining integer,
    paused_resume_state varchar(32),
    active_match jsonb,
    latest_completed_match jsonb,
    raw_payload jsonb not null
);

create table if not exists matches (
    id bigserial primary key,
    server_id varchar(100) not null,
    received_at timestamptz not null,
    ended_at timestamptz not null,
    duration_millis bigint not null,
    winner varchar(32) not null,
    reason text not null,
    runner_name varchar(64) not null,
    hunter_count integer not null,
    poi_name varchar(128) not null,
    poi_distance_blocks integer not null,
    match_world_base_name varchar(128) not null,
    payload_fingerprint varchar(64) not null unique,
    raw_payload jsonb not null
);

create index if not exists idx_matches_received_at on matches(received_at desc);
create index if not exists idx_matches_runner_name on matches(runner_name);

create table if not exists match_players (
    match_id bigint not null references matches(id) on delete cascade,
    player_name varchar(64) not null,
    role varchar(16) not null,
    kills integer not null,
    primary key (match_id, player_name)
);

create index if not exists idx_match_players_player_name on match_players(player_name);
