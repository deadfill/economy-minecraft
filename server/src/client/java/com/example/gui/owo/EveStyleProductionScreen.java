package com.example.gui.owo;

import com.example.skill.EconomyApiClient;
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
 * Экран системы производства в стиле EVE Online
 * Реализация без внешних библиотек
 */
public class EveStyleProductionScreen extends Screen {
    private static final Logger LOGGER = Logger.getLogger(EveStyleProductionScreen.class.getName());
    
    // Цветовая схема EVE Online
    private static final int EVE_BLUE = 0xFF00A0E0;
    private static final int EVE_DARK_BLUE = 0xFF002B49;
    private static final int EVE_VERY_DARK_BLUE = 0xFF001429;
    private static final int EVE_ORANGE = 0xFFFF6600;
    private static final int EVE_GREEN = 0xFF00CC00;
    private static final int EVE_RED = 0xFFFF3300;
    private static final int EVE_GRAY = 0xFFAAAAAA;
    
    private final EconomyApiClient apiClient;
    private final List<ProductionLine> productionLines;
    private boolean dataLoaded = false;
    private String statusMessage = "🔄 Загрузка данных...";
    private int scrollOffset = 0;
    private final int maxVisibleLines = 3;
    private long lastUpdateTime = 0;
    
    // Анимация пульсации
    private long animationStartTime = System.currentTimeMillis();

    // Модель производственной линии
    public static class ProductionLine {
        public String id;
        public String name;
        public String status; // "ACTIVE", "INACTIVE", "PROCESSING"
        public String recipeName;
        public int progress; // 0-100
        public List<String> inputResources;
        public List<String> outputResources;
        public long timeLeftMs;
        
        public ProductionLine(String id, String name, String status, String recipeName, 
                             int progress, List<String> inputResources, List<String> outputResources, 
                             long timeLeftMs) {
            this.id = id;
            this.name = name;
            this.status = status;
            this.recipeName = recipeName;
            this.progress = progress;
            this.inputResources = inputResources;
            this.outputResources = outputResources;
            this.timeLeftMs = timeLeftMs;
        }
        
        public String getIcon() {
            return "⚙️";
        }
        
        public int getColor() {
            return switch (status) {
                case "ACTIVE" -> EVE_GREEN;
                case "PROCESSING" -> EVE_ORANGE;
                case "INACTIVE" -> EVE_GRAY;
                default -> EVE_RED;
            };
        }
        
        public String getStatusDescription() {
            return switch (status) {
                case "ACTIVE" -> "Активна";
                case "PROCESSING" -> "В процессе";
                case "INACTIVE" -> "Неактивна";
                default -> "Неизвестно";
            };
        }
        
        public String getFormattedTimeLeft() {
            if (timeLeftMs <= 0) return "Готово!";
            
            long seconds = timeLeftMs / 1000;
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
    }

    public EveStyleProductionScreen() {
        super(Component.literal("Производственные линии EVE Online"));
        this.apiClient = new EconomyApiClient();
        this.productionLines = new ArrayList<>();
        this.lastUpdateTime = System.currentTimeMillis();
        this.animationStartTime = System.currentTimeMillis();
    }

