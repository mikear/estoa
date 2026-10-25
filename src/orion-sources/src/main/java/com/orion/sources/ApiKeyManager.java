package com.orion.sources;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Almacen de claves de API cifradas con AES-256-GCM. La clave maestra se
 * genera una sola vez y se guarda con permisos 0600 en {@code ~/.orion/.keystore.key};
 * opcionalmente puede protegerse con contrasena via la variable de entorno
 * ORION_KEYSTORE_PASS. Las claves nunca se registran en logs ni se exportan
 * en los informes; la UI solo muestra la mascara (ultimos 4 caracteres).
 */
public class ApiKeyManager {

    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final Path keyFile;
    private final Path storeFile;
    private final SecretKey masterKey;
    private final Map<String, String> keys = new TreeMap<>();
    private final SecureRandom random = new SecureRandom();

    public ApiKeyManager() {
        this(Path.of(System.getProperty("user.home"), ".orion"));
    }

    public ApiKeyManager(Path orionDir) {
        this.keyFile = orionDir.resolve(".keystore.key");
        this.storeFile = orionDir.resolve("keys.dat");
        try {
            Files.createDirectories(orionDir);
            this.masterKey = loadOrCreateMasterKey();
            loadStore();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo inicializar el almacén de claves: " + e.getMessage(), e);
        }
    }

    private SecretKey loadOrCreateMasterKey() throws IOException {
        if (Files.exists(keyFile)) {
            restrict(keyFile);
            byte[] raw = Base64.getDecoder().decode(Files.readString(keyFile).trim());
            return derive(new String(raw, StandardCharsets.UTF_8));
        }
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String encoded = Base64.getEncoder().encodeToString(raw);
        try {
            // Crear ya con 0600 evita la ventana en la que el fichero es legible.
            Files.createFile(keyFile, PosixFilePermissions.asFileAttribute(
                    PosixFilePermissions.fromString("rw-------")));
        } catch (UnsupportedOperationException | IllegalArgumentException e) {
            Files.createFile(keyFile); // Windows no soporta permisos POSIX
        }
        Files.writeString(keyFile, encoded);
        restrict(keyFile);
        return derive(new String(raw, StandardCharsets.UTF_8));
    }

    private static void restrict(Path path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (Exception ignored) {
            // sistemas sin POSIX (Windows)
        }
    }

    /** PBKDF2 sobre el material maestro + contraseña opcional. */
    private SecretKey derive(String material) {
        try {
            String pass = System.getenv("ORION_KEYSTORE_PASS");
            String salt = "orion-keystore-v1";
            javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(
                    (material + ":" + (pass == null ? "" : pass)).toCharArray(),
                    salt.getBytes(StandardCharsets.UTF_8), 120_000, 256);
            javax.crypto.SecretKeyFactory f = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return new SecretKeySpec(f.generateSecret(spec).getEncoded(), "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Derivación de la clave maestra fallida", e);
        }
    }

    private synchronized void loadStore() throws IOException {
        if (!Files.exists(storeFile)) return;
        restrict(storeFile);
        for (String line : Files.readAllLines(storeFile)) {
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            try {
                keys.put(line.substring(0, eq), decrypt(line.substring(eq + 1)));
            } catch (Exception ignored) {
                // entrada corrupta: se ignora
            }
        }
    }

    private synchronized void saveStore() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : keys.entrySet()) {
            try {
                sb.append(e.getKey()).append('=').append(encrypt(e.getValue())).append('\n');
            } catch (IOException ioe) {
                throw ioe;
            } catch (Exception ce) {
                throw new IOException("Fallo cifrando la clave de " + e.getKey(), ce);
            }
        }
        Files.writeString(storeFile, sb.toString());
        restrict(storeFile);
    }

    private String encrypt(String plain) throws Exception {
        byte[] iv = new byte[IV_LEN];
        random.nextBytes(iv);
        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, iv));
        byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        byte[] out = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ct, 0, out, iv.length, ct.length);
        return Base64.getEncoder().encodeToString(out);
    }

    private String decrypt(String b64) throws Exception {
        byte[] all = Base64.getDecoder().decode(b64);
        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, all, 0, IV_LEN));
        byte[] plain = cipher.doFinal(all, IV_LEN, all.length - IV_LEN);
        return new String(plain, StandardCharsets.UTF_8);
    }

    public synchronized void setKey(String service, String apiKey) {
        if (service == null || apiKey == null || apiKey.isBlank()) return;
        keys.put(service.trim().toLowerCase(), apiKey.trim());
        try {
            saveStore();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo persistir el almacén de claves", e);
        }
    }

    public synchronized String getKey(String service) {
        return keys.get(service.trim().toLowerCase());
    }

    public synchronized boolean hasKey(String service) {
        String k = getKey(service);
        return k != null && !k.isBlank();
    }

    public synchronized void deleteKey(String service) {
        keys.remove(service.trim().toLowerCase());
        try {
            saveStore();
        } catch (IOException ignored) {
        }
    }
    public synchronized Map<String, String> masked() {
        Map<String, String> m = new LinkedHashMap<>();
        keys.forEach((s, k) -> m.put(s, mask(k)));
        return m;
    }

    public static String mask(String key) {
        if (key == null || key.length() <= 4) return "****";
        return "************" + key.substring(key.length() - 4);
    }

    public static boolean selfTest() {
        try {
            Path tmp = Files.createTempDirectory("orion-kst");
            ApiKeyManager m = new ApiKeyManager(tmp);
            m.setKey("test-service", "abcd1234efgh5678");
            if (!"abcd1234efgh5678".equals(m.getKey("test-service"))) return false;
            String stored = Files.readString(tmp.resolve("keys.dat"));
            return !stored.contains("abcd1234efgh5678")
                    && m.hasKey("test-service")
                    && "************5678".equals(ApiKeyManager.mask("abcd1234efgh5678"));
        } catch (Exception e) {
            return false;
        }
    }
}
