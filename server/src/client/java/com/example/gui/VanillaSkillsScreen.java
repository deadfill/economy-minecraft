package com.example.gui;

import com.example.skill.EconomyApiClient;
import com.example.skill.NatsSkillClient;
import com.example.skill.ServerSkill;
import com.example.skill.SkillTraining;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Экран системы скиллов на основе стандартного Minecraft Screen
 * Простой и надежный подход без внешних библиотек
 */
public class VanillaSkillsScreen extends Screen {
    private static final Logger LOGGER = Logger.getLogger(VanillaSkillsScreen.class.getName());
    
    private final EconomyApiClient apiClient;
    private final NatsSkillClient natsClient;
    private final List<ServerSkill> skills;
    private boolean dataLoaded = false;
    private String statusMessage = "🔄 Загрузка данных...";
    private int scrollOffset = 0;
    private final int maxVisibleSkills = 3;
    private long lastUpdateTime = 0;
    // Автоматическое обновление больше не нужно - используем NATS real-time события

    public VanillaSkillsScreen() {
        super(Component.literal("Система скиллов"));
        this.apiClient = new EconomyApiClient();
        this.skills = new ArrayList<>();
        this.lastUpdateTime = System.currentTimeMillis();
        
        // Инициализируем NATS клиент для real-time обновлений
        UUID playerUuid = apiClient.getCurrentPlayerUuid();
        if (playerUuid != null) {
            this.natsClient = new NatsSkillClient(playerUuid);
            setupNatsHandlers();
            natsClient.connect();
        } else {
            this.natsClient = null;
            LOGGER.warning("Player UUID is null, NATS client not initialized");
        }
    }

    /**
     * Настройка обработчиков NATS событий
     */
    private void setupNatsHandlers() {
        // Обработчик обновления уровня скилла
        natsClient.setOnSkillLevelUpdate(update -> {
            for (ServerSkill skill : skills) {
                if (skill.id.equals(update.skillId)) {
                    skill.currentLevel = update.newLevel;
                    statusMessage = String.format("✨ %s достиг уровня %d!", 
                        skill.title, update.newLevel);
                    playLevelUpSound();
                    LOGGER.info("Skill level updated via NATS: " + skill.title + " -> " + update.newLevel);
                    break;
                }
            }
        });
        
        // Обработчик обновления статуса тренировки
        natsClient.setOnTrainingStatusUpdate(update -> {
            for (ServerSkill skill : skills) {
                if (skill.id.equals(update.skillId)) {
                    if ("COMPLETED".equals(update.status)) {
                        skill.activeTraining = null;
                        skill.currentLevel = update.targetLevel;
                        statusMessage = String.format("🎉 Тренировка %s завершена! Уровень: %d", 
                            skill.title, update.targetLevel);
                        playLevelUpSound();
                        LOGGER.info("Training completed via NATS: " + skill.title + " -> level " + update.targetLevel);
                    } else if ("STARTED".equals(update.status)) {
                        // Создаем объект активной тренировки
                        skill.activeTraining = new SkillTraining(
                            update.skillId, update.targetLevel, 
                            update.startMs, update.endMs);
                        statusMessage = String.format("🚀 Тренировка %s начата!", skill.title);
                        LOGGER.info("Training started via NATS: " + skill.title);
                    }
                    break;
                }
            }
        });
        
        natsClient.setOnConnected(() -> {
            statusMessage = "📡 Подключен к NATS серверу - real-time обновления активны";
            LOGGER.info("NATS client connected - real-time updates enabled");
        });
        
        natsClient.setOnDisconnected(() -> {
            statusMessage = "⚠️ Соединение с NATS потеряно - обновления приостановлены";
            LOGGER.warning("NATS client disconnected - updates paused");
        });
    }
    
