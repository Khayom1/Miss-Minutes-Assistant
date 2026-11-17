create table if not exists private.miss_minutes_tts_rate_limits (
  user_id uuid not null,
  window_start timestamptz not null,
  request_count integer not null default 1,
  constraint miss_minutes_tts_rate_limits_pkey primary key (user_id, window_start),
  constraint miss_minutes_tts_rate_limits_count_positive check (request_count > 0)
);
create index if not exists miss_minutes_tts_rate_limits_window_idx
  on private.miss_minutes_tts_rate_limits (window_start);
alter table private.miss_minutes_tts_rate_limits enable row level security;
revoke all on table private.miss_minutes_tts_rate_limits from public, anon, authenticated;
grant select, insert, update, delete on table private.miss_minutes_tts_rate_limits to service_role;

create or replace function public.consume_miss_minutes_tts_rate_limit(p_user_id uuid, p_limit integer)
returns boolean
language plpgsql
security invoker
set search_path = pg_catalog, private
as $$
declare
  v_window_start timestamptz := date_trunc('minute', clock_timestamp());
  v_request_count integer;
begin
  if p_user_id is null or p_limit is null or p_limit < 1 or p_limit > 10 then
    raise exception 'invalid rate limit parameters' using errcode = '22023';
  end if;
  delete from private.miss_minutes_tts_rate_limits
  where window_start < v_window_start - interval '24 hours';
  insert into private.miss_minutes_tts_rate_limits (user_id, window_start, request_count)
  values (p_user_id, v_window_start, 1)
  on conflict (user_id, window_start)
  do update set request_count = private.miss_minutes_tts_rate_limits.request_count + 1
  returning request_count into v_request_count;
  return v_request_count <= p_limit;
end;
$$;
revoke all on function public.consume_miss_minutes_tts_rate_limit(uuid, integer) from public;
revoke all on function public.consume_miss_minutes_tts_rate_limit(uuid, integer) from anon, authenticated;
grant execute on function public.consume_miss_minutes_tts_rate_limit(uuid, integer) to service_role;
