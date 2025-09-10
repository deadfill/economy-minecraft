package com.example;

import com.example.debug.DatabaseTestCommands;
import com.example.gui.VanillaSkillsScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public class TemplateModClient implements ClientModInitializer {
    
    private static KeyMapping skillsKey;

    @Override
    public void onInitializeClient() {
        // Регистрируем клавишу для открытия скиллов
        skillsKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.template-mod.skills",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K, // Клавиша K
            "category.template-mod.general"
        ));

        // Обработчик нажатия клавиши
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (skillsKey.consumeClick()) {
                Minecraft.getInstance().setScreen(new VanillaSkillsScreen());
            }
        });

        // Регистрируем тестовые команды для БД
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            DatabaseTestCommands.register(dispatcher);
        });
    }
}