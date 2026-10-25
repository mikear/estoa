# Instalación y configuración

## 1. Requisitos

### Usuarios de una release

| Componente | Mínimo | Notas |
|---|---|---|
| Sistema operativo | Windows 10/11 x64, Linux x64 con GTK3 o macOS 12+ Intel | Apple Silicon funciona con Rosetta; el build ARM64 nativo está previsto |
| Java | No necesario | El instalador y las versiones portables incluyen su propio runtime |
| RAM | 4 GB | El grafo y las cachés de fuentes viven en memoria |
| Disco | 200 MB + datos | La base y las claves crecen en `~/.orion/` |
| Internet | Opcional | Solo hace falta para enriquecer y comprobar actualizaciones |

### Compilación desde código

| Componente | Mínimo | Notas |
|---|---|---|
| JDK | 21 | Temurin, Zulu u OpenJDK 21+; se compila con `release=21` |
| Maven | 3.9+ | No hay Maven Wrapper en el repositorio |
| WiX 3 | Solo Windows | Necesario para generar el instalador `.exe` |
| `fakeroot` | Solo Linux | Necesario para generar el paquete `.deb` |

## 2. Descarga e instalación

Descarga la última versión desde [github.com/mikear/estoa/releases/latest](https://github.com/mikear/estoa/releases/latest).

| Plataforma | Instalador | Portable |
|---|---|---|
| Windows x64 | `Estoa-windows-x64.exe` | `Estoa-windows-x64-portable.zip` |
| Linux x64 | `estoa-linux-x64.deb` | `estoa-linux-x64-portable.tar.gz` |
| macOS Intel | `Estoa-macos-x64.dmg` | `Estoa-macos-x64-portable.zip` |

Los checksums están en `SHA256SUMS.txt`.

### Windows

1. Ejecuta `Estoa-windows-x64.exe`. La instalación es por usuario y no requiere administrador.
2. Si SmartScreen muestra un aviso, elige **Más información → Ejecutar de todas formas**. La v1 no está firmada.
3. La versión portable se extrae y se ejecuta desde `Estoa\Estoa.exe`; no requiere instalación.

### Linux

```bash
sudo apt install ./estoa-linux-x64.deb
```

Abre **Estoa** desde el menú de aplicaciones. Si el lanzador no está en el `PATH`, usa el acceso directo del paquete o el binario dentro de la instalación.

También puedes usar la versión portable:

```bash
tar -xzf estoa-linux-x64-portable.tar.gz
./Estoa/bin/Estoa
```

Se requiere GTK3. En Wayland puro puede hacer falta `GDK_BACKEND=x11`.

### macOS

1. Abre `Estoa-macos-x64.dmg` y arrastra Estoa a Aplicaciones.
2. Si Gatekeeper bloquea la app, haz clic derecho → **Abrir** la primera vez.
3. La versión portable se extrae y se abre `Estoa.app`.
4. En Apple Silicon, macOS pedirá instalar Rosetta si aún no está disponible.

## 3. Primer arranque

Al abrir Estoa se crea la base local en `~/.orion/orion.db`. La barra de estado muestra el caso activo, el número de entidades y relaciones y el estado de persistencia.

Para cargar el caso de demostración:

```text
Estoa --demo
```

Opciones útiles:

```text
--version              Muestra la versión y sale
--screenshot DIR       Captura 30 pantallas y sale
--no-update-check      Omite la comprobación de actualizaciones
--live ...             Ejecuta la prueba headless de fuentes reales
```

## 4. Actualizaciones

Estoa consulta la última release pública de GitHub como máximo una vez cada 24 horas. Si hay una versión nueva, muestra un aviso con las opciones **Descargar** y **Más tarde**. **Descargar** abre la página de la release en el navegador; no descarga ni instala nada automáticamente. **Más tarde** pospone el aviso de esa versión durante 7 días.

- Comprobación manual: **Ayuda → Buscar actualizaciones...**
- Caché: `~/.orion/update.properties` (solo metadatos de versión).
- La comprobación no envía telemetría ni claves.
- Desactívala con `--no-update-check` o no usando la app con conexión.

## 5. Compilar y empaquetar desde código

```bash
cd src
mvn test
mvn clean package -DskipTests                # Linux
mvn clean package -DskipTests -Pwindows      # Windows
mvn clean package -DskipTests -Pmacos        # macOS Intel
```

El JAR resultante es `src/orion-app/target/estoa.jar`.

Generar el instalador y la versión portable:

```powershell
powershell -File packaging/package.ps1
```

```bash
bash packaging/package.sh
```

- Windows: si WiX 3 está instalado, se genera también `Estoa-windows-x64.exe`; sin WiX solo se genera el portable y se muestra un aviso. Usa `-RequireInstaller` para exigir el instalador.
- Linux: genera `.deb` y `.tar.gz`; requiere `fakeroot`.
- macOS: genera `.dmg` y `.zip`; debe ejecutarse en un Mac Intel para el build x64.
- Cada script escribe en `dist/` y reemplaza su contenido. No ejecutes dos plataformas sobre el mismo `dist/` si quieres conservar los artefactos anteriores.

El workflow `.github/workflows/release.yml` construye las tres plataformas al publicar un tag `v*` y combina los checksums en `SHA256SUMS.txt`.

## 6. Base de datos

### H2 embebida (por defecto)

Archivo único en `~/.orion/orion.db`. No requiere servicios; para hacer copia de seguridad, copia el archivo con Estoa cerrada.

### PostgreSQL (multi-analista)

Define las variables antes de arrancar:

```bash
export ORION_DB_URL=jdbc:postgresql://servidor:5432/orion
export ORION_DB_USER=orion
export ORION_DB_PASS=********
Estoa
```

El esquema se crea en el primer arranque. El grafo completo se guarda como JSON junto a las columnas de listado.

## 7. Claves de API

Menú **Fuentes → Gestión de claves API**:

1. Selecciona el servicio (VirusTotal, Shodan, urlscan.io o URLQuery).
2. Pega la clave y pulsa **Guardar clave**.
3. La clave se cifra con AES-256-GCM en `~/.orion/keys.dat`.
4. La columna **Clave** solo muestra la máscara (`************ABCD`).

Notas:

- **urlscan.io** permite búsquedas públicas; la clave eleva el límite y se envía en `API-Key`.
- **URLQuery**, **VirusTotal** y **Shodan** requieren clave.
- **AbuseCH** (ThreatFox, URLHaus, MalwareBazaar) usa una clave gratuita de `auth.abuse.ch`; Estoa la busca como `abusech` o con el identificador del conector.
- Sin clave, las transformaciones comerciales fallan de forma controlada sin afectar al resto del caso.
- La clave maestra se genera en `~/.orion/.keystore.key` con permisos `0600` en sistemas POSIX.
- Opcionalmente: `export ORION_KEYSTORE_PASS=mi-secreto` (PBKDF2, 120.000 iteraciones).
- Las claves no se registran en logs ni se incluyen en informes.

## 8. Límites de las fuentes abiertas

| Fuente | Límite aproximado sin clave |
|---|---|
| ip-api | 45 consultas/minuto (solo HTTP e IPv4) |
| NVD | ~5 solicitudes cada 30 segundos |
| crt.sh | Sin límite formal; usar con criterio |
| AbuseCH | Clave gratuita de `auth.abuse.ch` |
| Feodo/OpenPhish/KEV | Descarga de feed con caché (1 h / 1 h / 6 h) |

## 9. Complementos

Coloca los JARs en `~/.orion/plugins/`. Un complemento implementa `com.orion.plugin.OrionPlugin` y declara el servicio en `META-INF/services/com.orion.plugin.OrionPlugin`. Al cargar puede registrar conectores y transformaciones mediante `PluginContext`.

## 10. Resolución de problemas

| Síntoma | Causa probable | Solución |
|---|---|---|
| SmartScreen o Gatekeeper bloquea la app | Build v1 sin firma | Windows: **Más información → Ejecutar de todas formas**. macOS: clic derecho → **Abrir** |
| `libXtst.so.6: cannot open shared object file` | Sistema sin `libXtst` | `sudo apt install libxtst6` |
| Pantalla en blanco en Wayland | JavaFX GTK con Wayland puro | `export GDK_BACKEND=x11` |
| `no glassgtk3 in java.library.path` al ejecutar el JAR | Faltan los nativos de JavaFX | Usa el instalador/portable, o extrae los nativos del JAR |
| H2 bloqueado (`Database may be already in use`) | Otra instancia de Estoa abierta | Cierra la otra sesión |
| La comprobación de actualizaciones falla | Sin red o repo/release no disponible | La app sigue funcionando; revisa la conexión o usa `--no-update-check` |
| `jpackage` no está disponible | `JAVA_HOME` apunta a un JRE o Java 8 | Define `JAVA_HOME` con un JDK 21+ |
| El instalador Windows no se genera | WiX 3 no instalado | Instala WiX 3 o usa el portable; en CI se instala automáticamente |
