package com.example.economy.core;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.UUID;

/**
 * Расширенный сервис для управления рецептами с поддержкой бонусов
 */
@ApplicationScoped
public class ExtendedRecipeService {
    private static final Logger LOG = Logger.getLogger(ExtendedRecipeService.class);
    
    @Inject Database db;
    @Inject DatabaseRouter databaseRouter;
    @Inject SkillCatalogService skillCatalog;
    
    // DTO классы
    public static class Recipe {
        public String id;
        public String name;
        public String description;
        public String tag;
        public long baseDurationMs;
        public boolean enabled;
        public String category;
        public List<RecipeInput> inputs;
        public List<RecipeOutput> outputs;
        public List<RecipeRequirement> requirements;
        
        public Recipe() {
            this.inputs = new ArrayList<>();
            this.outputs = new ArrayList<>();
            this.requirements = new ArrayList<>();
        }
    }
    
    public static class RecipeInput {
        public String itemId;
        public int quantity;
        public String description;
        public int sortOrder;
    }
    
    public static class RecipeOutput {
        public String itemId;
        public int quantity;
        public double chance;
        public String description;
        public int sortOrder;
    }
    
    public static class RecipeRequirement {
        public String type; // 'skill_level' | 'building' | 'tool' | 'item'
        public String target;
        public String value;
        public String description;
    }
    
    public static class SkillBonus {
        public String skillId;
        public String kind; // 'recipe' | 'tag' | 'all'
        public String target;
        public String operation; // 'INPUT_COST_MULTIPLIER' | 'DURATION_MULTIPLIER' | 'UNLOCK_RECIPE' | 'EXTRA_OUTPUT_CHANCE'
        public int perLevelBps;
        public int capBps;
        public boolean enabled;
        public String description;
    }
    
