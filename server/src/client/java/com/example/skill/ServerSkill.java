package com.example.skill;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Модель скилла, получаемая с сервера economy API
 */
public class ServerSkill {
    @JsonProperty("id")
    public String id;
    
    @JsonProperty("title")
    public String title;
    
    @JsonProperty("desc")
    public String description;
    
    @JsonProperty("maxLevel")
    public int maxLevel;
    
    @JsonProperty("durationsMs")
    public long[] durationsMs;

    // Локальные данные (получаются отдельными запросами)
    public int currentLevel = 0;
    public SkillTraining activeTraining = null;

    public ServerSkill() {}

    public ServerSkill(String id, String title, String description, int maxLevel, long[] durationsMs) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.maxLevel = maxLevel;
        this.durationsMs = durationsMs;
    }

    /**
     * Получить длительность тренировки для следующего уровня в миллисекундах
     */
    public long getTrainingDurationMs() {
        if (currentLevel >= maxLevel) return 0;
        return durationsMs[currentLevel]; // durationsMs[0] для уровня 1, durationsMs[1] для уровня 2, и т.д.
    }

    /**
     * Получить длительность тренировки для следующего уровня в читаемом формате
     */
    public String getFormattedDuration() {
        long durationMs = getTrainingDurationMs();
        if (durationMs == 0) return "Макс. уровень";
        
        return formatDuration(durationMs);
    }

    /**
     * Проверить, можно ли тренировать скилл
     */
    public boolean canTrain() {
        return currentLevel < maxLevel && activeTraining == null;
    }

    /**
     * Проверить, идет ли сейчас тренировка
     */
    public boolean isTraining() {
        return activeTraining != null && !activeTraining.isCompleted();
    }

    /**
     * Получить прогресс тренировки (0.0 - 1.0)
     */
    public float getTrainingProgress() {
        if (activeTraining == null) return 0f;
        return activeTraining.getProgress();
    }

    /**
     * Получить время до завершения тренировки в миллисекундах
     */
    public long getTimeLeftMs() {
        if (activeTraining == null) return 0;
        return activeTraining.getTimeLeftMs();
    }

    /**
     * Получить отформатированное время до завершения
     */
    public String getFormattedTimeLeft() {
        long timeLeft = getTimeLeftMs();
        if (timeLeft <= 0) return "Готово!";
        return formatDuration(timeLeft);
    }

    /**
     * Получить описание бонуса скилла
     */
    public String getBonusDescription() {
        if ("industry".equals(id)) {
            int bonusPercent = Math.min(25, currentLevel * 5);
            return String.format("-%d%% к стоимости ресурсов", bonusPercent);
        }
        return "";
    }

    /**
     * Форматировать длительность в читаемый вид
     */
    private static String formatDuration(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return String.format("%dд %dч", days, hours % 24);
        } else if (hours > 0) {
            return String.format("%dч %dм", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%dм %dс", minutes, seconds % 60);
        } else {
            return String.format("%dс", seconds);
        }
    }

    /**
     * Получить иконку для скилла
     */
    public String getIcon() {
        return switch (id) {
            case "industry" -> "🏭";
            default -> "⭐";
        };
    }

    /**
     * Получить цвет для скилла
     */
    public int getColor() {
        return switch (id) {
            case "industry" -> 0xFF8E44AD; // Фиолетовый
            default -> 0xFF95A5A6; // Серый
        };
    }

    @Override
    public String toString() {
        return String.format("ServerSkill{id='%s', title='%s', level=%d/%d}", 
                           id, title, currentLevel, maxLevel);
    }
}
