-- ─────────────────────────────────────────────────────────────────────────────
-- 0012 — Video call signaling table.
--
-- Why
-- ----
-- Video calls in FixBid are routed through the public Jitsi Meet bridge
-- (`meet.jit.si`) loaded inside an in-app WebView. We don't run the media
-- bridge ourselves, but we DO need:
--   1. A persistent record of every call attempt (caller, callee, status,
--      duration) so the conversation feed can show "📹 Cuộc gọi 02:34"
--      bubbles after the call ends.
--   2. Realtime "ringing → accepted/rejected" signaling so the callee's
--      device shows an incoming-call dialog without the caller having to
--      send a separate ping over chat.
--
-- The Jitsi room name is derived deterministically from the call row id
-- (`fixbid-{callId}`) — both peers join the same room without any extra
-- coordination once they read the same row.
-- ─────────────────────────────────────────────────────────────────────────────

create table if not exists public.video_calls (
    id uuid not null default uuid_generate_v4(),
    conversation_id uuid not null,
    caller_id uuid not null,
    callee_id uuid not null,
    -- Lifecycle: ringing → accepted | rejected | missed | ended.
    -- We keep one column instead of separate flags so the UI can flip a
    -- single value (the realtime listener subscribes to UPDATE on this row).
    status text not null default 'ringing'
        check (status in ('ringing', 'accepted', 'rejected', 'missed', 'ended')),
    started_at timestamp with time zone not null default now(),
    answered_at timestamp with time zone,
    ended_at timestamp with time zone,
    -- Computed once when the call ends so clients don't have to do
    -- timestamptz math just to render a bubble like "Cuộc gọi 02:34".
    duration_seconds integer,
    constraint video_calls_pkey primary key (id),
    constraint video_calls_conversation_id_fkey
        foreign key (conversation_id) references public.conversations(id)
        on delete cascade,
    constraint video_calls_caller_id_fkey
        foreign key (caller_id) references public.profiles(id),
    constraint video_calls_callee_id_fkey
        foreign key (callee_id) references public.profiles(id)
);

-- Per-user inbox: "what calls am I involved in", newest first. Powers both
-- the call-history list and the global ringing observer.
create index if not exists video_calls_callee_status_idx
    on public.video_calls (callee_id, status, started_at desc);
create index if not exists video_calls_caller_status_idx
    on public.video_calls (caller_id, status, started_at desc);
create index if not exists video_calls_conversation_idx
    on public.video_calls (conversation_id, started_at desc);

-- Realtime: needed so `postgresChangeFlow<Update>` fires on the callee
-- when the row's status flips ringing → accepted/rejected.
do $$
begin
    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename = 'video_calls'
    ) then
        alter publication supabase_realtime add table public.video_calls;
    end if;
end$$;

-- Row-level security: a user can read & write only the calls they're a
-- party to. Anyone authenticated may insert (so the caller can create a
-- ringing row addressed to the callee).
alter table public.video_calls enable row level security;

drop policy if exists "video_calls_select_party" on public.video_calls;
create policy "video_calls_select_party"
    on public.video_calls for select
    using (auth.uid() = caller_id or auth.uid() = callee_id);

drop policy if exists "video_calls_update_party" on public.video_calls;
create policy "video_calls_update_party"
    on public.video_calls for update
    using (auth.uid() = caller_id or auth.uid() = callee_id);

drop policy if exists "video_calls_insert_authenticated" on public.video_calls;
create policy "video_calls_insert_authenticated"
    on public.video_calls for insert
    to authenticated
    with check (auth.uid() = caller_id);

notify pgrst, 'reload schema';
