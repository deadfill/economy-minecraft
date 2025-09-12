-- ===== enums =====
do $$
begin
  if not exists (select 1 from pg_type where typname = 'job_status') then
    create type job_status as enum ('IN_PROGRESS','DONE','CANCELLED');
  end if;
  if not exists (select 1 from pg_type where typname = 'order_side') then
    create type order_side as enum ('BUY','SELL');
  end if;
  if not exists (select 1 from pg_type where typname = 'order_status') then
    create type order_status as enum ('OPEN','PARTIAL','FILLED','CANCELLED');
  end if;
end$$;

-- ===== players & auth =====
create table if not exists players (
  uuid        uuid primary key,
  username    text not null,
  first_seen  timestamptz not null default now(),
  last_seen   timestamptz not null default now()
);
create index if not exists ix_players_username on players (lower(username));

-- локальная регистрация/логин (bcrypt)
create table if not exists auth_users (
  uuid        uuid primary key references players(uuid) on delete cascade,
  pass_hash   text not null,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

-- ===== wallets & tx =====
create table if not exists wallets (
  owner_uuid uuid primary key references players(uuid) on delete cascade,
  balance    bigint not null default 0,
  reserved   bigint not null default 0,
  updated_at timestamptz not null default now(),
  check (balance >= 0),
  check (reserved >= 0)
);

create table if not exists wallet_tx (
  id          bigserial primary key,
  owner_uuid  uuid not null references players(uuid) on delete cascade,
  delta       bigint not null,
  reason      text not null,
  created_at  timestamptz not null default now()
);
create index if not exists ix_wallet_tx_owner on wallet_tx (owner_uuid, created_at);

-- ===== production =====
create table if not exists production_jobs (
  id               uuid primary key,
  owner_uuid       uuid not null references players(uuid) on delete cascade,
  recipe_id        text not null,
  start_ms         bigint not null,
  end_ms           bigint not null,
  status           job_status not null default 'IN_PROGRESS',
  idempotency_key  text,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now(),
  check (end_ms >= start_ms)
);
create unique index if not exists ux_jobs_idem
  on production_jobs(idempotency_key)
  where idempotency_key is not null;

create index if not exists ix_jobs_owner_status_end
  on production_jobs(owner_uuid, status, end_ms);

-- ===== rewards =====
create table if not exists player_rewards (
  owner_uuid uuid primary key references players(uuid) on delete cascade,
  count      int not null default 0,
  updated_at timestamptz not null default now(),
  check (count >= 0)
);

-- ===== market =====
create table if not exists market_orders (
  id          bigserial primary key,
  owner_uuid  uuid not null references players(uuid) on delete cascade,
  item_id     text not null,
  side        order_side not null,
  price       bigint not null,
  qty         bigint not null,
  filled      bigint not null default 0,
  status      order_status not null default 'OPEN',
  created_at  timestamptz not null default now(),
  check (price > 0),
  check (qty > 0),
  check (filled >= 0),
  check (filled <= qty)
);
create index if not exists ix_orders_lookup
  on market_orders (item_id, side, price, status);

-- seed dev player
insert into players(uuid, username) values
  ('00000000-0000-0000-0000-000000000001', 'dev')
on conflict (uuid) do update set username = excluded.username;

insert into wallets(owner_uuid, balance) values
  ('00000000-0000-0000-0000-000000000001', 100000)
on conflict (owner_uuid) do nothing;

-- ===== GravitLauncher Integration =====
-- Include GravitLauncher tables
\i /docker-entrypoint-initdb.d/gravit-launcher-init.sql
