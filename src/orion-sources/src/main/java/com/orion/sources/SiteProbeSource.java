package com.orion.sources;

import com.orion.core.model.EdgeDraft;
import com.orion.core.model.EntityType;
import com.orion.core.model.OrionEntity;
import com.orion.core.model.RelationshipType;
import com.orion.core.util.JsonUtil;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inspeccion del sitio publicado por un dominio o URL: estado HTTP, titulo,
 * generador (CMS), formularios, favicon (hash mmh3 estilo Shodan + SHA-256),
 * correos visibles y cabeceras de seguridad. Sin clave de API.
 *
 * <p>La sonda rechaza destinos internos (loopback, redes privadas, link-local)
 * para no convertirse en un proxy SSRF.
 */
public class SiteProbeSource implements SourceConnector {

    public static final String ID = "site-probe";

    private static final Pattern TITLE = Pattern.compile(
            "<title[^>]*>([\\s\\S]{0,300}?)</title>", Pattern.CASE_INSENSITIVE);
    private static final Pattern GENERATOR_A = Pattern.compile(
            "<meta[^>]+name=[\"']generator[\"'][^>]*content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern GENERATOR_B = Pattern.compile(
            "<meta[^>]+content=[\"']([^\"']+)[\"'][^>]*name=[\"']generator[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern ICON_A = Pattern.compile(
            "<link[^>]+rel=[\"'][^\"']*icon[^\"']*[\"'][^>]*href=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern ICON_B = Pattern.compile(
            "<link[^>]+href=[\"']([^\"']+)[\"'][^>]*rel=[\"'][^\"']*icon[^\"']*[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern FORM = Pattern.compile("<form\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PASSWORD = Pattern.compile(
            "<input[^>]+type=[\"']password[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern MAILTO = Pattern.compile(
            "mailto:([^\"'?>\\s&]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern LINK = Pattern.compile(
            "<a\\b[^>]*\\bhref\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    @Override
    public String id() { return ID; }

    @Override
    public String name() { return "Inspección del sitio web"; }

    @Override
    public String description() { return "HTTP, título, CMS, formularios, favicon (mmh3/SHA-256), correos, sitios enlazados y cabeceras de seguridad."; }

    @Override
    public Category category() { return Category.OPEN; }

    @Override
    public boolean requiresApiKey() { return false; }

    @Override
    public Set<Operation> supports() { return Set.of(Operation.SITE_PROBE); }

    @Override
    public SourceResult query(SourceQuery q) {
        String target = q.value().trim();
        String url;
        if (com.orion.core.util.NetUtil.isUrl(target)) {
            url = target;
        } else if (com.orion.core.util.NetUtil.isDomain(target)) {
            url = "https://" + com.orion.core.util.NetUtil.normalizeDomain(target);
        } else {
            return SourceResult.error(id(), "Se requiere un dominio o URL: " + q.value());
        }

        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            return SourceResult.error(id(), "URL no válida: " + target);
        }
        String host = com.orion.core.util.NetUtil.normalizeDomain(uri.getHost());
        if (host.isEmpty()) {
            return SourceResult.error(id(), "URL sin host: " + target);
        }
        if (Http.isInternalHost(host)) {
            return SourceResult.error(id(), "Destino interno no permitido: " + host);
        }

        try {
            Http.Response resp = probe(url);
            String html = resp.body() == null ? "" : resp.body();
            String title = decode(first(html, TITLE));
            String generator = decode(first(html, GENERATOR_A));
            if (generator.isEmpty()) generator = decode(first(html, GENERATOR_B));
            int forms = count(html, FORM);
            boolean password = PASSWORD.matcher(html).find();
            Set<String> emails = emails(html);
            Set<String> linked = linkedHosts(html, uri, host);

            SourceResult r = SourceResult.ok(id(), "HTTP " + resp.status()
                    + (title.isEmpty() ? "" : " | " + title)
                    + " | " + forms + " formularios"
                    + " | " + emails.size() + " correos"
                    + (linked.isEmpty() ? "" : " | " + linked.size() + " sitios enlazados"));

            OrionEntity dom = OrionEntity.of(EntityType.DOMAIN, host)
                    .attr("httpStatus", resp.status())
                    .attr("finalUrl", url)
                    .attr("title", title)
                    .attr("server", resp.header("server"))
                    .attr("poweredBy", resp.header("x-powered-by"))
                    .attr("contentType", resp.header("content-type"))
                    .attr("generator", generator)
                    .attr("forms", forms)
                    .attr("passwordForm", password)
                    .attr("emails", String.join(", ", emails))
                    .attr("securityHeaders", securityHeaders(resp));
            dom.addSource(id());

            double risk = 10;
            if (password) risk += 15;
            if (title.toLowerCase().matches(".*(login|sign in|acceder|iniciar sesion).*")) risk += 10;
            if (resp.status() >= 400) risk = 5;
            dom.setRiskScore(risk);
            r.entity(dom);

            if (!generator.isEmpty()) {
                OrionEntity software = OrionEntity.of(EntityType.SOFTWARE, generator)
                        .attr("source", id());
                software.addSource(id());
                r.entity(software);
                r.edge(new EdgeDraft(host, generator, RelationshipType.RELATED_TO, "usa tecnología"));
            }

            int n = 0;
            for (String email : emails) {
                if (n++ >= 10) break;
                OrionEntity em = OrionEntity.of(EntityType.EMAIL, email)
                        .attr("publishedAt", host)
                        .attr("source", id());
                em.addSource(id());
                r.entity(em);
                r.edge(new EdgeDraft(host, email, RelationshipType.RELATED_TO, "correo publicado"));
            }

            for (String linkedHost : linked) {
                OrionEntity ld = OrionEntity.of(EntityType.DOMAIN, linkedHost)
                        .attr("linkedFrom", host)
                        .attr("source", id());
                ld.addSource(id());
                r.entity(ld);
                r.edge(new EdgeDraft(host, linkedHost, RelationshipType.RELATED_TO, "enlace saliente"));
            }

            String icon = first(html, ICON_A);
            if (icon.isEmpty()) icon = first(html, ICON_B);
            if (!icon.isEmpty()) {
                favicon(r, host, uri, icon);
            }

            r.raw("status", resp.status()).raw("title", title).raw("forms", forms);
            return r;
        } catch (Exception e) {
            return SourceResult.error(id(), "Inspección fallida: " + e.getMessage());
        }
    }

    private static Http.Response probe(String url) throws Exception {
        try {
            return Http.fetch(url, Map.of(
                    "User-Agent", Http.BROWSER_USER_AGENT,
                    "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"),
                    Duration.ofSeconds(20));
        } catch (java.io.IOException e) {
            if (url.startsWith("https://")) {
                return Http.fetch("http://" + url.substring("https://".length()), Map.of(
                        "User-Agent", Http.BROWSER_USER_AGENT,
                        "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"),
                        Duration.ofSeconds(15));
            }
            throw e;
        }
    }

    private static void favicon(SourceResult r, String host, URI base, String icon) {
        try {
            URI iconUri = base.resolve(icon);
            if (!"http".equalsIgnoreCase(iconUri.getScheme()) && !"https".equalsIgnoreCase(iconUri.getScheme())) return;
            Http.Bytes bytes = Http.bytes(iconUri.toString(), Map.of(
                    "User-Agent", Http.BROWSER_USER_AGENT,
                    "Accept", "image/*"), Duration.ofSeconds(12));
            if (bytes.status() != 200 || bytes.body() == null || bytes.body().length == 0
                    || bytes.body().length > 512 * 1024) {
                return;
            }
            String b64 = Base64.getMimeEncoder(76, new byte[]{'\n'}).encodeToString(bytes.body());
            String hash = String.valueOf(mmh3(b64));
            String sha256 = sha256(bytes.body());
            OrionEntity fav = OrionEntity.of(EntityType.HASH, "mmh3:" + hash)
                    .attr("sha256", sha256)
                    .attr("faviconUrl", iconUri.toString())
                    .attr("forDomain", host)
                    .attr("source", ID);
            fav.addSource(ID);
            fav.setRiskScore(10);
            r.entity(fav);
            r.edge(new EdgeDraft(host, "mmh3:" + hash, RelationshipType.RELATED_TO, "favicon"));
            r.raw("faviconMmh3", hash).raw("faviconSha256", sha256);
        } catch (Exception ignored) {
            // el favicon es opcional: un fallo no invalida la inspeccion
        }
    }

    private static String securityHeaders(Http.Response resp) {
        StringBuilder sb = new StringBuilder();
        for (String h : new String[]{"strict-transport-security", "content-security-policy",
                "x-frame-options", "x-content-type-options", "referrer-policy"}) {
            if (!resp.header(h).isEmpty()) {
                if (sb.length() > 0) sb.append("; ");
                sb.append(h);
            }
        }
        return sb.toString();
    }

    static Set<String> linkedHosts(String html, URI base, String host) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = LINK.matcher(html);
        while (m.find() && out.size() < 25) {
            String href = m.group(1).trim();
            if (href.isEmpty() || href.startsWith("#")) continue;
            try {
                URI link = base.resolve(href);
                String scheme = link.getScheme();
                if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) continue;
                String linkedHost = com.orion.core.util.NetUtil.normalizeDomain(link.getHost());
                if (linkedHost.isEmpty() || linkedHost.equals(host)
                        || !com.orion.core.util.NetUtil.isDomain(linkedHost)) {
                    continue;
                }
                out.add(linkedHost);
            } catch (Exception ignored) {
                // un enlace mal formado no invalida la inspeccion
            }
        }
        return out;
    }

    private static Set<String> emails(String html) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = MAILTO.matcher(html);
        while (m.find() && out.size() < 20) {
            String e = URLDecoder.decode(m.group(1), StandardCharsets.UTF_8).toLowerCase();
            if (com.orion.core.util.NetUtil.isEmail(e)) out.add(e);
        }
        m = EMAIL.matcher(html);
        while (m.find() && out.size() < 20) {
            String e = m.group().toLowerCase();
            if (e.endsWith(".png") || e.endsWith(".jpg") || e.endsWith(".gif")
                    || e.endsWith(".webp") || e.endsWith(".svg") || e.contains("@example.")) {
                continue;
            }
            if (com.orion.core.util.NetUtil.isEmail(e)) out.add(e);
        }
        return out;
    }

    private static String first(String text, Pattern pattern) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1).trim() : "";
    }

    private static int count(String text, Pattern pattern) {
        int n = 0;
        Matcher m = pattern.matcher(text);
        while (m.find() && n < 100) n++;
        return n;
    }

    private static String decode(String s) {
        return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
                .replaceAll("\\s+", " ").trim();
    }

    private static String sha256(byte[] data) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder sb = new StringBuilder(d.length * 2);
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** MurmurHash3 x86 32-bit (seed 0), compatible con el hash de favicon de Shodan. */
    static int mmh3(String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        int h = 0;
        int i = 0;
        while (i + 4 <= data.length) {
            int k = (data[i] & 0xff)
                    | ((data[i + 1] & 0xff) << 8)
                    | ((data[i + 2] & 0xff) << 16)
                    | ((data[i + 3] & 0xff) << 24);
            k *= 0xcc9e2d51;
            k = Integer.rotateLeft(k, 15);
            k *= 0x1b873593;
            h ^= k;
            h = Integer.rotateLeft(h, 13);
            h = h * 5 + 0xe6546b64;
            i += 4;
        }
        int k = 0;
        switch (data.length - i) {
            case 3:
                k ^= (data[i + 2] & 0xff) << 16;
                // fall through
            case 2:
                k ^= (data[i + 1] & 0xff) << 8;
                // fall through
            case 1:
                k ^= data[i] & 0xff;
                k *= 0xcc9e2d51;
                k = Integer.rotateLeft(k, 15);
                k *= 0x1b873593;
                h ^= k;
                break;
            default:
                break;
        }
        h ^= data.length;
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        return h;
    }
}
