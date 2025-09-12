package com.example.gui.owo;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/**
 * Менеджер GUI в стиле EVE Online
 * Управляет открытием экранов скиллов и производства
 */
public class EveStyleGuiManager {
    private static KeyMapping skillsKeyBinding;
    private static KeyMapping productionKeyBinding;
    
    public static void initialize() {
        // Регистрация горячих клавиш
        registerKeyBindings();
        
        // Регистрация обработчика тиков клиента
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Проверяем нажатия горячих клавиш
            while (skillsKeyBinding.consumeClick()) {
                openSkillsScreen();
            }
            
            while (productionKeyBinding.consumeClick()) {
                openProductionScreen();
            }
        });
    }
    
    /**
     * Регистрация горячих клавиш
     */
    private static void registerKeyBindings() {
        // Горячая клавиша для открытия экрана скиллов (по умолчанию H)
        skillsKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.eve.skills", // Идентификатор перевода
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "category.eve.gui" // Категория для меню настроек
        ));
        
        // Горячая клавиша для открытия экрана производства (по умолчанию J)
        productionKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.eve.production", // Идентификатор перевода
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            "category.eve.gui" // Категория для меню настроек
        ));
    }
    
    /**
     * Открыть экран скиллов в стиле EVE Online
     */
    public static void openSkillsScreen() {
        Minecraft.getInstance().setScreen(new EveStyleSkillsScreen());
    }
    
    /**
     * Открыть экран производства в стиле EVE Online
     */
    public static void openProductionScreen() {
        Minecraft.getInstance().setScreen(new EveStyleProductionScreen());
    }
    
    /**
     * Открыть главное меню EVE Online стиля
     */
    public static void openMainMenu() {
        Minecraft.getInstance().setScreen(new EveStyleMainMenu());
    }
}