    /**
     * Воспроизвести звук повышения уровня
     */
    private void playLevelUpSound() {
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0F)
        );
    }

    @Override
    protected void init() {
        super.init();
        
        // Кнопка "Обновить" 
        this.addRenderableWidget(Button.builder(
            Component.literal("🔄 Обновить"),
            button -> {
                playClickSound();
                // Можно выбрать: forceLoadSkillsData() для принудительного
                // или loadSkillsData() для обычного обновления
                loadSkillsData(); // Обычное обновление с кэшем
                lastUpdateTime = System.currentTimeMillis();
            }
        ).bounds(this.width / 2 - 155, this.height - 40, 100, 20).build());

        // Кнопка "Прокрутка вверх"
        this.addRenderableWidget(Button.builder(
            Component.literal("▲ Вверх"),
            button -> {
                playClickSound();
                if (scrollOffset > 0) {
                    scrollOffset--;
                }
            }
        ).bounds(this.width / 2 - 50, this.height - 40, 45, 20).build());

        // Кнопка "Прокрутка вниз"
        this.addRenderableWidget(Button.builder(
            Component.literal("▼ Вниз"),
            button -> {
                playClickSound();
                if (scrollOffset < Math.max(0, skills.size() - maxVisibleSkills)) {
                    scrollOffset++;
                }
            }
        ).bounds(this.width / 2 + 5, this.height - 40, 45, 20).build());

        // Кнопка "Закрыть"
        this.addRenderableWidget(Button.builder(
            Component.literal("❌ Закрыть"),
            button -> {
                playClickSound();
                onClose();
            }
        ).bounds(this.width / 2 + 55, this.height - 40, 100, 20).build());

        // Загружаем данные при инициализации
        if (!dataLoaded) {
            loadSkillsData();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // Автоматическое обновление больше не нужно - используем NATS real-time события
        
        // Рендерим фон
        this.renderBackground(graphics, mouseX, mouseY, partialTicks);
        
        // Заголовок
        graphics.drawCenteredString(this.font, "🏭 Система скиллов", this.width / 2, 20, 0xFFFFFF);
        graphics.drawCenteredString(this.font, "Развивайте навыки для получения бонусов", this.width / 2, 35, 0xAAAAAA);

        // Статус
        graphics.drawCenteredString(this.font, statusMessage, this.width / 2, 55, 0xFFAA00);

        if (dataLoaded && !skills.isEmpty()) {
            renderSkills(graphics, mouseX, mouseY);
            renderScrollInfo(graphics);
        } else if (dataLoaded) {
            graphics.drawCenteredString(this.font, "❌ Скиллы не найдены", this.width / 2, 80, 0xFF0000);
        }

        // Рендерим кнопки и другие элементы
        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    /**
     * Отрисовка скиллов
     */
    private void renderSkills(GuiGraphics graphics, int mouseX, int mouseY) {
        int startY = 80;
        int skillHeight = 100;
        int skillWidth = 500;
        int x = (this.width - skillWidth) / 2;

        // Показываем только часть скиллов в зависимости от прокрутки
        int endIndex = Math.min(scrollOffset + maxVisibleSkills, skills.size());
        
        for (int i = scrollOffset; i < endIndex; i++) {
            ServerSkill skill = skills.get(i);
            int y = startY + (i - scrollOffset) * skillHeight;

            // Фон панели скилла
            graphics.fill(x - 5, y - 5, x + skillWidth + 5, y + skillHeight - 5, 0x80000000);
            graphics.fill(x, y, x + skillWidth, y + skillHeight - 10, 0x40FFFFFF);

            // Рамка
            graphics.fill(x - 1, y - 1, x + skillWidth + 1, y + 1, 0xFF666666);
            graphics.fill(x - 1, y + skillHeight - 11, x + skillWidth + 1, y + skillHeight - 9, 0xFF666666);
            graphics.fill(x - 1, y - 1, x + 1, y + skillHeight - 9, 0xFF666666);
            graphics.fill(x + skillWidth - 1, y - 1, x + skillWidth + 1, y + skillHeight - 9, 0xFF666666);

            // Название и уровень
            String titleText = skill.getIcon() + " " + skill.title;
            graphics.drawString(this.font, titleText, x + 10, y + 5, 0xFFFFFF);
            
            String levelText = "Уровень " + skill.currentLevel + "/" + skill.maxLevel;
            graphics.drawString(this.font, levelText, x + skillWidth - 10 - this.font.width(levelText), y + 5, 0xFFFF00);

            // Описание
            graphics.drawString(this.font, skill.description, x + 10, y + 20, 0xCCCCCC);

            // Бонус
            String bonusText = skill.getBonusDescription();
            if (!bonusText.isEmpty()) {
                graphics.drawString(this.font, "💎 " + bonusText, x + 10, y + 35, 0x00FF00);
            }

            // Статус тренировки
            if (skill.isTraining()) {
                float progress = skill.getTrainingProgress();
                String progressText = String.format("⏱️ Тренировка: %.1f%% (%s)", progress * 100, skill.getFormattedTimeLeft());
                
                // Анимированный цвет для активной тренировки
                long time = System.currentTimeMillis();
                float pulse = (float) (0.7 + 0.3 * Math.sin(time * 0.003));
                int animatedColor = (int) (0xFF * pulse) << 16 | (int) (0xAA * pulse) << 8;
                
                graphics.drawString(this.font, progressText, x + 10, y + 50, 0xFF000000 | animatedColor);

                // Прогресс-бар
                int barWidth = 300;
                int barHeight = 8;
                int barX = x + 10;
                int barY = y + 65;
                
                // Фон прогресс-бара
                graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF333333);
                
                // Рамка прогресс-бара
                graphics.fill(barX - 1, barY - 1, barX + barWidth + 1, barY - 1, 0xFF666666);
                graphics.fill(barX - 1, barY + barHeight, barX + barWidth + 1, barY + barHeight + 1, 0xFF666666);
                graphics.fill(barX - 1, barY - 1, barX - 1, barY + barHeight + 1, 0xFF666666);
                graphics.fill(barX + barWidth, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0xFF666666);
                
                // Заполнение прогресс-бара
                int fillWidth = (int) (barWidth * progress);
                if (fillWidth > 0) {
                    // Градиент от темного к светлому
                    int color = skill.getColor();
                    int darkColor = darkenColor(color, 0.7f);
                    
                    for (int px = 0; px < fillWidth; px++) {
                        float ratio = (float) px / fillWidth;
                        int blendedColor = blendColors(darkColor, color, ratio);
                        graphics.fill(barX + px, barY, barX + px + 1, barY + barHeight, blendedColor);
                    }
                }
                
                // Процент в центре прогресс-бара
                String percentText = String.format("%.1f%%", progress * 100);
                int textWidth = this.font.width(percentText);
                graphics.drawString(this.font, percentText, barX + (barWidth - textWidth) / 2, barY + 1, 0xFFFFFF);
                
            } else if (skill.canTrain()) {
                String trainText = "📈 Нажмите T для тренировки (" + skill.getFormattedDuration() + ")";
                graphics.drawString(this.font, trainText, x + 10, y + 50, 0xFFFF00);
                
                // Подсказка
                graphics.drawString(this.font, "Или используйте кнопку 'Тренировать'", x + 10, y + 65, 0xAAAAAA);
                
            } else if (skill.currentLevel >= skill.maxLevel) {
                graphics.drawString(this.font, "✅ Максимальный уровень достигнут", x + 10, y + 50, 0x00FF00);
            }

            // Кнопка тренировки (если можно тренировать)
            if (skill.canTrain()) {
                int buttonX = x + skillWidth - 120;
                int buttonY = y + 60;
                int buttonWidth = 100;
                int buttonHeight = 20;
                
                // Проверяем, наведена ли мышь на кнопку
                boolean isHovered = mouseX >= buttonX && mouseX <= buttonX + buttonWidth && 
                                  mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
                
                // Фон кнопки
                int buttonColor = isHovered ? 0x80FFFF00 : 0x80666666;
                graphics.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, buttonColor);
                
                // Рамка кнопки
                int borderColor = isHovered ? 0xFFFFFF00 : 0xFF999999;
                graphics.fill(buttonX - 1, buttonY - 1, buttonX + buttonWidth + 1, buttonY, borderColor);
                graphics.fill(buttonX - 1, buttonY + buttonHeight, buttonX + buttonWidth + 1, buttonY + buttonHeight + 1, borderColor);
                graphics.fill(buttonX - 1, buttonY - 1, buttonX, buttonY + buttonHeight + 1, borderColor);
                graphics.fill(buttonX + buttonWidth, buttonY - 1, buttonX + buttonWidth + 1, buttonY + buttonHeight + 1, borderColor);
                
                // Текст кнопки
                String buttonText = "Тренировать";
                int textWidth = this.font.width(buttonText);
                graphics.drawString(this.font, buttonText, buttonX + (buttonWidth - textWidth) / 2, buttonY + 6, 0xFFFFFF);
            }
        }
    }

    /**
     * Отрисовка информации о прокрутке
     */
    private void renderScrollInfo(GuiGraphics graphics) {
        if (skills.size() > maxVisibleSkills) {
            String scrollInfo = String.format("Показано %d-%d из %d скиллов", 
                scrollOffset + 1, 
                Math.min(scrollOffset + maxVisibleSkills, skills.size()), 
                skills.size());
            graphics.drawCenteredString(this.font, scrollInfo, this.width / 2, this.height - 60, 0xAAAAAA);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Обработка кликов по кнопкам тренировки
        if (dataLoaded && !skills.isEmpty()) {
            int startY = 80;
            int skillHeight = 100;
            int skillWidth = 500;
            int x = (this.width - skillWidth) / 2;
            
            int endIndex = Math.min(scrollOffset + maxVisibleSkills, skills.size());
            
            for (int i = scrollOffset; i < endIndex; i++) {
                ServerSkill skill = skills.get(i);
                if (!skill.canTrain()) continue;
                
                int y = startY + (i - scrollOffset) * skillHeight;
                int buttonX = x + skillWidth - 120;
                int buttonY = y + 60;
                int buttonWidth = 100;
                int buttonHeight = 20;
                
                if (mouseX >= buttonX && mouseX <= buttonX + buttonWidth && 
                    mouseY >= buttonY && mouseY <= buttonY + buttonHeight) {
                    playClickSound();
                    startTraining(skill);
                    return true;
                }
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Клавиша T для тренировки первого доступного скилла
        if (keyCode == 84) { // T key
            for (ServerSkill skill : skills) {
                if (skill.canTrain()) {
                    startTraining(skill);
                    break;
                }
            }
            return true;
        }
        
        // Стрелки для прокрутки
        if (keyCode == 265) { // UP arrow
            if (scrollOffset > 0) {
                scrollOffset--;
                playClickSound();
            }
            return true;
        }
        
        if (keyCode == 264) { // DOWN arrow
            if (scrollOffset < Math.max(0, skills.size() - maxVisibleSkills)) {
                scrollOffset++;
                playClickSound();
            }
            return true;
        }
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Быстрое обновление статуса тренировки (без полной перезагрузки)
     */
    private void refreshSkillsData() {
        // Защита от слишком частых обновлений
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime < 3000) { // Минимум 3 секунды между обновлениями
            return;
        }
        lastUpdateTime = currentTime;
        
        UUID playerUuid = apiClient.getCurrentPlayerUuid();
        if (playerUuid == null) return;

        // Обновляем только статус тренировки
        apiClient.getTrainingStatus(playerUuid).thenAccept(training -> {
            // Сбрасываем активные тренировки
            for (ServerSkill skill : skills) {
                skill.activeTraining = null;
            }
            
            // Устанавливаем новую активную тренировку
            if (training != null) {
                for (ServerSkill skill : skills) {
                    if (skill.id.equals(training.skillId)) {
                        skill.activeTraining = training;
                        break;
                    }
                }
            }
        }).exceptionally(throwable -> {
            // Тихо игнорируем ошибки при автообновлении
            return null;
        });
    }

    /**
     * Принудительно загрузить данные скиллов с сервера (игнорируя кэш)
     */
    private void forceLoadSkillsData() {
        statusMessage = "🔄 Принудительное обновление...";
        dataLoaded = false;
        scrollOffset = 0;

        UUID playerUuid = apiClient.getCurrentPlayerUuid();
        if (playerUuid == null) {
            statusMessage = "❌ Игрок не найден";
            return;
        }

        // Принудительно загружаем список скиллов (игнорируя кэш)
        apiClient.getSkills(true).thenCompose(serverSkills -> {
            skills.clear();
            skills.addAll(serverSkills);

            // Загружаем уровни для каждого скилла
            List<java.util.concurrent.CompletableFuture<Void>> levelFutures = new ArrayList<>();
            for (ServerSkill skill : skills) {
                var levelFuture = apiClient.getSkillLevel(playerUuid, skill.id)
                    .thenAccept(level -> skill.currentLevel = level);
                levelFutures.add(levelFuture);
            }

            return java.util.concurrent.CompletableFuture.allOf(levelFutures.toArray(new java.util.concurrent.CompletableFuture[0]));

        }).thenCompose(v -> {
            // Загружаем статус тренировки
            return apiClient.getTrainingStatus(playerUuid);

        }).thenAccept(training -> {
            // Обновляем активную тренировку
            if (training != null) {
                for (ServerSkill skill : skills) {
                    if (skill.id.equals(training.skillId)) {
                        skill.activeTraining = training;
                        break;
                    }
                }
            }

            // Обновляем UI в главном потоке
            Minecraft.getInstance().execute(() -> {
                dataLoaded = true;
                if (skills.isEmpty()) {
                    statusMessage = "❌ Скиллы не найдены на сервере";
                } else {
                    statusMessage = "✅ Принудительно обновлено " + skills.size() + " скиллов";
                }
            });

        }).exceptionally(throwable -> {
            Minecraft.getInstance().execute(() -> {
                statusMessage = "❌ Ошибка принудительного обновления: " + throwable.getMessage();
                dataLoaded = false;
            });
            return null;
        });
    }

    /**
     * Загрузить данные скиллов с сервера
     */
    private void loadSkillsData() {
        statusMessage = "🔄 Загрузка данных...";
        dataLoaded = false;
        scrollOffset = 0;

        UUID playerUuid = apiClient.getCurrentPlayerUuid();
        if (playerUuid == null) {
            statusMessage = "❌ Игрок не найден";
            return;
        }

        // Загружаем список скиллов
        apiClient.getSkills().thenCompose(serverSkills -> {
            // Обновляем только если получили данные
            if (!serverSkills.isEmpty() || skills.isEmpty()) {
                skills.clear();
                skills.addAll(serverSkills);
            }

            // Загружаем уровни для каждого скилла
            List<java.util.concurrent.CompletableFuture<Void>> levelFutures = new ArrayList<>();
            for (ServerSkill skill : skills) {
                var levelFuture = apiClient.getSkillLevel(playerUuid, skill.id)
                    .thenAccept(level -> skill.currentLevel = level);
                levelFutures.add(levelFuture);
            }

            return java.util.concurrent.CompletableFuture.allOf(levelFutures.toArray(new java.util.concurrent.CompletableFuture[0]));

        }).thenCompose(v -> {
            // Загружаем статус тренировки
            return apiClient.getTrainingStatus(playerUuid);

        }).thenAccept(training -> {
            // Обновляем активную тренировку
            if (training != null) {
                for (ServerSkill skill : skills) {
                    if (skill.id.equals(training.skillId)) {
                        skill.activeTraining = training;
                        break;
                    }
                }
            }

            // Обновляем UI в главном потоке
            Minecraft.getInstance().execute(() -> {
                dataLoaded = true;
                if (skills.isEmpty()) {
                    statusMessage = "❌ Скиллы не найдены на сервере";
                } else {
                    statusMessage = "✅ Загружено " + skills.size() + " скиллов. Используйте T для тренировки";
                }
            });

        }).exceptionally(throwable -> {
            Minecraft.getInstance().execute(() -> {
                statusMessage = "❌ Ошибка загрузки: " + throwable.getMessage();
                dataLoaded = false;
            });
            return null;
        });
    }

    /**
     * Начать тренировку скилла
     */
    private void startTraining(ServerSkill skill) {
        UUID playerUuid = apiClient.getCurrentPlayerUuid();
        if (playerUuid == null) return;

        playClickSound();
        statusMessage = "🔄 Начинаем тренировку " + skill.title + "...";

        apiClient.startTraining(playerUuid, skill.id).thenAccept(success -> {
            Minecraft.getInstance().execute(() -> {
                if (success) {
                    statusMessage = "✅ Тренировка " + skill.title + " начата! Ожидаем подтверждения...";
                    // Обновление придет автоматически через NATS событие
                    LOGGER.info("Training request sent, waiting for NATS confirmation");
                } else {
                    statusMessage = "❌ Не удалось начать тренировку " + skill.title;
                }
            });
        });
    }

    /**
     * Воспроизвести звук клика
     */
    private void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
        );
    }

    /**
     * Затемнить цвет
     */
    private int darkenColor(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Смешать два цвета
     */
    private int blendColors(int color1, int color2, float ratio) {
        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;
        
        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;
        
        int a = (int) (a1 + (a2 - a1) * ratio);
        int r = (int) (r1 + (r2 - r1) * ratio);
        int g = (int) (g1 + (g2 - g1) * ratio);
        int b = (int) (b1 + (b2 - b1) * ratio);
        
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    public void onClose() {
        // Отключаем NATS клиент
        if (natsClient != null) {
            natsClient.disconnect();
        }
        
        // Закрываем API клиент
        apiClient.close();
        super.onClose();
        
        LOGGER.info("VanillaSkillsScreen closed, connections terminated");
    }

    @Override
    public boolean isPauseScreen() {
        return false; // Не ставим игру на паузу
    }
}
