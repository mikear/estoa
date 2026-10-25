# Manual de usuario

## 1. La interfaz

Al arrancar Estoa encontrarás una ventana única de tema oscuro organizada asi:

- **Barra de menús (12 menús)**: Archivo, Edición, Caso, Entidad, Transformación, Visualización, Fuentes, Análisis, Informes, Complementos, Ventana y Ayuda.
- **Toolbar superior**: campo para añadir observables (detecta el tipo automáticamente), búsqueda (Ctrl+F), selector de layout, ajuste de **Repulsión**, zoom, ajuste de vista y accesos a correlación e informes.
- **Panel izquierdo**: pestañas **Caso** (metadatos, etiquetas y notas), **Entidades** (lista navegable del grafo), **IOC** (indicadores con riesgo >= 40 priorizados) y **Fuentes** (estado de los 24 conectores).
- **Canvas central**: pestañas **Grafo**, **Tabla**, **Árbol** y **ATT&CK** sobre el área de trabajo.
- **Panel derecho**: pestañas **Propiedades** (detalle, factores de riesgo y referencias externas activables de la entidad seleccionada), **Riesgo** (resumen del caso, hallazgos e hipótesis) y **ATT&CK** (cobertura y actores).
- **Barra de estado**: caso activo, tamaño del grafo, estado de la ultima operación, motor de persistencia y reloj.

## 2. Tu primera investigación

### 2.1 Crear el caso

Menú **Archivo > Nuevo caso** o **Caso > Metadatos** para editar nombre, descripción, estado (Abierto / En curso / Cerrado / Archivado), prioridad (Baja a Critica) y etiquetas. La clasificación TLP del caso se usa en los informes.

### 2.2 Añadir observables

Escribe un dominio, IP, hash, URL, CVE, ASN o ID de técnica ATT&CK en el campo del toolbar y pulsa **+ Añadir**. Estoa clasifica el tipo automáticamente y crea el nodo en el grafo. Para tipos sin detección automática (organización, persona, campaña...) usa **Entidad > Añadir por tipo**.

Con `--demo` puedes explorar un caso completo de ejemplo: una campaña de LockBit contra el sector salud con infraestructura C2, certificados CT, muestras, actor, TTPs y una CVE explotada.

### 2.3 Enriquecer (transformaciones)

Selecciona un nodo (clic) y:

- pulsa **Enriquecer** en el panel derecho para lanzar todas las transformaciones aplicables sin clave, o
- usa **Ejecutar todas las transformaciones** (menú contextual o menú **Transformación**) para lanzar también las que usan API,
- abre el menú contextual con **clic derecho** y elige una transformación concreta, o
- usa el menú **Transformación** agrupado por categoría.

Tras cualquier transformación el grafo se reorganiza y se separan las etiquetas para evitar solapamientos. Las entidades nuevas quedan conectadas a la entidad de origen.

Ejemplo de flujo típico con un dominio sospechoso:

1. *Resolver registros DNS*: nodos IP aparecen conectados con "resuelve a".
2. *Datos de registro (RDAP)*: registrador y fechas de alta (un dominio de días de antiguedad ya es una senal).
3. *Descubrir subdominios* y *Certificados CT*: superficie real del adversario; un certificado compartido entre dominios delata al operador.
4. Sobre una IP: *Atribución ASN* (Team Cymru) y *Reputación* (Feodo + ThreatFox + URLHaus).

Las transformaciones se ejecutan en segundo plano; la barra de estado indica el progreso y el grafo se actualiza al terminar.

### 2.4 Explorar el grafo

