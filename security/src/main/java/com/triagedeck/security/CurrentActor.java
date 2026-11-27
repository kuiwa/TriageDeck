package com.triagedeck.security.auth;

import java.security.Principal;
import java.util.List;

/**
 * 当前操作人。实现 {@link Principal} 是必须的：`UsernamePasswordAuthenticationToken`
 * 的 `getName()` 在 principal 不是 Principal/UserDetails 时会退化成 `toString()`，
 * 导致 `SecurityContext.getCurrentUser()`（进而 history.cleared_by、alert.cleared.clearedBy）
 * 写进 `CurrentActor[...]` 这样的整串。
 */
public record CurrentActor(String username, String subject, List<String> roles)
        implements Principal {

    @Override
    public String getName() {
        return username;
    }
}
