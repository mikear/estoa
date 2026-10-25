# Estoa Threat Intelligence Platform

**Versión 1.0.0** · Plataforma de escritorio para inteligencia de amenazas, investigación cibernética, análisis de filtraciones, enriquecimiento de IOCs y análisis de enlaces (link analysis), construida con Java 21, Spring Boot y JavaFX.

Estoa permite a un analista crear un caso de investigación, añadir observables (dominios, IP, hashes, URLs, CVE, actores...), enriquecerlos mediante 24 conectores de datos reales, explorar la infraestructura del adversario como un grafo interactivo, correlacionar hallazgos con 8 reglas heurísticas, generar hipótesis y exportar informes profesionales en 6 formatos. Todo el ciclo de investigación en una sola aplicación, con persistencia local y claves de API cifradas.

## Capacidades principales

| Área | Detalle |
|---|---|
| **Modelo de entidades** | 28 tipos (dominio, IPv4/IPv6, ASN, netblock, hash, certificado, malware, APT, campaña, CVE, técnica/táctica ATT&CK, organización...) con atributos dinámicos, TLP y riesgo 0-100 |
| **Conectores abiertos (20)** | DNS sobre HTTPS (Google/Cloudflare), RDAP, crt.sh, ip-api, Team Cymru, ThreatFox, URLHaus, MalwareBazaar, Feodo Tracker, OpenPhish, ransomware.live, RansomLook, NVD, CISA KEV, EPSS, MITRE ATT&CK (bundle embebido), Shodan InternetDB, Site Probe, HIBP y Hudson Rock |
| **Conectores comerciales (4)** | VirusTotal, Shodan, urlscan.io y URLQuery con claves cifradas AES-256-GCM en disco |
| **Transformaciones (22)** | Tipo Maltego: dominio a DNS/subdominios/certificados, IP a ASN/geolocalización/reputación, hash a muestra, CVE a NVD+KEV, actor a perfil TTP... |
| **Grafo interactivo** | Canvas JavaFX con zoom a cursor, paneo, arrastre de nodos (opcionalmente con sus vecinos N1), selección múltiple por rectángulo, borrado con Supr, realce de vecindad, 8 disposiciones (fuerza dirigida Barnes-Hut, orgánico, circular, rejilla, jerárquico, radial, clústeres, aleatorio), anti-solapamiento, repulsión ajustable, agrupación expandible por tipo/riesgo/TLP/comunidad, foco de vecindad N1 con historial, filtros de riesgo/TLP, etiquetas con prioridad y tooltip, leyenda, búsqueda resaltada, pantalla completa (F11) y referencias externas activables con visibilidad por referencia |
| **Vistas de datos** | Pestañas centrales **Tabla** (columnas ordenables por tipo, valor, riesgo, TLP, fuentes, enlaces y fecha; color por riesgo) y **Árbol** (tipos de entidad, entidades y relaciones de primer grado), ambas con selección múltiple, borrado con Supr, filtros combinables por texto, tipo, TLP y nivel de riesgo, contador de resultados, doble clic para centrar en el grafo y menú de fila (ver en grafo, copiar, enriquecer, eliminar) |
| **Motor de correlación** | 8 reglas (infraestructura ASN común, certificado compartido, alta en cascada de dominios, confirmación multifuente, linaje de muestras, solapamiento TTP, typosquatting, colocación geografica) con generación automática de hipótesis y acciones sugeridas |
| **Puntuación de riesgo** | Modelo compuesto explicable: confirmación multifuente, atributos de amenaza (CVSS, EPSS, CISA KEV, VirusTotal, familia de malware), conectividad y tipo de entidad |
| **Matriz ATT&CK** | 14 tácticas, ~90 técnicas embebidas con resaltado de las observadas en el caso y perfiles de actores |
| **Matriz de adyacencia** | Pestaña con las 80 entidades de mayor riesgo y sus relaciones dirigidas; el tamaño de celda se adapta al caso, útil para detectar pivotes y nodos centrales |
| **Informes (7 tipos x 6 formatos)** | Resumen ejecutivo, resumen de caso, informe de IOCs, perfil de actor, línea temporal, superficie de ataque e informe técnico; exportación a PDF (marca de agua CONFIDENCIAL), HTML, CSV, JSON, PNG y SVG |
| **Persistencia** | H2 embebida por defecto (cero configuración) y perfil PostgreSQL para despliegues multi-analista |
| **Complementos** | SPI basado en ServiceLoader: los complementos registran conectores y transformaciones desde `~/.orion/plugins/*.jar` |

## Instalación

Descarga el instalador o la versión portable desde [github.com/mikear/estoa/releases/latest](https://github.com/mikear/estoa/releases/latest). Los paquetes incluyen su propio runtime: **no hace falta instalar Java**.

| Plataforma | Instalador | Portable |
|---|---|---|
| Windows x64 | `Estoa-windows-x64.exe` | `Estoa-windows-x64-portable.zip` |
| Linux x64 | `estoa-linux-x64.deb` | `estoa-linux-x64-portable.tar.gz` |
| macOS Intel | `Estoa-macos-x64.dmg` | `Estoa-macos-x64-portable.zip` |

La v1 no está firmada; Windows SmartScreen o macOS Gatekeeper pueden pedir confirmación. Apple Silicon funciona con Rosetta. Detalles por plataforma en [INSTALACION.md](INSTALACION.md).

## Arranque

```text
Estoa --demo     # caso de demostración completo
Estoa            # aplicación normal
```

Estoa comprueba si hay una release nueva como máximo una vez al día y ofrece abrir la página de descarga; nunca instala automáticamente. Se puede desactivar con `--no-update-check`.

Para compilar desde código se requiere JDK 21+ y Maven. Detalles en [INSTALACION.md](INSTALACION.md).

## Documentación

- [INSTALACION.md](INSTALACION.md) — requisitos, instalación, base de datos y claves API
- [ARQUITECTURA.md](ARQUITECTURA.md) — módulos, modelo de datos y decisiones de diseño
- [MANUAL-USUARIO.md](MANUAL-USUARIO.md) — guía completa de uso con flujos de investigación
- `capturas/` — pantallas reales de la aplicación en ejecución

## Aviso legal y de uso

Estoa es una herramienta para profesionales de seguridad autorizados. Las consultas a fuentes públicas deben respetar sus términos de servicio y la legislación aplicable. Los datos obtenidos de terceros pertenecen a sus respectivos proveedores (AbuseCH, MITRE, NIST, CISA, Team Cymru...). El bundle ATT&CK embebido es un subconjunto del proyecto MITRE ATT&CK (Copyright The MITRE Corporation, CC BY 4.0).

Desarrollo: [mikear](https://github.com/mikear).
