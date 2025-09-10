package com.example.economy.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

/**
 * Менеджер конфигураций для автоматической загрузки скиллов и рецептов из JSON
 */
@ApplicationScoped
public class ConfigurationManager {
    private static final Logger LOG = Logger.getLogger(ConfigurationManager.class);
    
    @Inject Database db;
    @Inject ExtendedRecipeService recipeService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // DTO классы для JSON конфигураций
    public static class SkillConfig {
        public String id;
        public String title;
        public String description;
        public String icon;
        public int maxLevel;
        public boolean enabled = true;
        public String category;
        public List<DurationLevel> durations;
        public List<BonusConfig> bonuses;
        
        public static class DurationLevel {
            public int level;
            public long durationMs;
            public String description;
        }
        
        public static class BonusConfig {
            public String kind; // 'recipe' | 'tag' | 'all'
            public String target;
            public String operation; // 'INPUT_COST_MULTIPLIER' | 'DURATION_MULTIPLIER' | 'UNLOCK_RECIPE' | 'EXTRA_OUTPUT_CHANCE'
            public int perLevelBps;
            public int capBps;
            public String description;
            public boolean enabled = true;
        }
    }
    
    public static class RecipeConfig {
        public String id;
        public String name;
        public String description;
        public String tag;
        public long baseDurationMs;
        public boolean enabled = true;
        public String category;
        public List<ItemStack> inputs;
        public List<ItemStack> outputs;
        public Requirements requirements;
        
        public static class ItemStack {
            public String itemId;
            public int quantity;
            public double chance = 1.0;
            public String description;
            public int sortOrder = 0;
        }
        
        public static class Requirements {
            public java.util.Map<String, Integer> minSkillLevel;
            public List<String> buildings;
            public List<String> tools;
            public List<String> items;
        }
    }
    
    @PostConstruct
    void init() {
        LOG.info("🔄 Loading configurations from JSON files...");
        try {
            loadSkillsFromResources();
            loadRecipesFromResources();
            LOG.info("✅ Configuration loading completed successfully");
        } catch (Exception e) {
            LOG.error("❌ Failed to load configurations", e);
        }
    }
    
    /**
     * Загрузить скиллы из resources/config/skills/
     */
    private void loadSkillsFromResources() {
        try {
            // Пытаемся загрузить из classpath
            InputStream skillsStream = getClass().getResourceAsStream("/config/skills");
            if (skillsStream == null) {
                LOG.warn("No skills configuration directory found in resources");
                return;
            }
            
            // Загружаем встроенные примеры скиллов
            loadBuiltinSkills();
            
        } catch (Exception e) {
            LOG.error("Failed to load skills from resources", e);
        }
    }
    
    /**
     * Загрузить встроенные примеры скиллов
     */
    private void loadBuiltinSkills() throws Exception {
        // Пример скилла Industry
        SkillConfig industrySkill = new SkillConfig();
        industrySkill.id = "industry";
        industrySkill.title = "Industry";
        industrySkill.description = "Промышленное производство - снижает стоимость ресурсов и время производства";
        industrySkill.icon = "🏭";
        industrySkill.maxLevel = 10;
        industrySkill.enabled = true;
        industrySkill.category = "production";
        
        // Длительности тренировок
        industrySkill.durations = List.of(
            createDuration(1, 60000, "1 минута"),
            createDuration(2, 300000, "5 минут"),
            createDuration(3, 1800000, "30 минут"),
            createDuration(4, 7200000, "2 часа"),
            createDuration(5, 43200000, "12 часов"),
            createDuration(6, 86400000, "24 часа"),
            createDuration(7, 172800000, "48 часов"),
            createDuration(8, 345600000, "4 дня"),
            createDuration(9, 604800000, "7 дней"),
            createDuration(10, 1209600000, "14 дней")
        );
        
        // Бонусы
        industrySkill.bonuses = List.of(
            createBonus("tag", "industry", "INPUT_COST_MULTIPLIER", 500, 5000, 
                "Снижение стоимости материалов на 5% за уровень (макс. 50%)"),
            createBonus("tag", "industry", "DURATION_MULTIPLIER", 300, 3000,
                "Уменьшение времени производства на 3% за уровень (макс. 30%)"),
            createBonus("tag", "industry", "EXTRA_OUTPUT_CHANCE", 200, 1000,
                "Шанс дополнительного выхода на 2% за уровень (макс. 10%)")
        );
        
        syncSkillToDatabase(industrySkill);
        
        // Пример скилла Mining
        SkillConfig miningSkill = new SkillConfig();
        miningSkill.id = "mining";
        miningSkill.title = "Mining";
        miningSkill.description = "Добыча ресурсов - увеличивает эффективность добычи руды и камня";
        miningSkill.icon = "⛏️";
        miningSkill.maxLevel = 10;
        miningSkill.enabled = true;
        miningSkill.category = "gathering";
        
        miningSkill.durations = industrySkill.durations; // Те же длительности
        
        miningSkill.bonuses = List.of(
            createBonus("tag", "mining", "EXTRA_OUTPUT_CHANCE", 400, 2000,
                "Шанс дополнительной добычи на 4% за уровень (макс. 20%)"),
            createBonus("tag", "mining", "DURATION_MULTIPLIER", 250, 2500,
                "Уменьшение времени добычи на 2.5% за уровень (макс. 25%)")
        );
        
        syncSkillToDatabase(miningSkill);
        
        LOG.info("✅ Loaded builtin skills: industry, mining");
    }
    