- **Zoom**: rueda del raton (centrado en el cursor).
- **Paneo**: botón central o `Alt` + arrastrar sobre zona vacía.
- **Mover un nodo**: arrastrarlo (fija su posición temporalmente).
- **Selección**: clic; `Shift` + clic añade o alterna; arrastrar sobre zona vacía dibuja un rectángulo de selección (`Shift` lo suma a la actual). Los nodos no relacionados se atenúan para leer la vecindad. Las etiquetas se priorizan por selección, riesgo y centralidad; el valor completo aparece en el tooltip.
- **Borrar selección**: `Supr` o `Backspace` en el grafo; `Esc` limpia la selección.
- **Disposiciones** (menú Visualización o selector del toolbar): fuerza dirigida, orgánico, circular, rejilla, jerárquico, radial, clústeres y aleatorio. **Ajustar** recoloca la vista al grafo completo.
- **Repulsión** (slider del toolbar): separa más o menos las entidades en los disposiciones de fuerza y orgánico; al soltar, recalcula la disposición.
- **Agrupar por** (menú Visualización): burbujas agregadas por **Tipo**, **Riesgo**, **TLP** o **Comunidad** (Louvain sobre las relaciones). Cada burbuja muestra el número de entidades, los críticos y el riesgo medio. Clic en una burbuja la expande a pantalla completa con sus entidades y aristas internas; el resto queda oculto hasta pulsar Esc (vuelve a la vista agrupada) o **Mostrar todas las entidades**. Los nodos sin grupo propio se reúnen en **Otros**.
- **Filtros del grafo** (menú Visualización): riesgo mínimo (medio/alto/crítico) y TLP por nivel. **Quitar filtros del grafo** restaura todo.
- **Foco de vecindad N1** (menú Visualización o clic derecho sobre un nodo): muestra solo la entidad y sus vecinos directos. **Volver al foco anterior** retrocede en el historial y **Salir del foco** restaura la vista; Esc también sale.
- **Etiquetas de relaciones**: se muestran al pasar el ratón o al seleccionar una arista; el menú Visualización permite mantenerlas visibles.
- **Leyenda**: completa en vista normal y compacta al agrupar; se oculta desde **Mostrar leyenda**.
- **Buscar** (Ctrl+F): resalta en el grafo las coincidencias de la búsqueda global.
- **Separar entidades solapadas** (menú Visualización): fuerza una pasada extra de anti-solapamiento.
- **Pantalla completa** (F11): oculta el marco y amplia el canvas; F11 de nuevo (o Esc) sale.
- **Referencias** (panel Propiedades): lista de enlaces externos derivados de la entidad (NVD/KEV para CVE, ATT&CK para técnicas y actores, crt.sh, urlscan, URL original...). Marca una para anadirla al grafo como nodo URL; desmarcala para retirarla. El botón de ojo oculta o vuelve a mostrar el nodo URL sin borrar la referencia.

### 2.4.1 Vistas de tabla y árbol

La zona central tiene cinco pestañas: **Grafo**, **Tabla**, **Árbol**, **Matriz** y **ATT&CK** (menú **Ventana** o atajo `Ctrl+F` contextual).

**Tabla**: una fila por entidad con columnas **Tipo** (icono y etiqueta), **Valor**, **Riesgo** (coloreado por nivel y con borde lateral), **TLP**, **Fuentes**, **Enlaces** (vecinos unicos) y **Visto** (ultima actualización, con fecha completa en el tooltip). Cualquier columna se ordena con un clic en su cabecera (ascendente/descendente) y las columnas se pueden redimensionar y reordenar arrastrando. La selección de una fila actualiza el panel de propiedades; `Ctrl`/`Shift` permite seleccionar varias.

**Árbol**: jerarquía **tipo de entidad** (con contador) **> entidad** **> relaciones de primer grado** (cada relación muestra su dirección, el vecino y su tipo). Se puede expandir y contraer por tipo o por entidad, elegir el orden (riesgo, valor o número de enlaces) y usar **Expandir todo** / **Contraer todo**; el estado de expansion se conserva al recalcular. También admite selección múltiple con `Ctrl`/`Shift`.

**Filtros comunes** (barra superior de ambas vistas): texto libre que busca en valor, tipo, fuente y atributos dinámicos, filtro por tipo de entidad, por TLP y por nivel de riesgo, más **Limpiar** y un contador "N de M entidades". Los filtros se combinan y se aplican al instante.

