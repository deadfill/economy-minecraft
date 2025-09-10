-- === 1) Справочник скиллов ===
create table if not exists skills (
                                      id            text primary key,
                                      title         text not null,
                                      description   text not null,
                                      max_level     int  not null default 5,
                                      durations_ms  jsonb not null,             -- [ms L1..Lmax]
                                      enabled       boolean not null default true,
                                      version       int not null default 1,
                                      updated_at    timestamptz not null default now()
    );

-- === 2) Бонусы скиллов ===
-- выражаем бонусы через basis points (bps): 100 bps = 1%, 10000 bps = 100%
-- kind:   'recipe' | 'tag' | 'all'
-- op:     'INPUT_COST_MULTIPLIER' (сейчас поддерживаем это)
-- формула: multiplier = 1 - min(cap_bps, per_level_bps * level) / 10000
create table if not exists skill_bonuses (
                                             id              bigserial primary key,
                                             skill_id        text not null references skills(id) on delete cascade,
    kind            text not null,            -- 'recipe' | 'tag' | 'all'
    target          text,                      -- 'demo:diamond' или 'industry' (tag) или null
    op              text not null,            -- 'INPUT_COST_MULTIPLIER'
    per_level_bps   int  not null,            -- напр. 500 (5% за уровень)
    cap_bps         int  not null default 0,  -- напр. 2500 (cap 25%)
    enabled         boolean not null default true
    );

-- === 3) Инвентарь игрока (простая таблица материалов) ===
create table if not exists player_materials (
                                                owner_uuid  uuid not null,
                                                item_id     text not null,
                                                qty         bigint not null default 0,
                                                primary key (owner_uuid, item_id)
    );

-- === 4) Пример наполнения: скилл INDUSTRY даёт -5%/уровень, максимум -25% для рецептов с тегом 'industry'
insert into skills(id,title,description,max_level,durations_ms,enabled)
values ('industry','Industry','Снижает стоимость ресурсов при производстве',5,'[60000,300000,1800000,7200000,43200000]',true)
    on conflict (id) do nothing;

insert into skill_bonuses(skill_id,kind,target,op,per_level_bps,cap_bps,enabled)
values ('industry','tag','industry','INPUT_COST_MULTIPLIER',500,2500,true)
    on conflict do nothing;

-- (опционально) тестовые материалы для игрока:
-- insert into player_materials(owner_uuid,item_id,qty) values ('00000000-0000-0000-0000-000000000001','ore.iron',100);
