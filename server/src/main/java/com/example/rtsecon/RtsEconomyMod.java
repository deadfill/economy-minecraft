package com.example.rtsecon;

import com.example.rtsecon.http.EconHttp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Nats;
import io.nats.client.Options;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RtsEconomyMod implements ModInitializer {
    // AUTHED теперь управляется SimpleAuth модом

    // === NATS ===
    private static volatile Connection NATS = null;
    private static volatile boolean NATS_CONNECTED = false;
    private static Dispatcher NATS_DISP;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ConcurrentLinkedQueue<Notif> NOTIFS = new ConcurrentLinkedQueue<>();
    // Дедупликация сообщений (храним последние 100 сообщений на 30 секунд)
    private static final Map<String, Long> MESSAGE_DEDUP = new ConcurrentHashMap<>();

    private static class Notif {
        final UUID owner; final String text;
        Notif(UUID owner, String text) { this.owner = owner; this.text = text; }
    }

    @Override
    public void onInitialize() {
        // Авторизация теперь управляется SimpleAuth модом

        // выдать чат-уведомления из очереди (главный тред сервера)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Notif nf;
            while ((nf = NOTIFS.poll()) != null) {
                ServerPlayer p = server.getPlayerList().getPlayer(nf.owner);
                if (p != null) { // SimpleAuth проверяет авторизацию автоматически
                    p.sendSystemMessage(Component.literal(nf.text).withStyle(ChatFormatting.AQUA));
                }
            }
        });

        // закрытие NATS при остановке сервера
        ServerLifecycleEvents.SERVER_STOPPING.register(srv -> {
            try { if (NATS_DISP != null) NATS_DISP.unsubscribe("econ.production.done"); } catch (Exception ignored) {}
            try { if (NATS_DISP != null) NATS_DISP.unsubscribe("econ.production.claimed"); } catch (Exception ignored) {}
            try { if (NATS_DISP != null) NATS_DISP.unsubscribe("econ.skill.done"); } catch (Exception ignored) {}
            try { if (NATS != null) NATS.close(); } catch (Exception ignored) {}
            NATS = null;
            NATS_CONNECTED = false;
        });

        // Подключаемся к NATS и подписываемся
        connectNatsOnce();

        // команды
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> {

            // Команды /register и /login теперь предоставляются SimpleAuth модом

            // ===== SKILL =====
            dispatcher.register(Commands.literal("skill")
                    .then(Commands.literal("train")
                            .then(Commands.argument("id", StringArgumentType.string())
                                    .executes(ctx -> {
                                        ServerPlayer p = ctx.getSource().getPlayerOrException();
                                        if (!requireAuth(p)) return 0;
                                        String skillId = StringArgumentType.getString(ctx, "id");
                                        try {
                                            System.out.println("[EconMod] Starting skill training: " + skillId + " for " + p.getUUID());
                                            JsonNode res = EconHttp.trainSkill(p.getUUID(), skillId);
                                            System.out.println("[EconMod] Skill training response: " + res);
                                            long left = res.get("endMs").asLong() - System.currentTimeMillis();
                                            p.sendSystemMessage(Component.literal(
                                                            "⏳ Тренировка '" + skillId + "' → L" + res.get("targetLevel").asInt()
                                                                    + " (~" + Math.max(0, left/1000) + "с)")
                                                    .withStyle(ChatFormatting.AQUA));
                                        } catch (Exception e) {
                                            System.out.println("[EconMod] Skill training error: " + e.getMessage());
                                            e.printStackTrace();
                                            p.sendSystemMessage(Component.literal("Ошибка тренировки: " + e.getMessage())
                                                    .withStyle(ChatFormatting.RED));
                                        }
                                        return 1;
                                    })))
                    .then(Commands.literal("status")
                            .executes(ctx -> {
                                ServerPlayer p = ctx.getSource().getPlayerOrException();
                                if (!requireAuth(p)) return 0;
                                try {
                                    JsonNode st = EconHttp.skillStatus(p.getUUID()).path("current");
                                    if (st.isMissingNode() || st.isNull()) {
                                        p.sendSystemMessage(Component.literal("Навыки: сейчас не тренируются.")
                                                .withStyle(ChatFormatting.GRAY));
                                    } else {
                                        long left = st.get("endMs").asLong() - System.currentTimeMillis();
                                        p.sendSystemMessage(Component.literal(
                                                        "🧠 " + st.get("skillId").asText()
                                                                + " → L" + st.get("targetLevel").asInt()
                                                                + " осталось ~" + Math.max(0, left/1000) + "с")
                                                .withStyle(ChatFormatting.YELLOW));
                                    }
                                } catch (Exception e) {
                                    p.sendSystemMessage(Component.literal("Ошибка статуса: " + e.getMessage())
                                            .withStyle(ChatFormatting.RED));
                                }
                                return 1;
                            }))
                    .then(Commands.literal("list")
                            .executes(ctx -> {
                                ServerPlayer p = ctx.getSource().getPlayerOrException();
                                if (!requireAuth(p)) return 0;
                                try {
                                    var skills = EconHttp.fetchSkills();
                                    if (skills.isEmpty()) {
                                        p.sendSystemMessage(Component.literal("Скилы недоступны.")
                                                .withStyle(ChatFormatting.GRAY));
                                    } else {
                                        p.sendSystemMessage(Component.literal("Доступные навыки:")
                                                .withStyle(ChatFormatting.GOLD));
                                        for (var s : skills) {
                                            p.sendSystemMessage(Component.literal(
                                                    "• " + s.id + " — " + s.title + " (макс L" + s.maxLevel + ")"
                                            ).withStyle(ChatFormatting.YELLOW));
                                        }
                                        p.sendSystemMessage(Component.literal(
                                                "Подробно: /skill train <id>  |  /skill status"
                                        ).withStyle(ChatFormatting.DARK_GRAY));
                                    }
                                } catch (Exception e) {
                                    p.sendSystemMessage(Component.literal("Ошибка загрузки списка: " + e.getMessage())
                                            .withStyle(ChatFormatting.RED));
                                }
                                return 1;
                            }))
                    .then(Commands.literal("refresh")
                            .executes(ctx -> {
                                ServerPlayer p = ctx.getSource().getPlayerOrException();
                                if (!requireAuth(p)) return 0;
                                try {
                                    EconHttp.invalidateSkillsCache();
                                    EconHttp.fetchSkills();
                                    p.sendSystemMessage(Component.literal("Кэш списка навыков обновлён.")
                                            .withStyle(ChatFormatting.GREEN));
                                } catch (Exception e) {
                                    p.sendSystemMessage(Component.literal("Ошибка обновления: " + e.getMessage())
                                            .withStyle(ChatFormatting.RED));
                                }
                                return 1;
                            }))
                    .then(Commands.literal("clear-cache")
                            .executes(ctx -> {
                                ServerPlayer p = ctx.getSource().getPlayerOrException();
                                if (!requireAuth(p)) return 0;
                                try {
                                    // Очищаем клиентский кэш уровней навыков
                                    EconHttp.clearAllSkillLevelCache();
                                    p.sendSystemMessage(Component.literal("Клиентский кэш навыков очищен.")
                                            .withStyle(ChatFormatting.GREEN));
                                } catch (Exception e) {
                                    p.sendSystemMessage(Component.literal("Ошибка: " + e.getMessage())
                                            .withStyle(ChatFormatting.RED));
                                }
                                return 1;
                            }))
                    .then(Commands.literal("level")
                            .then(Commands.argument("skillId", StringArgumentType.string())
                                    .executes(ctx -> {
                                        ServerPlayer p = ctx.getSource().getPlayerOrException();
                                        if (!requireAuth(p)) return 0;
                                        String skillId = StringArgumentType.getString(ctx, "skillId");
                                        try {
                                            JsonNode res = EconHttp.skillLevel(p.getUUID(), skillId);
                                            int level = res.get("level").asInt();
                                            // Рассчитываем скидку для наглядности
                                            int discount = Math.min(25, level * 5); // 5% за уровень, макс 25%
                                            p.sendSystemMessage(Component.literal(
                                                    "🎯 " + skillId + ": уровень " + level + " (скидка -" + discount + "%)")
                                                    .withStyle(ChatFormatting.AQUA));
                                        } catch (Exception e) {
                                            p.sendSystemMessage(Component.literal("Ошибка: " + e.getMessage())
                                                    .withStyle(ChatFormatting.RED));
                                        }
                                        return 1;
                                    })))
            );

            // ===== ECO =====
            dispatcher.register(Commands.literal("eco")
                    .then(Commands.literal("debug")
                            .then(Commands.literal("add-iron")
                                    .then(Commands.argument("qty", IntegerArgumentType.integer(1, 1000))
                                            .executes(ctx -> {
                                                ServerPlayer p = ctx.getSource().getPlayerOrException();
                                                if (!requireAuth(p)) return 0;
                                                int qty = IntegerArgumentType.getInteger(ctx, "qty");
                                                try {
                                                    // Вызываем debug API для добавления материалов
                                                    String url = System.getProperty("econ.base", "http://localhost:8081") 
                                                            + "/api/v1/debug/add-material?ownerUuid=" + p.getUUID() 
                                                            + "&itemId=ore.iron&qty=" + qty;
                                                    var client = java.net.http.HttpClient.newHttpClient();
                                                    var request = java.net.http.HttpRequest.newBuilder()
                                                            .uri(java.net.URI.create(url))
                                                            .timeout(java.time.Duration.ofSeconds(5))
                                                            .POST(java.net.http.HttpRequest.BodyPublishers.noBody())
                                                            .build();
                                                    var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                                                    
                                                    if (response.statusCode() == 200) {
                                                        p.sendSystemMessage(Component.literal("✅ Добавлено " + qty + " железной руды")
                                                                .withStyle(ChatFormatting.GREEN));
                                                    } else {
                                                        p.sendSystemMessage(Component.literal("❌ Ошибка добавления: " + response.body())
                                                                .withStyle(ChatFormatting.RED));
                                                    }
                                                } catch (Exception ex) {
                                                    p.sendSystemMessage(Component.literal("❌ Ошибка: " + ex.getMessage())
                                                            .withStyle(ChatFormatting.RED));
                                                }
                                                return 1;
                                            }))))
                    .then(Commands.literal("start")
                            .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                    .executes(ctx -> {
                                        ServerPlayer p = ctx.getSource().getPlayerOrException();
                                        if (!requireAuth(p)) return 0;
                                        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
                                        try {
                                            JsonNode res = EconHttp.startProduction(p.getUUID(), "demo:diamond", seconds);
                                            String jobId = res.get("jobId").asText();
                                            p.sendSystemMessage(Component.literal(
                                                            "Запущено: " + jobId + " (готово через " + seconds + "с)")
                                                    .withStyle(ChatFormatting.GREEN));
                                        } catch (Exception ex) {
                                            p.sendSystemMessage(Component.literal("Ошибка сервиса: " + ex.getMessage())
                                                    .withStyle(ChatFormatting.RED));
                                        }
                                        return 1;
                                    })))
                    .then(Commands.literal("list")
                            .executes(ctx -> {
                                ServerPlayer p = ctx.getSource().getPlayerOrException();
                                if (!requireAuth(p)) return 0;
                                try {
                                    var list = EconHttp.listJobs(p.getUUID());
                                    if (list.isEmpty()) {
                                        p.sendSystemMessage(Component.literal("Нет заданий.")
                                                .withStyle(ChatFormatting.GRAY));
                                    } else {
                                        long now = System.currentTimeMillis();
                                        for (var j : list) {
                                            long left = Math.max(0, j.endMs - now);
                                            p.sendSystemMessage(Component.literal(
                                                            "• " + j.jobId + " [" + j.status + "] ETA " + (left / 1000) + "с")
                                                    .withStyle(ChatFormatting.YELLOW));
                                        }
                                    }
                                } catch (Exception ex) {
                                    p.sendSystemMessage(Component.literal("Ошибка сервиса: " + ex.getMessage())
                                            .withStyle(ChatFormatting.RED));
                                }
                                return 1;
                            }))
                    .then(Commands.literal("claim")
                            .executes(ctx -> {
                                ServerPlayer p = ctx.getSource().getPlayerOrException();
                                if (!requireAuth(p)) return 0;
                                try {
                                    int n = EconHttp.claim(p.getUUID());
                                    if (n > 0) {
                                        p.addItem(new ItemStack(Items.DIAMOND, n));
                                        p.sendSystemMessage(Component.literal("Выдано алмазов: " + n)
                                                .withStyle(ChatFormatting.GREEN));
                                    } else {
                                        p.sendSystemMessage(Component.literal("Наград нет.")
                                                .withStyle(ChatFormatting.GRAY));
                                    }
                                } catch (Exception ex) {
                                    p.sendSystemMessage(Component.literal("Ошибка сервиса: " + ex.getMessage())
                                            .withStyle(ChatFormatting.RED));
                                }
                                return 1;
                            }))
            );
        });
    }

    private static boolean requireAuth(ServerPlayer p) {
        // SimpleAuth автоматически проверяет авторизацию
        // Неавторизованные игроки не могут выполнять команды
        return true;
    }

    private static void freeze(ServerPlayer p, boolean freeze) {
        if (freeze) {
            p.setGameMode(GameType.ADVENTURE);
            p.setDeltaMovement(0, 0, 0);
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 255, false, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, 255, false, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false, false));
        } else {
            p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            p.removeEffect(MobEffects.DIG_SLOWDOWN);
            p.removeEffect(MobEffects.BLINDNESS);
            p.setGameMode(GameType.SURVIVAL);
        }
    }

    // ——— NATS ———

    private static synchronized void connectNatsOnce() {
        if (NATS_CONNECTED) return;
        try {
            String url = System.getProperty("nats.url", "nats://localhost:4222");
            Options opts = new Options.Builder()
                    .server(url)
                    .connectionName("mc-server")
                    .maxReconnects(-1)
                    .reconnectWait(Duration.ofSeconds(2))
                    .pingInterval(Duration.ofSeconds(10))
                    .build();
            NATS = Nats.connect(opts);

            NATS_DISP = NATS.createDispatcher(msg -> {
                try {
                    String subj = msg.getSubject();
                    JsonNode node = JSON.readTree(new String(msg.getData(), StandardCharsets.UTF_8));

                    // owner может отсутствовать — не падаем
                    UUID owner = null;
                    if (node.hasNonNull("owner")) {
                        String os = node.get("owner").asText("");
                        if (!os.isEmpty()) {
                            try { owner = UUID.fromString(os); } catch (Exception ignored) {}
                        }
                    }
                    if (owner == null) return;

                    if ("econ.production.done".equals(subj)) {
                        String jobId = node.path("jobId").asText("");
                        NOTIFS.add(new Notif(owner, "⛏ Производство завершено! /eco claim (job " + shortId(jobId) + ")"));
                    } else if ("econ.production.claimed".equals(subj)) {
                        int cnt = node.path("claimed").asInt(0); // сервер отправляет поле "claimed"
                        if (cnt > 0) {
                            NOTIFS.add(new Notif(owner, "✅ Получено наград: " + cnt));
                        }
                    } else if ("econ.skill.done".equals(subj)) {
                        String skill = node.path("skill").asText("");
                        int lvl = node.path("level").asInt(0);
                        
                        // Дедупликация: создаем уникальный ключ для этого события
                        String dedupKey = owner + "|skill|" + skill + "|" + lvl;
                        long now = System.currentTimeMillis();
                        
                        // Проверяем, не было ли такого сообщения в последние 30 секунд
                        Long lastTime = MESSAGE_DEDUP.get(dedupKey);
                        if (lastTime != null && (now - lastTime) < 30_000) {
                            // Дубликат - игнорируем
                            return;
                        }
                        
                        // Сохраняем время этого сообщения
                        MESSAGE_DEDUP.put(dedupKey, now);
                        
                        // Очищаем старые записи (старше 30 секунд)
                        MESSAGE_DEDUP.entrySet().removeIf(entry -> (now - entry.getValue()) > 30_000);
                        
                        // Очищаем кэш уровня навыка после изменения
                        EconHttp.invalidateSkillLevelCache(owner, skill);
                        NOTIFS.add(new Notif(owner, "🧠 Навык " + skill + " достиг уровня " + lvl));
                    }
                } catch (Exception ignored) {}
            });

            // одна точка подписки на все темы
            NATS_DISP.subscribe("econ.production.done");
            NATS_DISP.subscribe("econ.production.claimed");
            NATS_DISP.subscribe("econ.skill.done");

            NATS_CONNECTED = true;
            System.out.println("[EconMod] NATS connected: " + url + " (econ.production.*, econ.skill.done)");
        } catch (Exception e) {
            System.out.println("[EconMod] NATS connect failed: " + e.getMessage());
        }
    }

    private static String shortId(String s) {
        return (s == null || s.length() < 8) ? s : s.substring(0, 8) + "…";
    }
}
