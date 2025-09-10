-- Текущие уровни навыков
create table if not exists skill_levels (
                                            owner_uuid   uuid        not null,
                                            skill_id     text        not null,
                                            level        int         not null default 0,
                                            updated_at   timestamptz not null default now(),
    primary key (owner_uuid, skill_id)
    );

-- Активная прокачка (1 запись на игрока)
create table if not exists skill_training (
                                              owner_uuid    uuid        primary key,
                                              skill_id      text        not null,
                                              target_level  int         not null,
                                              start_ms      bigint      not null,
                                              end_ms        bigint      not null,
                                              status        text        not null check (status in ('IN_PROGRESS','DONE')) default 'IN_PROGRESS',
    idempotency_key text unique
    );

-- (опционально) очередь навыков
create table if not exists skill_queue (
                                           owner_uuid   uuid not null,
                                           pos          int  not null,
                                           skill_id     text not null,
                                           target_level int  not null,
                                           primary key (owner_uuid, pos)
    );