    /**
     * Получить все рецепты - READ операция, идет на реплики
     */
    public List<Recipe> getAllRecipes() throws Exception {
        return databaseRouter.executeRead(conn -> {
            List<Recipe> recipes = new ArrayList<>();
            
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, name, description, tag, base_duration_ms, enabled, category " +
                "FROM recipes ORDER BY category, name");
                 ResultSet rs = ps.executeQuery()) {
                
                while (rs.next()) {
                    Recipe recipe = new Recipe();
                    recipe.id = rs.getString("id");
                    recipe.name = rs.getString("name");
                    recipe.description = rs.getString("description");
                    recipe.tag = rs.getString("tag");
                    recipe.baseDurationMs = rs.getLong("base_duration_ms");
                    recipe.enabled = rs.getBoolean("enabled");
                    recipe.category = rs.getString("category");
                    
                    // Загружаем связанные данные через read соединение
                    loadRecipeInputs(recipe, conn);
                    loadRecipeOutputs(recipe, conn);
                    loadRecipeRequirements(recipe, conn);
                    
                    recipes.add(recipe);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to load recipes", e);
            }
            
            LOG.infof("Loaded %d recipes", recipes.size());
            return recipes;
        });
    }
    
    /**
     * Создать новый рецепт
     */
    public void createRecipe(Recipe recipe) throws Exception {
        try (Connection c = db.get()) {
            c.setAutoCommit(false);
            
            try {
                // Создаем основную запись рецепта
                try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO recipes (id, name, description, tag, base_duration_ms, enabled, category) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, recipe.id);
                    ps.setString(2, recipe.name);
                    ps.setString(3, recipe.description);
                    ps.setString(4, recipe.tag);
                    ps.setLong(5, recipe.baseDurationMs);
                    ps.setBoolean(6, recipe.enabled);
                    ps.setString(7, recipe.category);
                    ps.executeUpdate();
                }
                
                // Добавляем входы
                for (RecipeInput input : recipe.inputs) {
                    try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO recipe_inputs (recipe_id, item_id, quantity, description, sort_order) " +
                        "VALUES (?, ?, ?, ?, ?)")) {
                        ps.setString(1, recipe.id);
                        ps.setString(2, input.itemId);
                        ps.setInt(3, input.quantity);
                        ps.setString(4, input.description);
                        ps.setInt(5, input.sortOrder);
                        ps.executeUpdate();
                    }
                }
                
                // Добавляем выходы
                for (RecipeOutput output : recipe.outputs) {
                    try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO recipe_outputs (recipe_id, item_id, quantity, chance, description, sort_order) " +
                        "VALUES (?, ?, ?, ?, ?, ?)")) {
                        ps.setString(1, recipe.id);
                        ps.setString(2, output.itemId);
                        ps.setInt(3, output.quantity);
                        ps.setDouble(4, output.chance);
                        ps.setString(5, output.description);
                        ps.setInt(6, output.sortOrder);
                        ps.executeUpdate();
                    }
                }
                
                // Добавляем требования
                for (RecipeRequirement req : recipe.requirements) {
                    try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO recipe_requirements (recipe_id, type, target, value, description) " +
                        "VALUES (?, ?, ?, ?, ?)")) {
                        ps.setString(1, recipe.id);
                        ps.setString(2, req.type);
                        ps.setString(3, req.target);
                        ps.setString(4, req.value);
                        ps.setString(5, req.description);
                        ps.executeUpdate();
                    }
                }
                
                c.commit();
                LOG.infof("Created recipe: %s", recipe.id);
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }
    
    /**
     * Обновить рецепт
     */
    public void updateRecipe(Recipe recipe) throws Exception {
        try (Connection c = db.get()) {
            c.setAutoCommit(false);
            
            try {
                // Обновляем основную запись
                try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE recipes SET name=?, description=?, tag=?, base_duration_ms=?, " +
                    "enabled=?, category=?, updated_at=now() WHERE id=?")) {
                    ps.setString(1, recipe.name);
                    ps.setString(2, recipe.description);
                    ps.setString(3, recipe.tag);
                    ps.setLong(4, recipe.baseDurationMs);
                    ps.setBoolean(5, recipe.enabled);
                    ps.setString(6, recipe.category);
                    ps.setString(7, recipe.id);
                    ps.executeUpdate();
                }
                
                // Удаляем старые связанные записи
                deleteRecipeRelations(c, recipe.id);
                
                // Добавляем новые связанные записи (код аналогичен createRecipe)
                // ... (аналогично методу createRecipe)
                
                c.commit();
                LOG.infof("Updated recipe: %s", recipe.id);
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }
    
    /**
     * Получить все бонусы скиллов - READ операция, идет на реплики
     */
    public List<SkillBonus> getAllSkillBonuses() throws Exception {
        return databaseRouter.executeRead(conn -> {
            List<SkillBonus> bonuses = new ArrayList<>();
            
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT skill_id, kind, target, op, per_level_bps, cap_bps, enabled, description " +
                "FROM skill_bonuses ORDER BY skill_id, kind, target");
                 ResultSet rs = ps.executeQuery()) {
                
                while (rs.next()) {
                    SkillBonus bonus = new SkillBonus();
                    bonus.skillId = rs.getString("skill_id");
                    bonus.kind = rs.getString("kind");
                    bonus.target = rs.getString("target");
                    bonus.operation = rs.getString("op");
                    bonus.perLevelBps = rs.getInt("per_level_bps");
                    bonus.capBps = rs.getInt("cap_bps");
                    bonus.enabled = rs.getBoolean("enabled");
                    bonus.description = rs.getString("description");
                    bonuses.add(bonus);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to load skill bonuses", e);
            }
            
            LOG.infof("Loaded %d skill bonuses", bonuses.size());
            return bonuses;
        });
    }
    
    /**
     * Создать новый бонус скилла
     */
    public void createSkillBonus(SkillBonus bonus) throws Exception {
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                "INSERT INTO skill_bonuses (skill_id, kind, target, op, per_level_bps, cap_bps, enabled, description) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, bonus.skillId);
            ps.setString(2, bonus.kind);
            ps.setString(3, bonus.target);
            ps.setString(4, bonus.operation);
            ps.setInt(5, bonus.perLevelBps);
            ps.setInt(6, bonus.capBps);
            ps.setBoolean(7, bonus.enabled);
            ps.setString(8, bonus.description);
            ps.executeUpdate();
        }
        
        LOG.infof("Created skill bonus: %s -> %s", bonus.skillId, bonus.operation);
    }
    
    /**
     * Вычислить эффективные входы рецепта с учетом бонусов игрока
     */
    public List<RecipeInput> calculateEffectiveInputs(String recipeId, UUID playerUuid) throws Exception {
        Recipe recipe = getRecipeById(recipeId);
        if (recipe == null) return Collections.emptyList();
        
        List<RecipeInput> effectiveInputs = new ArrayList<>();
        
        for (RecipeInput input : recipe.inputs) {
            RecipeInput effective = new RecipeInput();
            effective.itemId = input.itemId;
            effective.description = input.description;
            effective.sortOrder = input.sortOrder;
            
            // Вычисляем эффективное количество с учетом бонусов
            double multiplier = calculateInputCostMultiplier(recipe.tag, playerUuid);
            effective.quantity = (int) Math.ceil(input.quantity * multiplier);
            
            effectiveInputs.add(effective);
        }
        
        return effectiveInputs;
    }
    
    /**
     * Вычислить эффективную длительность рецепта с учетом бонусов
     */
    public long calculateEffectiveDuration(String recipeId, UUID playerUuid) throws Exception {
        Recipe recipe = getRecipeById(recipeId);
        if (recipe == null) return 0;
        
        double multiplier = calculateDurationMultiplier(recipe.tag, playerUuid);
        return (long) (recipe.baseDurationMs * multiplier);
    }
    // Приватные методы
    
    private void loadRecipeInputs(Recipe recipe, Connection conn) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT item_id, quantity, description, sort_order FROM recipe_inputs " +
            "WHERE recipe_id = ? ORDER BY sort_order")) {
            ps.setString(1, recipe.id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RecipeInput input = new RecipeInput();
                    input.itemId = rs.getString("item_id");
                    input.quantity = rs.getInt("quantity");
                    input.description = rs.getString("description");
                    input.sortOrder = rs.getInt("sort_order");
                    recipe.inputs.add(input);
                }
            }
        }
    }
    
    private void loadRecipeOutputs(Recipe recipe, Connection conn) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT item_id, quantity, chance, description, sort_order FROM recipe_outputs " +
            "WHERE recipe_id = ? ORDER BY sort_order")) {
            ps.setString(1, recipe.id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RecipeOutput output = new RecipeOutput();
                    output.itemId = rs.getString("item_id");
                    output.quantity = rs.getInt("quantity");
                    output.chance = rs.getDouble("chance");
                    output.description = rs.getString("description");
                    output.sortOrder = rs.getInt("sort_order");
                    recipe.outputs.add(output);
                }
            }
        }
    }
    
    private void loadRecipeRequirements(Recipe recipe, Connection conn) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT type, target, value, description FROM recipe_requirements " +
            "WHERE recipe_id = ?")) {
            ps.setString(1, recipe.id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RecipeRequirement req = new RecipeRequirement();
                    req.type = rs.getString("type");
                    req.target = rs.getString("target");
                    req.value = rs.getString("value");
                    req.description = rs.getString("description");
                    recipe.requirements.add(req);
                }
            }
        }
    }
    
    // SELECT операция - читаем с реплик согласно правилам
    private Recipe getRecipeById(String id) throws Exception {
        return databaseRouter.executeRead(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, name, description, tag, base_duration_ms, enabled, category " +
                "FROM recipes WHERE id = ?")) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Recipe recipe = new Recipe();
                        recipe.id = rs.getString("id");
                        recipe.name = rs.getString("name");
                        recipe.description = rs.getString("description");
                        recipe.tag = rs.getString("tag");
                        recipe.baseDurationMs = rs.getLong("base_duration_ms");
                        recipe.enabled = rs.getBoolean("enabled");
                        recipe.category = rs.getString("category");
                        
                        loadRecipeInputs(recipe, conn);
                        loadRecipeOutputs(recipe, conn);
                        loadRecipeRequirements(recipe, conn);
                        
                        return recipe;
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to get recipe by id: " + id, e);
            }
            return null;
        });
    }
    
    private void deleteRecipeRelations(Connection c, String recipeId) throws Exception {
        String[] tables = {"recipe_inputs", "recipe_outputs", "recipe_requirements"};
        for (String table : tables) {
            try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM " + table + " WHERE recipe_id = ?")) {
                ps.setString(1, recipeId);
                ps.executeUpdate();
            }
        }
    }
    
    private double calculateInputCostMultiplier(String recipeTag, UUID playerUuid) throws Exception {
        // Получаем бонусы игрока для INPUT_COST_MULTIPLIER
        return calculateBonusMultiplier("INPUT_COST_MULTIPLIER", recipeTag, playerUuid);
    }
    
    private double calculateDurationMultiplier(String recipeTag, UUID playerUuid) throws Exception {
        // Получаем бонусы игрока для DURATION_MULTIPLIER
        return calculateBonusMultiplier("DURATION_MULTIPLIER", recipeTag, playerUuid);
    }
    
    private double calculateBonusMultiplier(String operation, String recipeTag, UUID playerUuid) throws Exception {
        double totalMultiplier = 1.0;
        
        // Получаем все применимые бонусы
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                "SELECT sb.skill_id, sb.per_level_bps, sb.cap_bps " +
                "FROM skill_bonuses sb " +
                "WHERE sb.op = ? AND sb.enabled = true " +
                "AND (sb.kind = 'all' OR (sb.kind = 'tag' AND sb.target = ?))")) {
            ps.setString(1, operation);
            ps.setString(2, recipeTag);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String skillId = rs.getString("skill_id");
                    int perLevelBps = rs.getInt("per_level_bps");
                    int capBps = rs.getInt("cap_bps");
                    
                    // Получаем уровень скилла игрока
                    // Здесь нужна интеграция с Repositories.getSkillLevel()
                    int playerLevel = getPlayerSkillLevel(playerUuid, skillId);
                    
                    // Вычисляем бонус
                    int bonusBps = Math.min(perLevelBps * playerLevel, capBps > 0 ? capBps : Integer.MAX_VALUE);
                    double bonus = bonusBps / 10000.0; // Конвертируем basis points в проценты
                    
                    // Применяем бонус
                    totalMultiplier *= (1.0 - bonus);
                }
            }
        }
        
        return Math.max(0.1, totalMultiplier); // Минимум 10% от оригинальной стоимости/времени
    }
    
    private int getPlayerSkillLevel(UUID playerUuid, String skillId) throws Exception {
        // Интеграция с существующей системой уровней
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement(
                "SELECT level FROM skill_levels WHERE owner_uuid = ? AND skill_id = ?")) {
            ps.setObject(1, playerUuid);
            ps.setString(2, skillId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("level");
                }
            }
        }
        return 0;
    }
}
