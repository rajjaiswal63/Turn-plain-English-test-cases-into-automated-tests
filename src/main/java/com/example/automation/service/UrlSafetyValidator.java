package com.example.automation.service;

import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class UrlSafetyValidator {
    private static final long DNS_CACHE_NANOS = java.time.Duration.ofSeconds(15).toNanos();
    private final ConcurrentMap<String, CachedAddresses> dnsCache = new ConcurrentHashMap<>();

    public URI validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank() || rawUrl.length() > 2048) {
            throw new AutomationException("Invalid or empty URL.");
        }
        final URI uri;
        try {
            uri = new URI(rawUrl.trim()).normalize();
        } catch (URISyntaxException ex) {
            throw new AutomationException("Invalid URL format.", ex);
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!uri.isAbsolute() || scheme == null || host == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new AutomationException("Only absolute HTTP or HTTPS website URLs are allowed.");
        }
        if (uri.getRawUserInfo() != null || host.isBlank()) {
            throw new AutomationException("URLs containing credentials or an invalid host are not allowed.");
        }
        int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443) {
            throw new AutomationException("Only standard HTTP (80) and HTTPS (443) ports are allowed.");
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (normalizedHost.endsWith(".") ) normalizedHost = normalizedHost.substring(0, normalizedHost.length() - 1);
        if (normalizedHost.startsWith("[") && normalizedHost.endsWith("]")) {
            normalizedHost = normalizedHost.substring(1, normalizedHost.length() - 1);
        }
        if (normalizedHost.equals("localhost") || normalizedHost.endsWith(".localhost")
                || normalizedHost.equals("local") || normalizedHost.endsWith(".local")) {
            throw new AutomationException("Local and localhost destinations are blocked.");
        }
        try {
            if (normalizedHost.indexOf(':') < 0 && !isIpv4Literal(normalizedHost)) {
                normalizedHost = IDN.toASCII(normalizedHost, IDN.USE_STD3_ASCII_RULES);
            }
            InetAddress[] addresses = resolve(normalizedHost);
            if (addresses.length == 0) throw new AutomationException("The URL host did not resolve.");
            for (InetAddress address : addresses) {
                if (!isPublicAddress(address)) {
                    throw new AutomationException("The URL resolves to a private, local, or reserved IP address and is blocked.");
                }
            }
        } catch (UnknownHostException ex) {
            throw new AutomationException("The URL host could not be resolved.", ex);
        } catch (IllegalArgumentException ex) {
            throw new AutomationException("Invalid internationalized domain name.", ex);
        }
        return uri;
    }

    private InetAddress[] resolve(String host) throws UnknownHostException {
        long now = System.nanoTime();
        CachedAddresses cached = dnsCache.get(host);
        if (cached != null && now < cached.expiresAtNanos) return cached.addresses;
        InetAddress[] addresses = InetAddress.getAllByName(host);
        dnsCache.put(host, new CachedAddresses(addresses, now + DNS_CACHE_NANOS));
        return addresses;
    }

    private boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            int a = Byte.toUnsignedInt(b[0]), second = Byte.toUnsignedInt(b[1]);
            int third = Byte.toUnsignedInt(b[2]);
            if (a == 0 || a == 10 || a == 127 || a >= 224) return false;
            if (a == 100 && second >= 64 && second <= 127) return false;
            if (a == 172 && second >= 16 && second <= 31) return false;
            if (a == 169 && second == 254) return false;
            if (a == 192 && (second == 168 || (second == 0 && third == 0) || (second == 0 && third == 2)
                    || (second == 88 && third == 99))) return false;
            if (a == 198 && (second == 18 || second == 19 || (second == 51 && third == 100))) return false;
            if (a == 203 && second == 0 && third == 113) return false;
            return true;
        }
        if (address instanceof Inet6Address) {
            int first = Byte.toUnsignedInt(b[0]);
            if ((first & 0xfe) == 0xfc) return false; // Unique-local fc00::/7
            if (first == 0x20 && Byte.toUnsignedInt(b[1]) == 0x01
                    && Byte.toUnsignedInt(b[2]) == 0x0d && Byte.toUnsignedInt(b[3]) == 0xb8) return false; // Documentation
            boolean mappedV4 = true;
            for (int i = 0; i < 10; i++) mappedV4 &= b[i] == 0;
            mappedV4 &= b[10] == (byte) 0xff && b[11] == (byte) 0xff;
            if (mappedV4) return false;
            return (first & 0xe0) == 0x20; // Global unicast only
        }
        return false;
    }

    private boolean isIpv4Literal(String host) {
        String[] octets = host.split("\\.", -1);
        if (octets.length != 4) return false;
        for (String octet : octets) {
            if (octet.isEmpty() || !octet.chars().allMatch(Character::isDigit)) return false;
            try { if (Integer.parseInt(octet) > 255) return false; }
            catch (NumberFormatException ex) { return false; }
        }
        return true;
    }

    private record CachedAddresses(InetAddress[] addresses, long expiresAtNanos) { }
}
