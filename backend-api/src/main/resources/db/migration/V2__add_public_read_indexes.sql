create index if not exists idx_matches_server_id_received_at on matches(server_id, received_at desc);
create index if not exists idx_matches_ended_at on matches(ended_at desc);
create index if not exists idx_match_players_player_name_role on match_players(player_name, role);
