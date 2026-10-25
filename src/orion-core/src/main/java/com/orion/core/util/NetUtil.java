package com.orion.core.util;

import java.util.regex.Pattern;

/** Utilidades de red para clasificar y normalizar observables. */
public final class NetUtil {

    private static final Pattern IPV4 =
            Pattern.compile("^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");
    private static final Pattern IPV6 = Pattern.compile("^([0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}$");
    private static final Pattern DOMAIN =
            Pattern.compile("^(?=.{1,253}$)(?!-)[a-zA-Z0-9-]{1,63}(?<!-)(\\.[a-zA-Z]{2,})+$");
    private static final Pattern MD5 = Pattern.compile("^[a-fA-F0-9]{32}$");
    private static final Pattern SHA1 = Pattern.compile("^[a-fA-F0-9]{40}$");
    private static final Pattern SHA256 = Pattern.compile("^[a-fA-F0-9]{64}$");
    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");
    private static final Pattern ATTCK_TECH = Pattern.compile("^T1\\d{3}(\\.\\d{3})?$");

    private NetUtil() {}

    public static boolean isIpv4(String s) { return s != null && IPV4.matcher(s.trim()).matches(); }
    public static boolean isIpv6(String s) { return s != null && IPV6.matcher(s.trim()).matches(); }
    public static boolean isDomain(String s) { return s != null && DOMAIN.matcher(s.trim().toLowerCase()).matches(); }
    public static boolean isEmail(String s) { return s != null && EMAIL.matcher(s.trim()).matches(); }
    public static boolean isMd5(String s) { return s != null && MD5.matcher(s.trim()).matches(); }
    public static boolean isSha1(String s) { return s != null && SHA1.matcher(s.trim()).matches(); }
    public static boolean isSha256(String s) { return s != null && SHA256.matcher(s.trim()).matches(); }
    public static boolean isHash(String s) { return isMd5(s) || isSha1(s) || isSha256(s); }

    public static boolean isUrl(String s) {
        return s != null && (s.trim().toLowerCase().startsWith("http://") || s.trim().toLowerCase().startsWith("https://"));
    }

    public static boolean isCve(String s) {
        return s != null && s.trim().toUpperCase().matches("^CVE-\\d{4}-\\d{4,7}$");
    }

    public static boolean isAsn(String s) {
        return s != null && s.trim().toUpperCase().matches("^AS\\d+$");
    }

    /** Normaliza un dominio: minusculas, sin punto final, sin esquema. */
    public static String normalizeDomain(String d) {
        if (d == null) return "";
        String s = d.trim().toLowerCase();
        if (s.startsWith("http://") || s.startsWith("https://")) {
            int slash = s.indexOf('/', s.indexOf("://") + 3);
            s = slash > 0 ? s.substring(0, slash) : s;
            s = s.replaceFirst("^https?://", "");
            int colon = s.indexOf(':');
            if (colon > 0) s = s.substring(0, colon);
        }
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s;
    }

    /** Extrae el dominio registrable aproximado de un FQDN o URL. */
    public static String registrableDomain(String host) {
        String h = normalizeDomain(host);
        String[] parts = h.split("\\.");
        if (parts.length <= 2) return h;
        // heuristica de sufijo de dos niveles mas comun (co.uk, com.ar, ...)
        String lastTwo = parts[parts.length - 2] + "." + parts[parts.length - 1];
        if (lastTwo.matches("(co|com|net|org|gov|edu)\\.(uk|ar|br|jp|cn|in|au|mx|cl|co)$")
                && parts.length >= 3) {
            return parts[parts.length - 3] + "." + lastTwo;
        }
        return lastTwo;
    }

    /** Detecta automaticamente el tipo de un observable en texto libre. */
    public static com.orion.core.model.EntityType detectType(String s) {
        if (s == null || s.isBlank()) return com.orion.core.model.EntityType.INDICATOR;
        String v = s.trim();
        if (isUrl(v)) return com.orion.core.model.EntityType.URL;
        if (isIpv4(v)) return com.orion.core.model.EntityType.IPV4;
        if (isIpv6(v)) return com.orion.core.model.EntityType.IPV6;
        if (isEmail(v)) return com.orion.core.model.EntityType.EMAIL;
        if (isCve(v)) return com.orion.core.model.EntityType.CVE;
        if (isAsn(v)) return com.orion.core.model.EntityType.ASN;
        if (v.toUpperCase().matches("^T1\\d{3}(\\.\\d{3})?$")) return com.orion.core.model.EntityType.ATTCK_TECHNIQUE;
        if (isHash(v)) return com.orion.core.model.EntityType.HASH;
        if (isDomain(v)) return com.orion.core.model.EntityType.DOMAIN;
        return com.orion.core.model.EntityType.INDICATOR;
    }

    /** Distancia de Levenshtein para medidas de similitud entre dominios. */
    public static int levenshtein(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) d[i][0] = i;
        for (int j = 0; j <= b.length(); j++) d[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
            }
        }
        return d[a.length()][b.length()];
    }
}