    /**
     * Загрузить рецепты из resources/config/recipes/
     */
    private void loadRecipesFromResources() {
        try {
            loadBuiltinRecipes();
        } catch (Exception e) {
            LOG.error("Failed to load recipes from resources", e);
        }
    }
    
    /**
     * Загрузить встроенные примеры рецептов
     */
    private void loadBuiltinRecipes() throws Exception {
        // Рецепт железного слитка
        RecipeConfig ironIngot = new RecipeConfig();
        ironIngot.id = "demo:iron_ingot";
        ironIngot.name = "Iron Ingot";
        ironIngot.description = "Выплавка железного слитка из руды";
        ironIngot.tag = "industry";
        ironIngot.baseDurationMs = 30000; // 30 секунд
        ironIngot.enabled = true;
        ironIngot.category = "smelting";
        
        ironIngot.inputs = List.of(
            createInput("ore.iron", 2, "Железная руда", 1),
            createInput("fuel.coal", 1, "Уголь", 2)
        );
        
        ironIngot.outputs = List.of(
            createOutput("ingot.iron", 1, 1.0, "Железный слиток", 1),
            createOutput("slag.iron", 1, 0.1, "Железный шлак (побочный продукт)", 2)
        );
        
        ironIngot.requirements = new RecipeConfig.Requirements();
        ironIngot.requirements.minSkillLevel = java.util.Map.of("industry", 1);
        ironIngot.requirements.buildings = List.of("furnace");
        
        syncRecipeToDatabase(ironIngot);
        
        // Рецепт стального слитка
        RecipeConfig steelIngot = new RecipeConfig();
        steelIngot.id = "demo:steel_ingot";
        steelIngot.name = "Steel Ingot";
        steelIngot.description = "Выплавка стального слитка из железа и угля";
        steelIngot.tag = "industry";
        steelIngot.baseDurationMs = 60000; // 1 минута
        steelIngot.enabled = true;
        steelIngot.category = "smelting";
        
        steelIngot.inputs = List.of(
            createInput("ingot.iron", 2, "Железный слиток", 1),
            createInput("fuel.coal", 2, "Уголь", 2)
        );
        
        steelIngot.outputs = List.of(
            createOutput("ingot.steel", 1, 1.0, "Стальной слиток", 1)
        );
        
        steelIngot.requirements = new RecipeConfig.Requirements();
        steelIngot.requirements.minSkillLevel = java.util.Map.of("industry", 3);
        steelIngot.requirements.buildings = List.of("advanced_furnace");
        
        syncRecipeToDatabase(steelIngot);
        
        // Рецепт добычи железной руды
        RecipeConfig ironOre = new RecipeConfig();
        ironOre.id = "mining:iron_ore";
        ironOre.name = "Iron Ore Mining";
        ironOre.description = "Добыча железной руды из месторождения";
        ironOre.tag = "mining";
        ironOre.baseDurationMs = 15000; // 15 секунд
        ironOre.enabled = true;
        ironOre.category = "mining";
        
        ironOre.inputs = List.of(
            createInput("tool.pickaxe", 1, "Кирка (изнашивается)", 1)
        );
        
        ironOre.outputs = List.of(
            createOutput("ore.iron", 1, 1.0, "Железная руда", 1),
            createOutput("stone.cobble", 1, 0.3, "Булыжник", 2)
        );
        
        ironOre.requirements = new RecipeConfig.Requirements();
        ironOre.requirements.minSkillLevel = java.util.Map.of("mining", 1);
        ironOre.requirements.tools = List.of("pickaxe");
        
        syncRecipeToDatabase(ironOre);
        
        LOG.info("✅ Loaded builtin recipes: iron_ingot, steel_ingot, iron_ore");
    }
    
