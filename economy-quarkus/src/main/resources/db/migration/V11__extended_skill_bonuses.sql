-- Расширяем систему бонусов скиллов для поддержки новых типов

-- Добавляем новые типы операций для skill_bonuses
-- Старые данные остаются совместимыми
ALTER TABLE skill_bonuses ADD COLUMN IF NOT EXISTS description text;

-- Обновляем комментарии для ясности
COMMENT ON COLUMN skill_bonuses.op IS 'Тип бонуса: INPUT_COST_MULTIPLIER | DURATION_MULTIPLIER | UNLOCK_RECIPE | EXTRA_OUTPUT_CHANCE';
COMMENT ON COLUMN skill_bonuses.kind IS 'Область применения: recipe (конкретный рецепт) | tag (группа рецептов) | all (все рецепты)';
COMMENT ON COLUMN skill_bonuses.target IS 'Цель бонуса: ID рецепта для kind=recipe, тег для kind=tag, null для kind=all';
COMMENT ON COLUMN skill_bonuses.per_level_bps IS 'Бонус за уровень в basis points (100 = 1%)';
COMMENT ON COLUMN skill_bonuses.cap_bps IS 'Максимальный бонус в basis points (0 = без ограничений)';

-- Создаем таблицу для рецептов (если еще не существует)
CREATE TABLE IF NOT EXISTS recipes (
    id              text PRIMARY KEY,
    name            text NOT NULL,
    description     text,
    tag             text NOT NULL,                    -- для группировки и бонусов
    base_duration_ms bigint NOT NULL,
    enabled         boolean NOT NULL DEFAULT true,
    category        text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

-- Создаем таблицу для ингредиентов рецептов
CREATE TABLE IF NOT EXISTS recipe_inputs (
    id          bigserial PRIMARY KEY,
    recipe_id   text NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
    item_id     text NOT NULL,
    quantity    int NOT NULL,
    description text,
    sort_order  int NOT NULL DEFAULT 0
);

-- Создаем таблицу для результатов рецептов
CREATE TABLE IF NOT EXISTS recipe_outputs (
    id          bigserial PRIMARY KEY,
    recipe_id   text NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
    item_id     text NOT NULL,
    quantity    int NOT NULL,
    chance      decimal(5,4) NOT NULL DEFAULT 1.0,  -- 0.0 - 1.0
    description text,
    sort_order  int NOT NULL DEFAULT 0
);

-- Создаем таблицу для требований рецептов
CREATE TABLE IF NOT EXISTS recipe_requirements (
    id          bigserial PRIMARY KEY,
    recipe_id   text NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
    type        text NOT NULL,  -- 'skill_level' | 'building' | 'tool' | 'item'
    target      text NOT NULL,  -- skill_id, building_id, tool_id, item_id
    value       text NOT NULL,  -- required level, quantity, etc
    description text
);

-- Индексы для производительности
CREATE INDEX IF NOT EXISTS idx_skill_bonuses_skill_id ON skill_bonuses(skill_id);
CREATE INDEX IF NOT EXISTS idx_skill_bonuses_kind_target ON skill_bonuses(kind, target);
CREATE INDEX IF NOT EXISTS idx_recipes_tag ON recipes(tag);
CREATE INDEX IF NOT EXISTS idx_recipes_enabled ON recipes(enabled);
CREATE INDEX IF NOT EXISTS idx_recipe_inputs_recipe_id ON recipe_inputs(recipe_id);
CREATE INDEX IF NOT EXISTS idx_recipe_outputs_recipe_id ON recipe_outputs(recipe_id);
CREATE INDEX IF NOT EXISTS idx_recipe_requirements_recipe_id ON recipe_requirements(recipe_id);

-- Добавляем примеры новых типов бонусов для существующего скилла industry
INSERT INTO skill_bonuses (skill_id, kind, target, op, per_level_bps, cap_bps, enabled, description)
VALUES 
    ('industry', 'tag', 'industry', 'DURATION_MULTIPLIER', 300, 3000, true, 'Уменьшение времени производства на 3% за уровень (макс. 30%)'),
    ('industry', 'tag', 'industry', 'EXTRA_OUTPUT_CHANCE', 200, 1000, true, 'Шанс дополнительного выхода на 2% за уровень (макс. 10%)')
ON CONFLICT DO NOTHING;

-- Добавляем пример рецепта
INSERT INTO recipes (id, name, description, tag, base_duration_ms, enabled, category)
VALUES ('demo:iron_ingot', 'Iron Ingot', 'Выплавка железного слитка из руды', 'industry', 30000, true, 'smelting')
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    tag = EXCLUDED.tag,
    base_duration_ms = EXCLUDED.base_duration_ms,
    enabled = EXCLUDED.enabled,
    category = EXCLUDED.category,
    updated_at = now();

-- Добавляем ингредиенты для рецепта
INSERT INTO recipe_inputs (recipe_id, item_id, quantity, description, sort_order)
VALUES 
    ('demo:iron_ingot', 'ore.iron', 2, 'Железная руда', 1),
    ('demo:iron_ingot', 'fuel.coal', 1, 'Уголь', 2)
ON CONFLICT DO NOTHING;

-- Добавляем результаты рецепта
INSERT INTO recipe_outputs (recipe_id, item_id, quantity, chance, description, sort_order)
VALUES 
    ('demo:iron_ingot', 'ingot.iron', 1, 1.0, 'Железный слиток', 1),
    ('demo:iron_ingot', 'slag.iron', 1, 0.1, 'Железный шлак (побочный продукт)', 2)
ON CONFLICT DO NOTHING;

-- Добавляем требования рецепта
INSERT INTO recipe_requirements (recipe_id, type, target, value, description)
VALUES 
    ('demo:iron_ingot', 'skill_level', 'industry', '1', 'Требуется уровень Industry 1+'),
    ('demo:iron_ingot', 'building', 'furnace', '1', 'Требуется печь')
ON CONFLICT DO NOTHING;