**Acciones**: doble clic en una fila o en un nodo lleva al grafo y centra la entidad; clic derecho abre el menú de fila (**Ver en el grafo**, **Copiar valor**, **Enriquecer**, **Eliminar entidades**); `Supr` elimina la selección y `Ctrl+C` copia el valor seleccionado.
- **Buscar** (Ctrl+F) filtra la lista de entidades del panel izquierdo; doble clic sobre una entrada la selecciona en el grafo.
- Los nodos con riesgo crítico muestran un **halo rojo discontinuo**.

### 2.5 Correlacionar

Menú **Análisis > Ejecutar correlación** (o botón **Correlación**). El motor evalúa las 8 reglas habilitadas y el panel derecho (pestaña Riesgo) muestra:

- **Hallazgos**: descripción, regla y confianza. Ejemplos: tres IPs maliciosas en el mismo ASN (infraestructura dedicada de C2), un certificado que cubre varios dominios, un actor que comparte suficientes técnicas con otro como para sospechar alias, o un par de dominios a un carácter de distancia (typosquatting).
- **Hipótesis**: enunciado accionable con confianza y acción sugerida (bloquear netblock, pivotar por certificado, solicitar abuso al registrador...). El estado inicial es *Por validar*; se confirma o descarta al superar o no el umbral de confianza (85%).

**Análisis > Recalcular riesgo** reevalúa todas las entidades después de grandes enriquecimientos. El riesgo 0-100 es explicable: corroboración multifuente (8 puntos por fuente, máximo 24), atributos de amenaza (CVSS, EPSS, CISA KEV, VirusTotal y familia de malware, hasta 50), conectividad en el grafo (hasta 15) y tipo de entidad (hasta 12). El panel **Propiedades** desglosa los cuatro factores.

### 2.6 Matriz ATT&CK

Menú **Análisis > Matriz ATT&CK** o **Ventana > Vista ATT&CK**. Las 14 tácticas ocupan las columnas; cada celda es una técnica, resaltada en azul cuando el caso contiene esa técnica, y la cabecera de columna muestra el contador de técnicas observadas. El panel derecho (pestaña ATT&CK) resume la cobertura y los perfiles de actor con sus técnicas vinculadas.

Para poblar la matriz, añade IDs de técnica (por ejemplo `T1566`) y ejecuta la transformación *Técnica ATT&CK*, o el perfil de un actor con *Perfil del actor (ATT&CK)*. La vista indica que el bundle es un subconjunto curado (~90 técnicas), no las ~600 de Enterprise.

### 2.6.1 Matriz de adyacencia

**Ventana > Vista matriz de adyacencia** o la pestaña **Matriz**. Muestra las 80 entidades de mayor riesgo como filas y columnas; una celda coloreada indica una relación dirigida desde la fila hacia la columna. El tamaño de celda se adapta al número de entidades y al espacio disponible (hasta 240 px por celda, con etiquetas ajustadas), de modo que un caso pequeño no queda diminuto. Es útil para localizar nodos centrales, pivotes y relaciones cruzadas sin recorrer el grafo.

### 2.7 Informes

Menú **Informes > Generador de informes** (o botón **Informes**) y elige tipo y formato:

| Tipo | Uso ideal |
|---|---|
| Resumen ejecutivo | Dirección y CSIRT: métricas, top IOCs e hipótesis |
| Resumen de caso | Documentación del inventario completo |
| Informe de IOCs | Entrega a SOC/blue team para bloqueos |
| Perfil de actor | Ficha del adversario con alias, origen y TTPs |
| Línea temporal de campaña | Cronología de observaciones para cronometraje |
| Superficie de ataque | Activos expuestos con atributos |
| Informe técnico | Todo lo anterior más hallazgos, hipótesis y matriz |