    /**
     * Синхронизировать скилл с базой данных
     */
    private void syncSkillToDatabase(SkillConfig config) throws Exception {
        // Преобразуем durations в JSON массив
        long[] durationsMs = config.durations.stream()
            .mapToLong(d -> d.durationMs)
            .toArray();
        
        String durationsJson = objectMapper.writeValueAsString(durationsMs);
        
        // Вставляем или обновляем скилл
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement("""
                INSERT INTO skills (id, title, description, max_level, durations_ms, enabled, version)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, 1)
                ON CONFLICT (id) DO UPDATE SET
                    title = EXCLUDED.title,
                    description = EXCLUDED.description,
                    max_level = EXCLUDED.max_level,
                    durations_ms = EXCLUDED.durations_ms,
                    enabled = EXCLUDED.enabled,
                    version = skills.version + 1,
                    updated_at = now()
                """)) {
            
            ps.setString(1, config.id);
            ps.setString(2, config.title);
            ps.setString(3, config.description);
            ps.setInt(4, config.maxLevel);
            ps.setString(5, durationsJson);
            ps.setBoolean(6, config.enabled);
            ps.executeUpdate();
        }
        
        // Синхронизируем бонусы
        if (config.bonuses != null) {
            // Удаляем старые бонусы
            try (Connection c = db.get();
                 PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM skill_bonuses WHERE skill_id = ?")) {
                ps.setString(1, config.id);
                ps.executeUpdate();
            }
            
            // Добавляем новые бонусы
            for (SkillConfig.BonusConfig bonus : config.bonuses) {
                try (Connection c = db.get();
                     PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO skill_bonuses (skill_id, kind, target, op, per_level_bps, cap_bps, enabled, description)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    ps.setString(1, config.id);
                    ps.setString(2, bonus.kind);
                    ps.setString(3, bonus.target);
                    ps.setString(4, bonus.operation);
                    ps.setInt(5, bonus.perLevelBps);
                    ps.setInt(6, bonus.capBps);
                    ps.setBoolean(7, bonus.enabled);
                    ps.setString(8, bonus.description);
                    ps.executeUpdate();
                }
            }
        }
        
