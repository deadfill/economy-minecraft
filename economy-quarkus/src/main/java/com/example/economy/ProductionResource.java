package com.example.economy;

import com.example.economy.core.ApiHandlers;
import com.example.economy.core.NatsBus;              // ⬅ добавь импорт
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/api/v1/production")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProductionResource {

    private static final Logger LOG = Logger.getLogger(ProductionResource.class);

    @Inject ApiHandlers apiHandlers;
    @Inject NatsBus nats;                              // ⬅ добавь инжект

    @POST
    @Path("/start")
    public Map<String, Object> startProduction(ApiHandlers.StartRequest request) {
        try {
            LOG.infof("Starting production for user: %s, recipe: %s", request.ownerUuid(), request.recipeId());
            return apiHandlers.startProduction(request);
        } catch (IllegalArgumentException e) {
            // Это ожидаемые ошибки валидации (нехватка материалов, неверный рецепт и т.д.)
            LOG.infof("Production start failed for user %s: %s", request.ownerUuid(), e.getMessage());
            return Map.of(
                "success", false,
                "error", Map.of(
                    "type", "validation_error",
                    "message", e.getMessage()
                )
            );
        } catch (Exception e) {
            LOG.error("Unexpected error during production start", e);
            return Map.of(
                "success", false,
                "error", Map.of(
                    "type", "server_error", 
                    "message", "Failed to start production"
                )
            );
        }
    }

    @GET
    @Path("/list")
    public List<com.example.economy.core.Repositories.JobRow> listJobs(@QueryParam("ownerUuid") String ownerUuid) {
        try {
            UUID owner = UUID.fromString(ownerUuid);
            LOG.infof("Listing jobs for user: %s", owner);
            return apiHandlers.listJobs(owner);
        } catch (Exception e) {
            LOG.error("Failed to list jobs", e);
            throw new WebApplicationException("Failed to list jobs", 500);
        }
    }

    @POST
    @Path("/claim")
    public Map<String, Object> claimRewards(ApiHandlers.ClaimRequest request) {
        try {
            LOG.infof("Claiming rewards for user: %s", request.ownerUuid());
            int claimed = apiHandlers.claimRewards(request.ownerUuid());

            // Публикуем в NATS ТОЛЬКО если есть что публиковать
            if (claimed > 0) {
                try {
                    String payload = "{\"type\":\"claimed\",\"owner\":\""+request.ownerUuid()+"\",\"claimed\":"+claimed+"}";
                    nats.publish("econ.production.claimed", payload);
                } catch (Exception ex) {
                    LOG.warn("NATS publish failed on claim: " + ex.getMessage());
                }
            }

            return Map.of("claimed", claimed);
        } catch (Exception e) {
            LOG.error("Failed to claim rewards", e);
            throw new WebApplicationException("Failed to claim rewards", 500);
        }
    }
}
