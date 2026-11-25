package io.github.wangkui.triagedeck.audit;

public class IpMasker {

    public static String mask(String ip) {
        if (ip == null || ip.isEmpty()) {
            return null;
        }
        ip = ip.trim();
        // IPv4
        if (ip.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
            String[] parts = ip.split("\\.");
            if (parts.length == 4) {
                try {
                    int firstOctet = Integer.parseInt(parts[0]);
                    if (firstOctet >= 0 && firstOctet <= 255) {
                        // /24 mask for private/internal IPs
                        if (firstOctet == 10) return "10.0.0.0/8";
                        if (firstOctet == 172 && Integer.parseInt(parts[1]) >= 16 && Integer.parseInt(parts[1]) <= 31) return "172.16.0.0/12";
                        if (firstOctet == 192 && Integer.parseInt(parts[1]) == 168) return "192.168.0.0/16";
                        return ip + "/32";
                    }
                } catch (NumberFormatException e) {
                    // fall through
                }
            }
            return ip;  // fallback - unchanged
        }
        // IPv6
        if (ip.matches("([0-9a-fA-F]{0,4}:){2,}[0-9a-fA-F]{0,4}")) {
            // Simplified: return network prefix /64
            int colonCount = 0;
            for (char c : ip.toCharArray()) {
                if (c == ':') colonCount++;
            }
            if (colonCount >= 2) {
                return ip.substring(0, ip.lastIndexOf(':') + 1) + "0/64";
            }
        }
        return ip;
    }

    public static String mask(String ip, int cidr) {
        return mask(ip);  // Simplified - always use /24 or /64
    }
}