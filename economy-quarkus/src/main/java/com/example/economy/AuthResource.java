package com.example.economy;

import com.example.economy.core.AuthHandlers;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.UUID;

@Path("/api/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    private static final Logger LOG = Logger.getLogger(AuthResource.class);

    @Inject
    AuthHandlers authHandlers;

    @POST
    @Path("/register")
    public Map<String, Object> register(AuthHandlers.RegisterRequest request) {
        try {
            LOG.infof("Registering user: %s", request.username());
            return authHandlers.register(request);
        } catch (Exception e) {
            LOG.error("Registration failed", e);
            throw new WebApplicationException("Registration failed", 500);
        }
    }

    @POST
    @Path("/login")
    public Map<String, Object> login(AuthHandlers.LoginRequest request) {
        try {
            LOG.infof("Login attempt for user: %s", request.uuid());
            return authHandlers.login(request);
        } catch (Exception e) {
            LOG.error("Login failed", e);
            throw new WebApplicationException("Registration failed", 500);
        }
    }
}