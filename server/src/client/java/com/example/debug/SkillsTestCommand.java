package com.example.debug;

import com.example.skill.EconomyApiClient;
import com.example.skill.ServerSkill;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Тестовая команда для проверки работы скиллов
 */
public class SkillsTestCommand {
    
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            ClientCommandManager.literal("testskills")
                .executes(context -> {
                    context.getSource().sendFeedback(Component.literal("§6[Skills Test] §7Загрузка скиллов из базы данных..."));
                    loadAndDisplaySkills(context.getSource());
                    return 1;
                })
        );
    }
    
    private static void loadAndDisplaySkills(FabricClientCommandSource source) {
        EconomyApiClient client = new EconomyApiClient();
        
        // Получаем список скиллов
        CompletableFuture<List<ServerSkill>> skillsFuture = client.getSkills();
        
        skillsFuture.thenAccept(skills -> {
            source.sendFeedback(Component.literal("§6[Skills Test] §aУспешно загружено " + skills.size() + " скиллов:"));
            
            for (ServerSkill skill : skills) {
                source.sendFeedback(Component.literal("§6[Skills Test] §7- " + skill.title + " (" + skill.id + ")"));
                source.sendFeedback(Component.literal("§6[Skills Test] §7  Уровень: " + skill.currentLevel + "/" + skill.maxLevel));
                source.sendFeedback(Component.literal("§6[Skills Test] §7  Описание: " + skill.description));
                
                if (skill.isTraining()) {
                    source.sendFeedback(Component.literal("§6[Skills Test] §e  Тренировка: " + String.format("%.1f%%", skill.getTrainingProgress() * 100)));
                } else if (skill.canTrain()) {
                    source.sendFeedback(Component.literal("§6[Skills Test] §a  Можно тренировать"));
                } else {
                    source.sendFeedback(Component.literal("§6[Skills Test] §c  Недоступно для тренировки"));
                }
            }
        }).exceptionally(throwable -> {
            source.sendFeedback(Component.literal("§6[Skills Test] §cОшибка загрузки скиллов: " + throwable.getMessage()));
            return null;
        });
    }
}