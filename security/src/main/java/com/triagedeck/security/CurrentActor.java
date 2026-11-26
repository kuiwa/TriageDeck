package com.triagedeck.security.auth;

import java.util.List;

public record CurrentActor(String username, String subject, List<String> roles) {
}
