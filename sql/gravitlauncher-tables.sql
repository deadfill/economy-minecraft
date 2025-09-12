-- Дополнительные таблицы для GravitLauncher
-- Добавляем колонки для совместимости с GravitLauncher

-- Добавляем недостающие колонки в таблицу players для GravitLauncher
alter table players 
  add column if not exists access_token text,
  add column if not exists server_id text;

-- Создаем индексы для производительности
create index if not exists ix_players_access_token on players (access_token) where access_token is not null;
create index if not exists ix_players_server_id on players (server_id) where server_id is not null;

-- Таблица для хранения Hardware ID (HWID) - античит система
create table if not exists hwids (
  id          bigserial primary key,
  uuid        uuid not null references players(uuid) on delete cascade,
  hwid        text not null,
  is_banned   boolean not null default false,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

create unique index if not exists ux_hwids_uuid_hwid on hwids (uuid, hwid);
create index if not exists ix_hwids_hwid on hwids (hwid);

-- Логи HWID для отслеживания
create table if not exists hwid_log (
  id          bigserial primary key,
  uuid        uuid not null references players(uuid) on delete cascade,
  hwid        text not null,
  ip_address  inet,
  created_at  timestamptz not null default now()
);

create index if not exists ix_hwid_log_uuid_created on hwid_log (uuid, created_at);
create index if not exists ix_hwid_log_hwid on hwid_log (hwid);

-- Система разрешений для GravitLauncher
create table if not exists user_permissions (
  uuid        uuid not null references players(uuid) on delete cascade,
  name        varchar(100) not null,
  created_at  timestamptz not null default now()
);

create unique index if not exists uk_user_permissions_uuid_name on user_permissions (uuid, name);

-- Токены серверов для привязки серверов Minecraft к лаунчеру
create table if not exists server_tokens (
  id          bigserial primary key,
  token       text not null unique,
  server_name text not null,
  profile     text not null,
  created_at  timestamptz not null default now(),
  last_used   timestamptz
);

create index if not exists ix_server_tokens_token on server_tokens (token);

-- Сессии пользователей
create table if not exists user_sessions (
  id              bigserial primary key,
  uuid            uuid not null references players(uuid) on delete cascade,
  access_token    text not null unique,
  refresh_token   text,
  expires_at      timestamptz not null,
  created_at      timestamptz not null default now(),
  last_activity   timestamptz not null default now(),
  ip_address      inet,
  user_agent      text
);

create index if not exists ix_user_sessions_uuid on user_sessions (uuid);
create index if not exists ix_user_sessions_access_token on user_sessions (access_token);
create index if not exists ix_user_sessions_expires on user_sessions (expires_at);

-- Настройки лаунчера для каждого пользователя
create table if not exists launcher_settings (
  uuid        uuid primary key references players(uuid) on delete cascade,
  settings    jsonb not null default '{}',
  updated_at  timestamptz not null default now()
);

-- Статистика использования лаунчера
create table if not exists launcher_stats (
  id          bigserial primary key,
  uuid        uuid not null references players(uuid) on delete cascade,
  action      text not null,
  profile     text,
  data        jsonb,
  created_at  timestamptz not null default now()
);

create index if not exists ix_launcher_stats_uuid_created on launcher_stats (uuid, created_at);
create index if not exists ix_launcher_stats_action on launcher_stats (action);

-- Добавляем базового администратора с полными правами
insert into user_permissions (uuid, name)
select uuid, 'launchserver.*'
from players 
where username = 'dev'
on conflict (uuid, name) do nothing;

-- Добавляем права на все профили для администратора
insert into user_permissions (uuid, name)
select uuid, 'launchserver.profile.*'
from players 
where username = 'dev'
on conflict (uuid, name) do nothing;

-- Комментарий о интеграции
comment on table hwids is 'Hardware ID для античит системы GravitLauncher';
comment on table user_permissions is 'Система разрешений GravitLauncher';
comment on table server_tokens is 'Токены для привязки серверов Minecraft';
comment on table user_sessions is 'Сессии пользователей лаунчера';
comment on table launcher_settings is 'Настройки лаунчера для пользователей';
comment on table launcher_stats is 'Статистика использования лаунчера';
