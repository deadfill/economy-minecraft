package com.example.gui.owo;

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
 * Экран системы скиллов в стиле EVE Online
 * Реализация без внешних библиотек
 */
public class EveStyleSkillsScreen extends Screen {
    private static final Logger LOGGER = Logger.getLogger(EveStyleSkillsScreen.class.getName());
    
    // Цветовая схема EVE Online
    private static final int EVE_BLUE = 0xFF00A0E0;
    private static final int EVE_DARK_BLUE = 0xFF002B49;
    private static final int EVE_VERY_DARK_BLUE = 0xFF001429;
    private static final int EVE_ORANGE = 0xFFFF6600;
    private static final int EVE_GREEN = 0xFF00CC0;
    private static final int EVE_RED = 0xFFFF3300;
    private static final int EVE_GRAY = 0xFFAAAAAA;
    
    private final EconomyApiClient apiClient;
    private final NatsSkillClient natsClient;
    private final List<ServerSkill> skills;
    private boolean dataLoaded = false;
    private String statusMessage = "🔄 Загрузка данных...";
    private int scrollOffset = 0;
    private final int maxVisibleSkills = 3;
    private long lastUpdateTime = 0;
    
    // Анимация пульсации
    private long animationStartTime = System.currentTimeMillis();

    public EveStyleSkillsScreen() {
        super(Component.literal("Система скиллов EVE Online"));
        this.apiClient = new EconomyApiClient();
        this.skills = new ArrayList<>();
        this.lastUpdateTime = System.currentTimeMillis();
        this.animationStartTime = System.currentTimeMillis();
        
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
                loadSkillsData();
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
        // Рендерим фон в стиле EVE Online
        renderEveBackground(graphics);
        
        // Заголовок в стиле EVE Online
        renderEveHeader(graphics);
        
        // Статус
        graphics.drawCenteredString(this.font, statusMessage, this.width / 2, 55, EVE_ORANGE);

        if (dataLoaded && !skills.isEmpty()) {
            renderSkills(graphics, mouseX, mouseY);
            renderScrollInfo(graphics);
        } else if (dataLoaded) {
            graphics.drawCenteredString(this.font, "❌ Скиллы не найдены", this.width / 2, 80, EVE_RED);
        }

        // Рендерим кнопки и другие элементы
        super.render(graphics, mouseX, mouseY, partialTicks);
    }
    
    /**
     * Отрисовка фона в стиле EVE Online
     */
    private void renderEveBackground(GuiGraphics graphics) {
        // Создаем градиентный фон
        int gradientHeight = this.height;
        for (int y = 0; y < gradientHeight; y++) {
            float ratio = (float) y / gradientHeight;
            int color = blendColors(EVE_VERY_DARK_BLUE, EVE_DARK_BLUE, ratio * 0.7f);
            graphics.fill(0, y, this.width, y + 1, color);
        }
        
        // Добавляем декоративные элементы
        long time = System.currentTimeMillis() - animationStartTime;
        float pulse = (float) (0.3 + 0.2 * Math.sin(time * 0.002));
        
        // Горизонтальные линии
        for (int i = 0; i < 5; i++) {
            int y = 100 + i * 30;
            int alpha = (int) (0x33 * (1.0 - pulse * 0.5));
            graphics.fill(0, y, this.width, y + 1, (alpha << 24) | (EVE_BLUE & 0xFFFFFF));
        }
    }
    
    /**
     * Отрисовка заголовка в стиле EVE Online
     */
    private void renderEveHeader(GuiGraphics graphics) {
        // Основной заголовок
        graphics.drawCenteredString(this.font, "🏭 Система скиллов EVE Online", this.width / 2, 20, EVE_BLUE);
        
        // Подзаголовок
        graphics.drawCenteredString(this.font, "Развивайте навыки для получения бонусов", this.width / 2, 35, EVE_GRAY);
        
        // Декоративная линия
        graphics.fill(this.width / 2 - 200, 45, this.width / 2 + 200, 46, EVE_BLUE);
    }