    @Override
    protected void init() {
        super.init();
        
        // Кнопка "Обновить" 
        this.addRenderableWidget(Button.builder(
            Component.literal("🔄 Обновить"),
            button -> {
                playClickSound();
                loadProductionData();
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
                if (scrollOffset < Math.max(0, productionLines.size() - maxVisibleLines)) {
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
            loadProductionData();
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

        if (dataLoaded && !productionLines.isEmpty()) {
            renderProductionLines(graphics, mouseX, mouseY);
            renderScrollInfo(graphics);
        } else if (dataLoaded) {
            graphics.drawCenteredString(this.font, "❌ Производственные линии не найдены", this.width / 2, 80, EVE_RED);
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
        graphics.drawCenteredString(this.font, "⚙️ Производственные линии", this.width / 2, 20, EVE_BLUE);
        
        // Подзаголовок
        graphics.drawCenteredString(this.font, "Управляйте производством ресурсов", this.width / 2, 35, EVE_GRAY);
        
        // Декоративная линия
        graphics.fill(this.width / 2 - 200, 45, this.width / 2 + 200, 46, EVE_BLUE);
    }

    /**
     * Отрисовка производственных линий в стиле EVE Online
     */
    private void renderProductionLines(GuiGraphics graphics, int mouseX, int mouseY) {
        int startY = 80;
        int lineHeight = 140;
        int lineWidth = 600;
        int x = (this.width - lineWidth) / 2;

        // Показываем только часть линий в зависимости от прокрутки
        int endIndex = Math.min(scrollOffset + maxVisibleLines, productionLines.size());
        
        for (int i = scrollOffset; i < endIndex; i++) {
            ProductionLine line = productionLines.get(i);
            int y = startY + (i - scrollOffset) * lineHeight;

            // Фон панели производственной линии с эффектом EVE Online
            renderEveProductionPanel(graphics, x, y, lineWidth, lineHeight, line, mouseX, mouseY);
        }
    }
    
    /**
     * Отрисовка панели производственной линии в стиле EVE Online
     */
    private void renderEveProductionPanel(GuiGraphics graphics, int x, int y, int width, int height, ProductionLine line, int mouseX, int mouseY) {
        // Фон панели с градиентом
        for (int i = 0; i < height - 10; i++) {
            float ratio = (float) i / (height - 10);
            int color = blendColors(0x80001429, 0x40002B49, ratio);
            graphics.fill(x, y + i, x + width, y + i + 1, color);
        }
        
        // Полупрозрачная рамка
        graphics.fill(x - 2, y - 2, x + width + 2, y, line.getColor());
        graphics.fill(x - 2, y + height - 12, x + width + 2, y + height - 10, line.getColor());
        graphics.fill(x - 2, y - 2, x, y + height - 10, line.getColor());
        graphics.fill(x + width, y - 2, x + width + 2, y + height - 10, line.getColor());
        
        // Название и статус
        String titleText = line.getIcon() + " " + line.name;
        graphics.drawString(this.font, titleText, x + 15, y + 10, EVE_BLUE);
        
        String statusText = "Статус: " + line.getStatusDescription();
        graphics.drawString(this.font, statusText, x + width - 15 - this.font.width(statusText), y + 10, line.getColor());

        // Рецепт
        if (line.recipeName != null && !line.recipeName.isEmpty()) {
            graphics.drawString(this.font, "Рецепт: " + line.recipeName, x + 15, y + 30, EVE_GRAY);
        }

        // Прогресс производства
        if (line.status.equals("PROCESSING")) {
            String progressText = String.format("Прогресс: %d%% (%s)", line.progress, line.getFormattedTimeLeft());
            
            // Анимированный цвет для активного производства
            long time = System.currentTimeMillis() - animationStartTime;
            float pulse = (float) (0.7 + 0.3 * Math.sin(time * 0.005));
            int animatedColor = blendColors(EVE_BLUE, 0xFFFFFFFF, pulse);
            
            graphics.drawString(this.font, progressText, x + 15, y + 50, animatedColor);

            // Прогресс-бар в стиле EVE Online
            renderEveProgressBar(graphics, x + 15, y + 70, 450, 12, (float) line.progress / 100, line.getColor());
        } else {
            graphics.drawString(this.font, "Статус: " + line.getStatusDescription(), x + 15, y + 50, line.getColor());
        }

        // Входящие ресурсы
        if (line.inputResources != null && !line.inputResources.isEmpty()) {
            graphics.drawString(this.font, "Входящие:", x + 15, y + 90, EVE_GRAY);
            for (int i = 0; i < Math.min(3, line.inputResources.size()); i++) {
                graphics.drawString(this.font, "  • " + line.inputResources.get(i), x + 15, y + 105 + i * 12, EVE_GREEN);
            }
            if (line.inputResources.size() > 3) {
                graphics.drawString(this.font, "  • и еще " + (line.inputResources.size() - 3) + " ресурсов", x + 15, y + 141, EVE_GRAY);
            }
        }

        // Исходящие ресурсы
        if (line.outputResources != null && !line.outputResources.isEmpty()) {
            graphics.drawString(this.font, "Исходящие:", x + 300, y + 90, EVE_GRAY);
            for (int i = 0; i < Math.min(3, line.outputResources.size()); i++) {
                graphics.drawString(this.font, "  • " + line.outputResources.get(i), x + 300, y + 105 + i * 12, EVE_ORANGE);
            }
            if (line.outputResources.size() > 3) {
                graphics.drawString(this.font, "  • и еще " + (line.outputResources.size() - 3) + " ресурсов", x + 300, y + 141, EVE_GRAY);
            }
        }

        // Кнопки управления
        int buttonX = x + width - 130;
        int buttonY = y + 100;
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
        String buttonText = line.status.equals("ACTIVE") || line.status.equals("PROCESSING") ? "Остановить" : "Запустить";
        int textWidth = this.font.width(buttonText);
        graphics.drawString(this.font, buttonText, buttonX + (buttonWidth - textWidth) / 2, buttonY + 8, 0xFFFFFFFF);
    }
    
    /**
     * Отрисовка прогресс-бара в стиле EVE Online
     */
    private void renderEveProgressBar(GuiGraphics graphics, int x, int y, int width, int height, float progress, int baseColor) {
        // Фон прогресс-бара
        graphics.fill(x, y, x + width, y + height, 0xFF3333);
        
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
        String percentText = String.format("%.0f%%", progress * 100);
        int textWidth = this.font.width(percentText);
        graphics.drawString(this.font, percentText, x + (width - textWidth) / 2, y + 2, 0xFFFFFFFF);
    }

    /**
     * Отрисовка информации о прокрутке
     */
    private void renderScrollInfo(GuiGraphics graphics) {
        if (productionLines.size() > maxVisibleLines) {
            String scrollInfo = String.format("Показано %d-%d из %d линий", 
                scrollOffset + 1, 
                Math.min(scrollOffset + maxVisibleLines, productionLines.size()), 
                productionLines.size());
            graphics.drawCenteredString(this.font, scrollInfo, this.width / 2, this.height - 60, EVE_GRAY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Обработка кликов по кнопкам управления
        if (dataLoaded && !productionLines.isEmpty()) {
            int startY = 80;
            int lineHeight = 140;
            int lineWidth = 600;
            int x = (this.width - lineWidth) / 2;
            
            int endIndex = Math.min(scrollOffset + maxVisibleLines, productionLines.size());
            
            for (int i = scrollOffset; i < endIndex; i++) {
                ProductionLine line = productionLines.get(i);
                
                int y = startY + (i - scrollOffset) * lineHeight;
                int buttonX = x + lineWidth - 130;
                int buttonY = y + 100;
                int buttonWidth = 110;
                int buttonHeight = 25;
                
                if (mouseX >= buttonX && mouseX <= buttonX + buttonWidth && 
                    mouseY >= buttonY && mouseY <= buttonY + buttonHeight) {
                    playClickSound();
                    toggleProductionLine(line);
                    return true;
                }
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Загрузить данные производственных линий с сервера
     */
    private void loadProductionData() {
        statusMessage = "🔄 Загрузка данных...";
        dataLoaded = false;
        scrollOffset = 0;

        UUID playerUuid = apiClient.getCurrentPlayerUuid();
        if (playerUuid == null) {
            statusMessage = "❌ Игрок не найден";
            return;
        }

        // Загружаем список производственных линий
        // В реальной реализации здесь будет вызов API
        // Пока используем тестовые данные
        loadTestProductionData();
    }
    
    /**
     * Загрузка тестовых данных для демонстрации
     */
    private void loadTestProductionData() {
        productionLines.clear();
        
        // Добавляем тестовые производственные линии
        List<String> input1 = new ArrayList<>();
        input1.add("Железная руда x10");
        input1.add("Уголь x5");
        
        List<String> output1 = new ArrayList<>();
        output1.add("Железные слитки x5");
        
        productionLines.add(new ProductionLine(
            "line1", 
            "Доменная печь", 
            "PROCESSING", 
            "Выплавка железа", 
            65, 
            input1, 
            output1, 
            3600000 // 1 час
        ));
        
        List<String> input2 = new ArrayList<>();
        input2.add("Дерево x20");
        input2.add("Железные слитки x2");
        
        List<String> output2 = new ArrayList<>();
        output2.add("Деревянный стол x1");
        output2.add("Доски x10");
        
        productionLines.add(new ProductionLine(
            "line2", 
            "Столярная мастерская", 
            "ACTIVE", 
            "Изготовление мебели", 
            0, 
            input2, 
            output2, 
            0
        ));
        
        List<String> input3 = new ArrayList<>();
        input3.add("Камень x15");
        input3.add("Железные слитки x3");
        
        List<String> output3 = new ArrayList<>();
        output3.add("Каменная печь x1");
        output3.add("Булыжник x10");
        
        productionLines.add(new ProductionLine(
            "line3", 
            "Каменотесная мастерская", 
            "INACTIVE", 
            "", 
            0, 
            input3, 
            output3, 
            0
        ));
        
        dataLoaded = true;
        statusMessage = "✅ Загружено " + productionLines.size() + " производственных линий";
    }

    /**
     * Переключить состояние производственной линии
     */
    private void toggleProductionLine(ProductionLine line) {
        playClickSound();
        statusMessage = "🔄 Переключаем " + line.name + "...";
        
        // В реальной реализации здесь будет вызов API
        // Пока просто меняем статус для демонстрации
        if (line.status.equals("ACTIVE") || line.status.equals("PROCESSING")) {
            line.status = "INACTIVE";
            statusMessage = "✅ " + line.name + " остановлена";
        } else {
            line.status = "ACTIVE";
            statusMessage = "✅ " + line.name + " запущена";
        }
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
        // Закрываем API клиент
        apiClient.close();
        super.onClose();
        
        LOGGER.info("EveStyleProductionScreen closed");
    }

    @Override
    public boolean isPauseScreen() {
        return false; // Не ставим игру на паузу
    }
}