Formatos: **PDF** (con marca de agua CONFIDENCIAL y paginación), **HTML** (autocontenida, para intranet), **CSV** (para SIEM/gestores de IOCs), **JSON** (integración), **PNG** y **SVG** (el grafo vectorial para presentaciones). También puedes exportar la vista actual del grafo desde **Archivo > Exportar grafo (PNG)**.

### 2.8 Guardar y reabrir

- **Ctrl+S** o **Archivo > Guardar caso**: persiste el caso completo (grafo, notas, metadatos) en la base.
- **Archivo > Abrir caso**: lista de casos guardados ordenados por ultima actualización.
- **Archivo > Salir**: autoguardado del caso activo (configurable en menú Caso).

### 2.9 Fuentes y claves API

Menú **Fuentes > Gestion de claves API**: la tabla muestra los 24 conectores con su estado; los comerciales (VirusTotal, Shodan, urlscan.io y URLQuery) aceptan clave (se guarda cifrada, ver INSTALACION.md). La pestaña **Fuentes** del panel izquierdo muestra el mismo inventario de un vistazo.

Las fuentes abiertas funcionan sin clave salvo AbuseCH (ThreatFox, URLHaus y MalwareBazaar), que usa una clave gratuita de `auth.abuse.ch` guardada como `abusech`. Recuerda los límites de tasa (NVD ~5 peticiones/30 s, ip-api 45/min) cuando enriquezcas en lote.

## 3. Flujos recomendados

**Investigación de infraestructura de phishing**
Dominio -> subdominios + certificados CT -> pivota por el certificado -> IPs -> ASN y reputación -> correlación (regla de certificado compartido y typosquatting) -> informe de IOC al SOC.

**Análisis de muestra**
Hash -> MalwareBazaar (familia y etiquetas) -> perfil del actor ATT&CK -> CVEs explotadas -> NVD+KEV -> matriz del caso -> informe técnico.

**Caso de ransomware**
Víctima (organización) -> ransomware.live -> actor y campaña -> TTPs del actor -> superficie de ataque del dominio víctima -> hipótesis de acceso inicial -> resumen ejecutivo para dirección.

## 4. Atajos de teclado

| Atajo | Acción |
|---|---|
| Ctrl+F | Buscar en el grafo, o en la tabla/árbol si esa pestaña está activa |
| Ctrl+C | Copiar el valor de la fila o nodo seleccionado (tabla/árbol) |
| Ctrl+S | Guardar caso |
| F11 | Pantalla completa (salir con F11 o Esc) |
| Esc | Plegar el grupo expandido o salir del foco N1 |
| Enter | Enfocar la vecindad N1 del nodo seleccionado |
| G | Activar/desactivar la agrupación |
| L | Mostrar/ocultar la leyenda |
| + / - | Acercar / alejar |
| Flechas | Desplazar la vista (fuera de la agrupación) |
| Clic derecho sobre nodo | Menú de transformaciones, foco N1 y eliminación |
| Arrastrar nodo | Recolocar manualmente (opcionalmente con sus vecinos N1) |
| Arrastrar sobre vacío | Rectángulo de selección múltiple |
| Botón central o Alt + arrastrar | Paneo del grafo |
| Shift + clic | Añadir o alternar un nodo en la selección |
| Supr / Backspace | Eliminar la selección del grafo, tabla o árbol |
| Rueda del raton | Zoom centrado en el cursor |

## 5. Donde viven tus datos

| Ruta | Contenido |
|---|---|
| `~/.orion/orion.db` | Base H2 con todos los casos (si usas el perfil H2) |
| `~/.orion/keys.dat` | Claves API cifradas (AES-256-GCM) |
| `~/.orion/.keystore.key` | Clave maestra del almacen (permisos 0600) |
| `~/.orion/natives/` | Librerías nativas de JavaFX extraidas |
| `~/.orion/plugins/` | JARs de complementos discoverables |

Copia de seguridad de un analista H2 = copiar `~/.orion/`. En PostgreSQL, cualquier `pg_dump` estándar.
