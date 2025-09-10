package com.example.economy.core;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.*;

@ApplicationScoped
public class RecipeRegistry {
    public static final class Input {
        public final String itemId;
        public final long qty;
        public Input(String itemId, long qty) { this.itemId = itemId; this.qty = qty; }
    }
    public static final class Recipe {
        public final String id;          // "demo:diamond"
        public final String tag;         // "industry" (под этот тег вешаем бонусы)
        public final long baseDurationMs;
        public final List<Input> inputs; // базовые входы без бонусов
        public Recipe(String id, String tag, long baseDurationMs, List<Input> inputs) {
            this.id=id; this.tag=tag; this.baseDurationMs=baseDurationMs; this.inputs = List.copyOf(inputs);
        }
    }

    private final Map<String, Recipe> byId;
    public RecipeRegistry() {
        Map<String, Recipe> m = new LinkedHashMap<>();

        // === Пример тестового рецепта ===
        m.put("demo:diamond", new Recipe(
                "demo:diamond",
                "industry",                  // на него попадут бонусы skill.kind='tag' target='industry'
                5_000L,
                List.of( new Input("ore.iron", 10) )  // по умолчанию надо 10 руды
        ));

        byId = Collections.unmodifiableMap(m);
    }

    public Recipe get(String id) { return byId.get(id); }
    public boolean exists(String id) { return byId.containsKey(id); }
}
