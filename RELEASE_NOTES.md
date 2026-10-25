# Notas de la versión — Estoa v1.0.0

**Fecha:** 2026-10-25 · **Estado:** primera release pública · **Desarrollo:** [mikear](https://github.com/mikear)

## Resumen

Estoa es una plataforma de escritorio de threat intelligence (JavaFX + Spring Boot) para investigaciones: desde la adición de observables hasta la correlación heurística, la matriz ATT&CK y la exportación de informes, con grafo interactivo y persistencia local.

Estoa (antes ORION) mantiene los identificadores internos `com.orion`, `~/.orion` y las variables `ORION_*` para no romper casos ni claves existentes.

## Novedades de la release

### Instalación sin Java

- Instaladores y versiones portables para Windows x64, Linux x64 y macOS Intel x64.
- Cada paquete incluye su propio runtime: **no hace falta instalar Java**.
- Windows: instalador por usuario, sin permisos de administrador, con acceso directo y menú.
- Linux: paquete `.deb` y archivo portable `.tar.gz`.
- macOS: `.dmg` y `.zip` portable. Apple Silicon funciona mediante Rosetta.
- Checksums SHA256 publicados con la release.

### Actualizaciones

- Aviso al arrancar si hay una release más nueva, como máximo una comprobación cada 24 horas.
- El aviso muestra la versión y abre la página de descarga; **no descarga ni instala automáticamente**.
- Comprobación manual en **Ayuda → Buscar actualizaciones...**
- Se puede desactivar con `--no-update-check`.
- No hay telemetría ni se envían claves; la única petición es a la API pública de GitHub Releases.

### Privacidad y soporte

- Los casos nuevos usan el analista genérico `analista`; el caso demo usa `demo`. Ya no se toma el nombre de usuario del sistema operativo.
- `--version` muestra la versión empaquetada.
- La versión de **Acerca de** se lee del propio build, no está fijada en el código.

## Qué incluye

### Modelo y grafo

- 28 tipos de entidad (dominio, IP, ASN, hash, certificado, malware, APT, campaña, CVE, técnica/táctica ATT&CK...) con identidad canónica `tipo:valor`, atributos, TLP y riesgo 0-100.
- Grafo interactivo con 8 disposiciones (fuerza dirigida Barnes-Hut, orgánico, circular, rejilla, jerárquico, radial, clústeres, aleatorio), zoom a cursor, paneo, arrastre con vecinos N1 opcional, anti-solapamiento, repulsión ajustable, pantalla completa (F11) y referencias externas activables.
- Agrupación expandible por **tipo, riesgo, TLP o comunidad** (Louvain determinista), con conteo/críticos, aristas agregadas, nodos sin grupo en **Otros** y expansión a pantalla completa.
- Foco de vecindad **N1** con historial (Enter/Esc y menú contextual), filtros de riesgo/TLP, etiquetas priorizadas con tooltip, etiquetas de relación al hover, leyenda normal/compacta, búsqueda resaltada y atajos `G`/`L`/`+`/`-`/flechas.
- Vistas de datos en pestañas: **Tabla**, **Árbol**, **Matriz** (adyacencia dirigida de las 80 entidades de mayor riesgo) y **ATT&CK**, con filtros combinables, orden por columnas, acciones de fila y sincronización con el grafo.

### Datos

- 24 conectores (20 abiertos + 4 comerciales: VirusTotal, Shodan, urlscan.io, URLQuery) con claves API cifradas AES-256-GCM en `~/.orion/`.
- 22 transformaciones tipo Maltego (dominio→DNS/certificados, IP→ASN/geolocalización, hash→muestra, CVE→NVD+KEV, actor→perfil TTP...).
- Motor de correlación con 8 reglas heurísticas y generación automática de hipótesis y acciones sugeridas.
- Riesgo 0-100 explicable con CVSS, EPSS, CISA KEV, VirusTotal, familia de malware, corroboración multifuente, conectividad y tipo de entidad.
- Bundle ATT&CK embebido (~90 técnicas, 14 tácticas) para uso sin conexión; la vista avisa de que es un subconjunto curado.

### Casos e informes

- Casos persistentes en H2 embebida (cero configuración) o PostgreSQL, con guardado/carga del grafo completo y metadatos.
- Matriz ATT&CK con resaltado de técnicas observadas y perfilado de actores.
- 7 tipos de informe × 6 formatos (PDF, HTML, CSV, JSON, PNG, SVG), con marca de agua CONFIDENCIAL en PDF.

### Extensibilidad

- SPI de complementos (`ServiceLoader`): los JARs en `~/.orion/plugins/` registran conectores y transformaciones adicionales.

## Plataformas

| Plataforma | Instalador | Portable |
|---|---|---|
| Windows 10/11 x64 | `Estoa-windows-x64.exe` | `Estoa-windows-x64-portable.zip` |
| Linux x64 (GTK3) | `estoa-linux-x64.deb` | `estoa-linux-x64-portable.tar.gz` |
| macOS 12+ Intel | `Estoa-macos-x64.dmg` | `Estoa-macos-x64-portable.zip` |

Los paquetes incluyen el runtime. Solo se necesita Java 21+ para compilar desde código.

## Requisitos

- Windows 10/11 x64, Linux x64 con GTK3 o macOS 12+ Intel.
- 4 GB de RAM y 200 MB de disco libre, además de los datos locales.
- Sin GPU: el render del grafo es por software.
- Internet opcional: solo para enriquecer fuentes y comprobar actualizaciones.

## Limitaciones conocidas

- Los instaladores v1 no están firmados ni notarizados. Windows SmartScreen y macOS Gatekeeper pueden mostrar advertencias.
- Solo hay builds x64. Apple Silicon funciona con Rosetta; no hay build ARM64 nativo todavía.
- El bundle ATT&CK es un subconjunto curado, no las ~600 técnicas de Enterprise.
- El conector `ip-api` gratuito solo funciona sobre HTTP e IPv4.
- Las actualizaciones se notifican y se abren en el navegador; no hay autoactualización.
- El aviso residual de JavaFX al ejecutar el fat JAR desde consola no afecta a los instaladores.

## QA

- 69 pruebas JUnit 5 en verde (14 suites).
- Smoke gráfico automático: 30 PNG del recorrido completo de la GUI + informe PDF de demostración.
- App-image de Windows validada sin Java externo: arranque, caso demo y 30 capturas.
- Escaneo de los artefactos empaquetados para comprobar que no incluyen rutas, bases locales ni claves del equipo de desarrollo.

## Actualización desde versiones anteriores

- Los casos y claves existentes en `~/.orion/` siguen siendo compatibles.
- Los casos creados por builds antiguos pueden conservar el nombre de usuario del sistema en sus metadatos; los casos nuevos usan `analista`.
- Si usabas los fat JARs y lanzadores de la raíz, se recomienda pasar al instalador o portable. El modo JAR sigue disponible para desarrollo.

## Verificación

Descarga `SHA256SUMS.txt` junto a los artefactos y compara:

```bash
sha256sum -c SHA256SUMS.txt
```

En Windows:

```powershell
Get-FileHash .\Estoa-windows-x64.exe -Algorithm SHA256
```

Desarrollo: [mikear](https://github.com/mikear).
