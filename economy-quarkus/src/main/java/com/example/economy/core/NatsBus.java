package com.example.economy.core;

import io.nats.client.*;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@ApplicationScoped
@Startup // гарантируем раннюю инициализацию бина до любых публикаций
public class NatsBus {
    private static final Logger LOG = Logger.getLogger(NatsBus.class.getName());

    @ConfigProperty(name = "app.nats.servers", defaultValue = "nats://localhost:4222")
    String servers;

    @ConfigProperty(name = "app.nats.name", defaultValue = "econ-service")
    String name;

    @ConfigProperty(name = "app.nats.js.stream", defaultValue = "ECON")
    String stream;

    // subject по умолчанию — можно публиковать через publish(json)
    @ConfigProperty(name = "app.nats.js.subject", defaultValue = "econ.production.done")
    String defaultSubject;

    private volatile Connection nc;
    private volatile JetStream js;

    @PostConstruct
    void init() {
        try {
            Options opts = new Options.Builder()
                    .server(servers)
                    .connectionName(name)
                    .maxReconnects(-1)
                    .reconnectWait(Duration.ofSeconds(2))
                    .pingInterval(Duration.ofSeconds(10))
                    .requestCleanupInterval(Duration.ofSeconds(5))
                    .connectionTimeout(Duration.ofSeconds(5))
                    .build();

            nc = Nats.connect(opts);
            LOG.info("Connected to NATS: " + servers);

            // JetStream mgmt может падать если JS выключен — это нормально, мы всё равно сможем core publish-ить
            try {
                JetStreamManagement jsm = nc.jetStreamManagement();
                ensureStreamHasSubjects(jsm, stream, List.of("econ.*", "player.*.skill.training", "player.*.skill.level"));
                js = nc.jetStream();
                LOG.info("JetStream ready: stream=" + stream + ", subjects include econ.*, player.*.skill.training, player.*.skill.level");
            } catch (Exception jsErr) {
                js = null;
                LOG.warning("JetStream is not available now: " + jsErr.getMessage() + " (core publish will be used)");
            }
        } catch (Exception e) {
            throw new RuntimeException("NATS init failed: " + e.getMessage(), e);
        }
    }

    @PreDestroy
    void shutdown() {
        if (nc != null) {
            try { nc.flush(Duration.ofMillis(100)); } catch (Exception ignored) {}
            try { nc.close(); } catch (Exception ignored) {}
        }
    }

    /** Гарантирует существование стрима и наличие всех указанных subject-паттернов. */
    private void ensureStreamHasSubjects(JetStreamManagement jsm, String stream, List<String> needed) throws Exception {
        try {
            StreamInfo info = jsm.getStreamInfo(stream);
            var cfg = info.getConfiguration();
            List<String> subjects = new ArrayList<>(cfg.getSubjects());
            LOG.info("Current stream '" + stream + "' subjects: " + subjects);
            boolean changed = false;
            for (String s : needed) {
                if (!subjects.contains(s)) {
                    subjects.add(s);
                    changed = true;
                }
            }
            if (changed) {
                StreamConfiguration updated = StreamConfiguration.builder(cfg)
                        .subjects(subjects.toArray(new String[0]))
                        .build();
                jsm.updateStream(updated);
                LOG.info("Updated stream '" + stream + "' with subjects " + subjects);
            } else {
                LOG.info("Stream '" + stream + "' already has subjects " + subjects);
            }
        } catch (JetStreamApiException notFound) {
            // создаём с нужными subjects
            StreamConfiguration sc = StreamConfiguration.builder()
                    .name(stream)
                    .subjects(needed.toArray(new String[0]))
                    .storageType(StorageType.File)
                    .retentionPolicy(RetentionPolicy.Limits)
                    .build();
            jsm.addStream(sc);
            LOG.info("Created stream '" + stream + "' with subjects " + needed);
        }
    }

    /**
     * Публикация: сначала пытаемся JetStream (быстро), если таймаут/ошибка — fallback на core publish.
     * Это идеальный вариант для уведомлений (чат/онлайн-реакции), чтобы не зависеть от ACK.
     */
    public void publish(String subject, String json) {
        if (nc == null) {
            LOG.warning("NATS connection is null; dropping subject=" + subject);
            return;
        }
        byte[] data = json.getBytes(StandardCharsets.UTF_8);

        // 1) Быстрая попытка JetStream (если доступен)
        if (js != null) {
            try {
                // Пытаемся "подсказать" стрим и не зависнуть
                PublishOptions po = PublishOptions.builder()
                        .stream(stream)
                        .expectedStream(stream)
                        .build();
                // Более короткий таймаут для быстрого fallback
                var ack = js.publish(subject, data, po);
                // jnats 2.17.x -> getSeqno()
                LOG.info("JetStream publish OK: stream=" + ack.getStream() + " seq=" + ack.getSeqno()
                        + " subject=" + subject);
                return;
            } catch (Exception e) {
                LOG.info("JetStream publish failed for '" + subject + "' -> using core NATS: " + e.getMessage());
                // НЕ делаем fallback автоматически - либо JetStream, либо ничего
                // Это предотвратит дублирование
            }
        }

        // 2) Fallback: обычный core NATS без ACK (надёжно для нотификаций)
        try {
            nc.publish(subject, data);
            try { nc.flush(Duration.ofMillis(30)); } catch (Exception ignore) {}
        } catch (Exception e) {
            LOG.warning("Core NATS publish failed for '" + subject + "': " + e.getMessage());
        }
    }

    /** Удобная перегрузка — публикация в дефолтный subject из конфига. */
    public void publish(String json) {
        publish(defaultSubject, json);
    }

    // ====== опционально: небольшой API для отладки ======
    public Connection connection() { return nc; }
    public String streamName() { return stream; }
    
    /**
     * Проверяет состояние JetStream и возвращает true, если он доступен.
     */
    public boolean isJetStreamAvailable() {
        return js != null;
    }
    
    /**
     * Проверяет состояние подключения к NATS.
     */
    public boolean isConnected() {
        return nc != null && nc.getStatus() == Connection.Status.CONNECTED;
    }
}
