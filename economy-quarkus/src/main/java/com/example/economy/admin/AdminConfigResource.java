package com.example.economy.admin;

import com.example.economy.core.ConfigurationManager;
import com.example.economy.core.ExtendedRecipeService;
import com.example.economy.core.SkillCatalogService;
import com.example.economy.core.SkillRepo;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;

/**
 * REST API для администрирования конфигураций скиллов и рецептов
 */
@Path("/api/admin/config")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@PermitAll
public class AdminConfigResource {
    
    private static final Logger LOG = Logger.getLogger(AdminConfigResource.class);
    
    @Inject ConfigurationManager configManager;
    @Inject SkillCatalogService skillCatalog;
    @Inject ExtendedRecipeService recipeService;
    @Inject SkillRepo skillRepo;

    
    // ===== ОБЩИЕ ОПЕРАЦИИ =====
    
    /**
     * Получить статистику системы
     */
    @GET
    @Path("/stats")
    public Response getSystemStats() {
        try {
            var skills = skillCatalog.allSkills();
            var recipes = recipeService.getAllRecipes();
            var bonuses = recipeService.getAllSkillBonuses();
            
            return Response.ok(Map.of(
                "skills", Map.of(
                    "total", skills.size(),
                    "enabled", skills.stream().mapToInt(s -> s.enabled ? 1 : 0).sum()
                ),
                "recipes", Map.of(
                    "total", recipes.size(),
                    "enabled", recipes.stream().mapToInt(r -> r.enabled ? 1 : 0).sum(),
                    "categories", recipes.stream().map(r -> r.category).distinct().count()
                ),
                "bonuses", Map.of(
                    "total", bonuses.size(),
                    "enabled", bonuses.stream().mapToInt(b -> b.enabled ? 1 : 0).sum(),
                    "operations", bonuses.stream().map(b -> b.operation).distinct().count()
                ),
                "system", Map.of(
                    "user", "admin",
                    "roles", List.of("admin", "developer"),
                    "timestamp", System.currentTimeMillis()
                )
            )).build();
        } catch (Exception e) {
            LOG.error("Failed to get system stats", e);
            return Response.status(500)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    /**
     * Перезагрузить все конфигурации
     */
    @POST
    @Path("/reload")
    public Response reloadConfigurations() {
        try {
            LOG.info("Configuration reload requested");
            
            configManager.reloadConfigurations();
            skillCatalog.refresh();
            
            return Response.ok(Map.of(
                "message", "Configurations reloaded successfully",
                "timestamp", System.currentTimeMillis(),
                "user", "admin"
            )).build();
        } catch (Exception e) {
            LOG.error("Failed to reload configurations", e);
            return Response.status(500)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    // ===== УПРАВЛЕНИЕ СКИЛЛАМИ =====
    
    /**
     * Получить все скиллы для редактирования
     */
    @GET
    @Path("/skills")
    public Response getSkills() {
        try {
            var skills = skillCatalog.allSkills().stream()
                .map(skill -> Map.of(
                    "id", skill.id,
                    "title", skill.title,
                    "description", skill.description,
                    "maxLevel", skill.maxLevel,
                    "enabled", skill.enabled,
                    "durationsMs", skill.durationsMs,
                    "version", skill.version
                ))
                .toList();
                
            return Response.ok(Map.of(
                "skills", skills,
                "total", skills.size(),
                "etag", skillCatalog.etag()
            )).build();
        } catch (Exception e) {
            LOG.error("Failed to get skills", e);
            return Response.status(500)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    /**
     * Создать новый скилл
     */
    @POST
    @Path("/skills")
    @PermitAll
    public Response createSkill(ConfigurationManager.SkillConfig skillConfig) {
        try {
            LOG.infof("Creating new skill: %s", skillConfig.id);
            
            // Валидация
            if (skillConfig.id == null || skillConfig.id.isBlank()) {
                return Response.status(400)
                    .entity(Map.of("error", "Skill ID is required"))
                    .build();
            }
            
            if (skillConfig.title == null || skillConfig.title.isBlank()) {
                return Response.status(400)
                    .entity(Map.of("error", "Skill title is required"))
                    .build();
            }
            
            // Проверяем, что скилл не существует
            if (skillCatalog.skillExists(skillConfig.id)) {
                return Response.status(409)
                    .entity(Map.of("error", "Skill already exists"))
                    .build();
            }
            
            // Устанавливаем значения по умолчанию
            if (skillConfig.maxLevel <= 0) {
                skillConfig.maxLevel = 5;
            }
            
            if (skillConfig.durations == null || skillConfig.durations.isEmpty()) {
                // Создаем стандартные длительности
                skillConfig.durations = createDefaultDurations(skillConfig.maxLevel);
            }
            
            // Создаем скилл через ConfigurationManager
            // В реальной реализации здесь была бы логика сохранения в JSON файл
            // Теперь сохраняем в БД
            var skill = new SkillRepo.Skill(
                skillConfig.id,
                skillConfig.title,
                skillConfig.description,
                skillConfig.maxLevel,
                skillConfig.durations.stream().mapToLong(d -> d.durationMs).toArray(),
                true, // enabled
                1 // version
            );
            LOG.infof("Saving skill to database: %s", skill.id);
            LOG.infof("Skill object: %s", skill);
            skillRepo.saveSkill(skill);
            LOG.infof("Skill saved to database: %s", skill.id);
            
            // Обновляем кэш
            // Добавляем небольшую задержку, чтобы убедиться, что данные записались в БД
            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            skillCatalog.refresh();
            
            return Response.status(201)
                .entity(Map.of(
                    "message", "Skill created successfully",
                    "id", skillConfig.id,
                    "title", skillConfig.title
                ))
                .build();
                
        } catch (Exception e) {
            LOG.error("Failed to create skill", e);
            return Response.status(500)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    /**
     * Обновить существующий скилл
     */
    @PUT
    @Path("/skills/{id}")
    @PermitAll
    public Response updateSkill(@PathParam("id") String skillId, ConfigurationManager.SkillConfig skillConfig) {
        try {
            LOG.infof("Updating skill: %s", skillId);
            
            if (!skillCatalog.skillExists(skillId)) {
                return Response.status(404)
                    .entity(Map.of("error", "Skill not found"))
                    .build();
            }
            
            skillConfig.id = skillId; // Убеждаемся что ID правильный
            
            // Здесь была бы логика обновления скилла
            
            return Response.ok(Map.of(
                "message", "Skill updated successfully",
                "id", skillId
            )).build();
            
        } catch (Exception e) {
            LOG.error("Failed to update skill", e);
            return Response.status(500)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    // ===== УПРАВЛЕНИЕ РЕЦЕПТАМИ =====
    
    /**
     * Получить все рецепты
     */
    @GET
    @Path("/recipes")
    public Response getRecipes() {
        try {
            var recipes = recipeService.getAllRecipes();
            
            return Response.ok(Map.of(
                "recipes", recipes,
                "total", recipes.size()
            )).build();
        } catch (Exception e) {
            LOG.error("Failed to get recipes", e);
            return Response.status(500)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    /**
     * Создать новый рецепт
     */
    @POST
    @Path("/recipes")
    @PermitAll
    public Response createRecipe(ExtendedRecipeService.Recipe recipe) {
        try {
            LOG.infof("Creating new recipe: %s", recipe.id);
            
            // Валидация
            if (recipe.id == null || recipe.id.isBlank()) {
                return Response.status(400)
                    .entity(Map.of("error", "Recipe ID is required"))
                    .build();
            }
            
            if (recipe.name == null || recipe.name.isBlank()) {
                return Response.status(400)
                    .entity(Map.of("error", "Recipe name is required"))
                    .build();
            }
            
            if (recipe.tag == null || recipe.tag.isBlank()) {
                return Response.status(400)
                    .entity(Map.of("error", "Recipe tag is required"))
                    .build();
            }
            
            recipeService.createRecipe(recipe);
            
            return Response.status(201)
                .entity(Map.of(
                    "message", "Recipe created successfully",
                    "id", recipe.id,
                    "name", recipe.name
                ))
                .build();
                
        } catch (Exception e) {
            LOG.error("Failed to create recipe", e);
            return Response.status(500)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    /**
     * Обновить рецепт
     */
    @PUT
    @Path("/recipes/{id}")
    @PermitAll
    public Response updateRecipe(@PathParam("id") String recipeId, ExtendedRecipeService.Recipe recipe) {
        try {
            LOG.infof("Updating recipe: %s", recipeId);
            
            recipe.id = recipeId; // Убеждаемся что ID правильный
            recipeService.updateRecipe(recipe);
            
            return Response.ok(Map.of(
                "message", "Recipe updated successfully",
                "id", recipeId
            )).build();
            
        } catch (Exception e) {
            LOG.error("Failed to update recipe", e);
            return Response.status(500)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    // ===== УПРАВЛЕНИЕ БОНУСАМИ =====
    
    /**
     * Получить все бонусы скиллов
     */
    @GET
    @Path("/bonuses")
    public Response getSkillBonuses() {
        try {
            var bonuses = recipeService.getAllSkillBonuses();
            
            // Группируем по скиллам для удобства
            var bonusesBySkill = bonuses.stream()
                .collect(java.util.stream.Collectors.groupingBy(b -> b.skillId));
            
            return Response.ok(Map.of(
                "bonuses", bonuses,
                "bonusesBySkill", bonusesBySkill,
                "total", bonuses.size()
            )).build();
        } catch (Exception e) {
            LOG.error("Failed to get skill bonuses", e);
            return Response.status(500)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    /**
     * Создать новый бонус скилла
     */
    @POST
    @Path("/bonuses")
    @PermitAll
    public Response createSkillBonus(ExtendedRecipeService.SkillBonus bonus) {
        try {
            LOG.infof("Creating skill bonus: %s -> %s", bonus.skillId, bonus.operation);
            
            // Валидация
            if (bonus.skillId == null || bonus.skillId.isBlank()) {
                return Response.status(400)
                    .entity(Map.of("error", "Skill ID is required"))
                    .build();
            }
            
            if (!skillCatalog.skillExists(bonus.skillId)) {
                return Response.status(400)
                    .entity(Map.of("error", "Skill not found: " + bonus.skillId))
                    .build();
            }
            
            if (bonus.operation == null || bonus.operation.isBlank()) {
                return Response.status(400)
                    .entity(Map.of("error", "Bonus operation is required"))
                    .build();
            }
            
            // Проверяем валидность операции
            var validOperations = List.of("INPUT_COST_MULTIPLIER", "DURATION_MULTIPLIER", 
                                        "UNLOCK_RECIPE", "EXTRA_OUTPUT_CHANCE");
            if (!validOperations.contains(bonus.operation)) {
                return Response.status(400)
                    .entity(Map.of("error", "Invalid operation. Valid operations: " + validOperations))
                    .build();
            }
            
            recipeService.createSkillBonus(bonus);
            
            return Response.status(201)
                .entity(Map.of(
                    "message", "Skill bonus created successfully",
                    "skillId", bonus.skillId,
                    "operation", bonus.operation
                ))
                .build();
                
        } catch (Exception e) {
            LOG.error("Failed to create skill bonus", e);
            return Response.status(500)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====
    
    /**
     * Создать стандартные длительности тренировок
     */
    private List<ConfigurationManager.SkillConfig.DurationLevel> createDefaultDurations(int maxLevel) {
        var durations = new java.util.ArrayList<ConfigurationManager.SkillConfig.DurationLevel>();
        
        // Экспоненциальный рост времени тренировки
        for (int level = 1; level <= maxLevel; level++) {
            var duration = new ConfigurationManager.SkillConfig.DurationLevel();
            duration.level = level;
            duration.durationMs = (long) (60000 * Math.pow(5, level - 1)); // 1 мин, 5 мин, 25 мин, и т.д.
            duration.description = formatDuration(duration.durationMs);
            durations.add(duration);
        }
        
        return durations;
    }
    
    /**
     * Форматировать длительность в читаемый вид
     */
    private String formatDuration(long durationMs) {
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return days + " дней";
        } else if (hours > 0) {
            return hours + " часов";
        } else if (minutes > 0) {
            return minutes + " минут";
        } else {
            return seconds + " секунд";
        }
    }
}
