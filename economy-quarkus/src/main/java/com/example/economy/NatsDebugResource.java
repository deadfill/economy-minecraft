package com.example.economy;

import com.example.economy.core.NatsBus;
import io.nats.client.JetStreamManagement;
import io.nats.client.api.StreamInfo;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

@Path("/api/v1/debug/nats")
@Produces(MediaType.APPLICATION_JSON)
public class NatsDebugResource {

    @Inject NatsBus nats;

    @GET
    @Path("/stream")
    public Map<String, Object> streamInfo() {
        try {
            var nc = nats.connection();
            if (nc == null) {
                return Map.of("error", "NATS connection is null");
            }
            JetStreamManagement jsm = nc.jetStreamManagement();
            StreamInfo info = jsm.getStreamInfo(nats.streamName());
            var cfg = info.getConfiguration();
            return Map.of(
                    "stream", cfg.getName(),
                    "subjects", List.copyOf(cfg.getSubjects()),
                    "retention", cfg.getRetentionPolicy().toString(),
                    "storage", cfg.getStorageType().toString()
            );
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }
}
