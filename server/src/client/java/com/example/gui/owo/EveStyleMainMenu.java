package com.example.gui.owo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Главное меню в стиле EVE Online
 * Позволяет переключаться между различными экранами
 */
public class EveStyleMainMenu extends Screen {
    // Цветовая схема EVE Online
    private static final int EVE_BLUE = 0xFF00A0E0;
    private static final int EVE_DARK_BLUE = 0xFF002B49;
    private static final int EVE_VERY_DARK_BLUE = 0xFF001429;
    private static final int EVE_ORANGE = 0xFFFF6600;
    private static final int EVE_GREEN = 0xFF00CC00;
    private static final int EVE_RED = 0xFFFF3300;
    private static final int EVE_GRAY = 0xFFAAAAAA;
    
    private long animationStartTime = System.currentTimeMillis();

    public EveStyleMainMenu() {
        super(Component.literal("Главное меню EVE Online"));
        this.animationStartTime = System.currentTimeMillis();
    }

    @Override
    protected void init() {
        super.init();
        
        int buttonWidth = 200;
        int buttonHeight = 30;
        int centerX = this.width / 2;
        int startY = this.height / 2 - 50;
        
        // Кнопка "Система скиллов"
        this.addRenderableWidget(Button.builder(
            Component.literal("🏭 Система скиллов"),
            button -> {
                playClickSound();
                Minecraft.getInstance().setScreen(new EveStyleSkillsScreen());
            }
        ).bounds(centerX - buttonWidth / 2, startY, buttonWidth, buttonHeight).build());
        
        // Кнопка "Производственные линии"
        this.addRenderableWidget(Button.builder(
            Component.literal("⚙️ Производственные линии"),
            button -> {
                playClickSound();
                Minecraft.getInstance().setScreen(new EveStyleProductionScreen());
            }
        ).bounds(centerX - buttonWidth / 2, startY + 40, buttonWidth, buttonHeight).build());
        
        // Кнопка "Закрыть"
        this.addRenderableWidget(Button.builder(
            Component.literal("❌ Закрыть"),
            button -> {
                playClickSound();
                onClose();
            }
        ).bounds(centerX - buttonWidth / 2, startY + 120, buttonWidth, buttonHeight).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // Рендерим фон в стиле EVE Online
        renderEveBackground(graphics);
        
        // Заголовок в стиле EVE Online
        renderEveHeader(graphics);
        
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
            int y = 50 + i * 30;
            int alpha = (int) (0x33 * (1.0 - pulse * 0.5));
            graphics.fill(0, y, this.width, y + 1, (alpha << 24) | (EVE_BLUE & 0xFFFFFF));
        }
    }
    
    /**
     * Отрисовка заголовка в стиле EVE Online
     */
    private void renderEveHeader(GuiGraphics graphics) {
        // Основной заголовок
        graphics.drawCenteredString(this.font, "🚀 Система управления EVE Online", this.width / 2, 20, EVE_BLUE);
        
        // Подзаголовок
        graphics.drawCenteredString(this.font, "Выберите раздел для управления", this.width / 2, 35, EVE_GRAY);
        
        // Декоративная линия
        graphics.fill(this.width / 2 - 200, 45, this.width / 2 + 200, 46, EVE_BLUE);
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
    public boolean isPauseScreen() {
        return false; // Не ставим игру на паузу
    }
}