    /**
     * Отрисовка скиллов в стиле EVE Online
     */
    private void renderSkills(GuiGraphics graphics, int mouseX, int mouseY) {
        int startY = 80;
        int skillHeight = 120;
        int skillWidth = 550;
        int x = (this.width - skillWidth) / 2;

        // Показываем только часть скиллов в зависимости от прокрутки
        int endIndex = Math.min(scrollOffset + maxVisibleSkills, skills.size());
        
        for (int i = scrollOffset; i < endIndex; i++) {
            ServerSkill skill = skills.get(i);
            int y = startY + (i - scrollOffset) * skillHeight;

            // Фон панели скилла с эффектом EVE Online
            renderEveSkillPanel(graphics, x, y, skillWidth, skillHeight, skill, mouseX, mouseY);
        }
    }
    
    /**
     * Отрисовка панели скилла в стиле EVE Online
     */
    private void renderEveSkillPanel(GuiGraphics graphics, int x, int y, int width, int height, ServerSkill skill, int mouseX, int mouseY) {
        // Фон панели с градиентом
        for (int i = 0; i < height - 10; i++) {
            float ratio = (float) i / (height - 10);
            int color = blendColors(0x80001429, 0x40002B49, ratio);
            graphics.fill(x, y + i, x + width, y + i + 1, color);
        }
        
        // Полупрозрачная рамка
        graphics.fill(x - 2, y - 2, x + width + 2, y, EVE_BLUE);
        graphics.fill(x - 2, y + height - 12, x + width + 2, y + height - 10, EVE_BLUE);
        graphics.fill(x - 2, y - 2, x, y + height - 10, EVE_BLUE);
        graphics.fill(x + width, y - 2, x + width + 2, y + height - 10, EVE_BLUE);
        
        // Название и уровень
        String titleText = skill.getIcon() + " " + skill.title;
        graphics.drawString(this.font, titleText, x + 15, y + 10, EVE_BLUE);
        
        String levelText = "Уровень " + skill.currentLevel + "/" + skill.maxLevel;
        graphics.drawString(this.font, levelText, x + width - 15 - this.font.width(levelText), y + 10, EVE_ORANGE);

        // Описание
        graphics.drawString(this.font, skill.description, x + 15, y + 30, EVE_GRAY);

        // Бонус
        String bonusText = skill.getBonusDescription();
        if (!bonusText.isEmpty()) {
            graphics.drawString(this.font, "💎 " + bonusText, x + 15, y + 45, EVE_GREEN);
        }

        // Статус тренировки
        if (skill.isTraining()) {
            float progress = skill.getTrainingProgress();
            String progressText = String.format("⏱️ Тренировка: %.1f%% (%s)", progress * 100, skill.getFormattedTimeLeft());
            
            // Анимированный цвет для активной тренировки
            long time = System.currentTimeMillis() - animationStartTime;
            float pulse = (float) (0.7 + 0.3 * Math.sin(time * 0.005));
            int animatedColor = blendColors(EVE_BLUE, 0xFFFFFFFF, pulse);
            
            graphics.drawString(this.font, progressText, x + 15, y + 65, animatedColor);

            // Прогресс-бар в стиле EVE Online
            renderEveProgressBar(graphics, x + 15, y + 85, 400, 12, progress, skill.getColor());
            
        } else if (skill.canTrain()) {
            String trainText = "📈 Нажмите T для тренировки (" + skill.getFormattedDuration() + ")";
            graphics.drawString(this.font, trainText, x + 15, y + 65, EVE_ORANGE);
            
            // Подсказка
            graphics.drawString(this.font, "Или используйте кнопку 'Тренировать'", x + 15, y + 80, EVE_GRAY);
            
        } else if (skill.currentLevel >= skill.maxLevel) {
            graphics.drawString(this.font, "✅ Максимальный уровень достигнут", x + 15, y + 65, EVE_GREEN);
        }

        // Кнопка тренировки (если можно тренировать)
        if (skill.canTrain()) {
            int buttonX = x + width - 130;
            int buttonY = y + 75;
            int buttonWidth = 110;
            int buttonHeight = 25;
            
            // Проверяем, наведена ли мышь на кнопку
            boolean isHovered = mouseX >= buttonX && mouseX <= buttonX + buttonWidth && 
                              mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
            
            // Фон кнопки в стиле EVE Online
            int buttonColor = isHovered ? 0xA0FF6600 : 0x80002B49;
            graphics.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, buttonColor);
            
            // Рамка кнопки
            int borderColor = isHovered ? EVE_ORANGE : EVE_BLUE;
            graphics.fill(buttonX - 1, buttonY - 1, buttonX + buttonWidth + 1, buttonY, borderColor);
            graphics.fill(buttonX - 1, buttonY + buttonHeight, buttonX + buttonWidth + 1, buttonY + buttonHeight + 1, borderColor);
            graphics.fill(buttonX - 1, buttonY - 1, buttonX, buttonY + buttonHeight + 1, borderColor);
            graphics.fill(buttonX + buttonWidth, buttonY - 1, buttonX + buttonWidth + 1, buttonY + buttonHeight + 1, borderColor);
            
            // Текст кнопки
            String buttonText = "Тренировать";
            int textWidth = this.font.width(buttonText);
            graphics.drawString(this.font, buttonText, buttonX + (buttonWidth - textWidth) / 2, buttonY + 8, 0xFFFFFFFF);
        }
    }
    
    /**
     * Отрисовка прогресс-бара в стиле EVE Online
     */
    private void renderEveProgressBar(GuiGraphics graphics, int x, int y, int width, int height, float progress, int baseColor) {
        // Фон прогресс-бара
        graphics.fill(x, y, x + width, y + height, 0xFF33333);
        
        // Рамка прогресс-бара
        graphics.fill(x - 1, y - 1, x + width + 1, y, EVE_BLUE);
        graphics.fill(x - 1, y + height, x + width + 1, y + height + 1, EVE_BLUE);
        graphics.fill(x - 1, y - 1, x, y + height + 1, EVE_BLUE);
        graphics.fill(x + width, y - 1, x + width + 1, y + height + 1, EVE_BLUE);
        
        // Заполнение прогресс-бара с градиентом
        int fillWidth = (int) (width * progress);
        if (fillWidth > 0) {
            int darkColor = darkenColor(baseColor, 0.5f);
            
            for (int px = 0; px < fillWidth; px++) {
                float ratio = (float) px / fillWidth;
                int blendedColor = blendColors(darkColor, baseColor, ratio);
                graphics.fill(x + px, y, x + px + 1, y + height, blendedColor);
            }
        }
        
        // Процент в центре прогресс-бара
        String percentText = String.format("%.1f%%", progress * 100);
        int textWidth = this.font.width(percentText);
        graphics.drawString(this.font, percentText, x + (width - textWidth) / 2, y + 2, 0xFFFFFFFF);
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
            graphics.drawCenteredString(this.font, scrollInfo, this.width / 2, this.height - 60, EVE_GRAY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Обработка кликов по кнопкам тренировки
        if (dataLoaded && !skills.isEmpty()) {
            int startY = 80;
            int skillHeight = 120;
            int skillWidth = 550;
            int x = (this.width - skillWidth) / 2;
            
            int endIndex = Math.min(scrollOffset + maxVisibleSkills, skills.size());
            
            for (int i = scrollOffset; i < endIndex; i++) {
                ServerSkill skill = skills.get(i);
                if (!skill.canTrain()) continue;
                
                int y = startY + (i - scrollOffset) * skillHeight;
                int buttonX = x + skillWidth - 130;
                int buttonY = y + 75;
                int buttonWidth = 110; // Исправлено: было 10, теперь 110
                int buttonHeight = 25;
                
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
                    statusMessage = "✅ Загружено " + skills.size() + " скилов. Используйте T для тренировки";
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
        
        LOGGER.info("EveStyleSkillsScreen closed, connections terminated");
    }

    @Override
    public boolean isPauseScreen() {
        return false; // Не ставим игру на паузу
    }
}