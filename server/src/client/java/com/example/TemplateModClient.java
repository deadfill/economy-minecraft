package com.example;

import com.example.gui.owo.EveStyleGuiManager;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Основной клиентский класс мода
 * Инициализирует систему интерфейса в стиле EVE Online
 */
public class TemplateModClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("template-mod-client");
    
    @Override
    public void onInitializeClient() {
        LOGGER.info("Инициализация клиентской части TemplateMod");
        
        // Инициализация EVE Online интерфейса
        EveStyleGuiManager.initialize();
        
        LOGGER.info("EVE Online интерфейс инициализирован");
    }
}