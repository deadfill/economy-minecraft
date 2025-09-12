package com.example.debug;

import com.example.skill.EconomyApiClient;
import com.example.skill.ServerSkill;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Демонстрация работы с скиллами
 */
public class SkillDemo {
    
    public static void demonstrateSkillsLoading() {
        EconomyApiClient client = new EconomyApiClient();
        
        // Получаем список скиллов
        CompletableFuture<List<ServerSkill>> skillsFuture = client.getSkills();
        
        skillsFuture.thenAccept(skills -> {
            System.out.println("Загружено " + skills.size() + " скиллов из базы данных:");
            for (ServerSkill skill : skills) {
                System.out.println("- " + skill.title + " (" + skill.id + ")");
                System.out.println("  Описание: " + skill.description);
                System.out.println("  Макс. уровень: " + skill.maxLevel);
                System.out.println("  Длительности: " + java.util.Arrays.toString(skill.durationsMs));
                
                // Получаем уровень скилла для текущего игрока
                UUID playerUuid = client.getCurrentPlayerUuid();
                if (playerUuid != null) {
                    client.getSkillLevel(playerUuid, skill.id).thenAccept(level -> {
                        System.out.println("  Текущий уровень: " + level);
                    });
                }
            }
        }).exceptionally(throwable -> {
            System.err.println("Ошибка загрузки скиллов: " + throwable.getMessage());
            return null;
        });
    }
}