        LOG.infof("✅ Synced skill to database: %s", config.id);
    }
    
    /**
     * Синхронизировать рецепт с базой данных
     */
    private void syncRecipeToDatabase(RecipeConfig config) throws Exception {
        ExtendedRecipeService.Recipe recipe = new ExtendedRecipeService.Recipe();
        recipe.id = config.id;
        recipe.name = config.name;
        recipe.description = config.description;
        recipe.tag = config.tag;
        recipe.baseDurationMs = config.baseDurationMs;
        recipe.enabled = config.enabled;
        recipe.category = config.category;
        
        // Конвертируем входы
        if (config.inputs != null) {
            for (RecipeConfig.ItemStack input : config.inputs) {
                ExtendedRecipeService.RecipeInput recipeInput = new ExtendedRecipeService.RecipeInput();
                recipeInput.itemId = input.itemId;
                recipeInput.quantity = input.quantity;
                recipeInput.description = input.description;
                recipeInput.sortOrder = input.sortOrder;
                recipe.inputs.add(recipeInput);
            }
        }
        
        // Конвертируем выходы
        if (config.outputs != null) {
            for (RecipeConfig.ItemStack output : config.outputs) {
                ExtendedRecipeService.RecipeOutput recipeOutput = new ExtendedRecipeService.RecipeOutput();
                recipeOutput.itemId = output.itemId;
                recipeOutput.quantity = output.quantity;
                recipeOutput.chance = output.chance;
                recipeOutput.description = output.description;
                recipeOutput.sortOrder = output.sortOrder;
                recipe.outputs.add(recipeOutput);
            }
        }
        
        // Конвертируем требования
        if (config.requirements != null) {
            if (config.requirements.minSkillLevel != null) {
                for (var entry : config.requirements.minSkillLevel.entrySet()) {
                    ExtendedRecipeService.RecipeRequirement req = new ExtendedRecipeService.RecipeRequirement();
                    req.type = "skill_level";
                    req.target = entry.getKey();
                    req.value = entry.getValue().toString();
                    req.description = "Требуется " + entry.getKey() + " уровень " + entry.getValue();
                    recipe.requirements.add(req);
                }
            }
            
            if (config.requirements.buildings != null) {
                for (String building : config.requirements.buildings) {
                    ExtendedRecipeService.RecipeRequirement req = new ExtendedRecipeService.RecipeRequirement();
                    req.type = "building";
                    req.target = building;
                    req.value = "1";
                    req.description = "Требуется здание: " + building;
                    recipe.requirements.add(req);
                }
            }
            
            if (config.requirements.tools != null) {
                for (String tool : config.requirements.tools) {
                    ExtendedRecipeService.RecipeRequirement req = new ExtendedRecipeService.RecipeRequirement();
                    req.type = "tool";
                    req.target = tool;
                    req.value = "1";
                    req.description = "Требуется инструмент: " + tool;
                    recipe.requirements.add(req);
                }
            }
        }
        
        // Пытаемся создать рецепт, если не существует
        try {
            recipeService.createRecipe(recipe);
            LOG.infof("✅ Created recipe: %s", config.id);
        } catch (Exception e) {
            // Если рецепт уже существует, обновляем его
            try {
                recipeService.updateRecipe(recipe);
                LOG.infof("✅ Updated recipe: %s", config.id);
            } catch (Exception updateError) {
                LOG.errorf(updateError, "Failed to sync recipe: %s", config.id);
            }
        }
    }
    
    /**
     * Перезагрузить все конфигурации
     */
    public void reloadConfigurations() {
        LOG.info("🔄 Reloading all configurations...");
        init();
    }
    
    // Вспомогательные методы для создания объектов
    
    private SkillConfig.DurationLevel createDuration(int level, long durationMs, String description) {
        SkillConfig.DurationLevel duration = new SkillConfig.DurationLevel();
        duration.level = level;
        duration.durationMs = durationMs;
        duration.description = description;
        return duration;
    }
    
    private SkillConfig.BonusConfig createBonus(String kind, String target, String operation, 
                                               int perLevelBps, int capBps, String description) {
        SkillConfig.BonusConfig bonus = new SkillConfig.BonusConfig();
        bonus.kind = kind;
        bonus.target = target;
        bonus.operation = operation;
        bonus.perLevelBps = perLevelBps;
        bonus.capBps = capBps;
        bonus.description = description;
        bonus.enabled = true;
        return bonus;
    }
    
    private RecipeConfig.ItemStack createInput(String itemId, int quantity, String description, int sortOrder) {
        RecipeConfig.ItemStack item = new RecipeConfig.ItemStack();
        item.itemId = itemId;
        item.quantity = quantity;
        item.description = description;
        item.sortOrder = sortOrder;
        return item;
    }
    
    private RecipeConfig.ItemStack createOutput(String itemId, int quantity, double chance, String description, int sortOrder) {
        RecipeConfig.ItemStack item = new RecipeConfig.ItemStack();
        item.itemId = itemId;
        item.quantity = quantity;
        item.chance = chance;
        item.description = description;
        item.sortOrder = sortOrder;
        return item;
    }
}
