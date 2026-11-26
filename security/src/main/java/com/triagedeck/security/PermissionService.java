package com.triagedeck.security.auth;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.casbin.jcasbin.main.Enforcer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {

    private static final Map<String, List<String>> ACTIONS = Map.of(
            "alert", List.of("read", "ingest", "clear"),
            "audit", List.of("read", "export"),
            "ai", List.of("briefing", "query", "digest"),
            "whoami", List.of("read"),
            "_perm", List.of("check"));

    private final Enforcer enforcer;

    public PermissionService() {
        try {
            Path model = copyClasspathResource("casbin/model.conf");
            Path policy = copyClasspathResource("casbin/policy.csv");
            this.enforcer = new Enforcer(model.toString(), policy.toString());
        } catch (IOException exception) {
            throw new IllegalStateException("Casbin resources could not be loaded", exception);
        }
    }

    public boolean isAllowed(Set<String> roles, String obj, String act) {
        if (!ACTIONS.containsKey(obj) || !ACTIONS.get(obj).contains(act)) {
            return false;
        }
        for (String role : roles) {
            if (enforcer.enforce(role, obj, act)) {
                return true;
            }
        }
        return false;
    }

    public List<Permission> permissions(Set<String> roles) {
        List<Permission> permissions = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : ACTIONS.entrySet()) {
            for (String act : entry.getValue()) {
                if (isAllowed(roles, entry.getKey(), act)) {
                    permissions.add(new Permission(entry.getKey(), act));
                }
            }
        }
        return permissions;
    }

    public boolean isValidObjectAndAction(String obj, String act) {
        return ACTIONS.containsKey(obj) && ACTIONS.get(obj).contains(act);
    }

    private static Path copyClasspathResource(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream input = resource.getInputStream()) {
            Path target = Files.createTempFile("triagedeck-", "-" + Path.of(path).getFileName());
            Files.writeString(target, new String(input.readAllBytes(), StandardCharsets.UTF_8));
            target.toFile().deleteOnExit();
            return target;
        }
    }

    public record Permission(String obj, String act) {
    }
}
