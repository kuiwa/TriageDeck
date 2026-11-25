package io.github.wangkui.triagedeck.alert.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

public record Fingerprint(String value) {

    public static Fingerprint of(AlertIngestCommand command) {
        String material = String.join("\u001F",
                normalize(command.source()),
                normalize(command.type()),
                normalize(command.resourceId()),
                normalize(command.eventCode()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            return new Fingerprint(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String normalize(String value) {
        return Objects.toString(value, "").strip().toLowerCase(Locale.ROOT);
    }
}
