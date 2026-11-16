create table if not exists public.ai_request_limits (
  user_id uuid primary key references auth.users(id) on delete cascade,
  window_started_at timestamptz not null,
  request_count integer not null default 0,
  constraint ai_request_limits_count_nonnegative check (request_count >= 0)
);

alter table public.ai_request_limits enable row level security;
revoke all on table public.ai_request_limits from public, anon, authenticated;

create or replace function public.consume_ai_request_limit(
  p_user_id uuid,
  p_limit integer,
  p_window_seconds integer
)
returns boolean
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
  v_now timestamptz := clock_timestamp();
  v_count integer;
begin
  if p_user_id is null
     or p_limit < 1 or p_limit > 1000
     or p_window_seconds < 1 or p_window_seconds > 3600 then
    raise exception using
      errcode = '22023',
      message = 'invalid rate limit parameters';
  end if;

  insert into public.ai_request_limits (user_id, window_started_at, request_count)
  values (p_user_id, v_now, 1)
  on conflict (user_id) do update
  set
    window_started_at = case
      when public.ai_request_limits.window_started_at
        + make_interval(secs => p_window_seconds) <= excluded.window_started_at
      then excluded.window_started_at
      else public.ai_request_limits.window_started_at
    end,
    request_count = case
      when public.ai_request_limits.window_started_at
        + make_interval(secs => p_window_seconds) <= excluded.window_started_at
      then 1
      else public.ai_request_limits.request_count + 1
    end
  returning request_count into v_count;

  return v_count <= p_limit;
end;
$$;

revoke all on function public.consume_ai_request_limit(uuid, integer, integer)
  from public, anon, authenticated;
grant execute on function public.consume_ai_request_limit(uuid, integer, integer)
  to service_role;
