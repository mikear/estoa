# Arquitectura de Estoa

## Vison general

Estoa es una aplicación de escritorio modular construida sobre Java 21, con Spring Boot como capa de composición y JavaFX como toolkit gráfico. El diseño sigue tres principios:

1. **Modularidad estricta**: diez módulos Maven con dependencias aciclicas; cada módulo se puede probar y evolucionar por separado.
2. **Fuentes como complementos de primera clase**: todo conector de datos implementa la misma interfaz, sea abierto o comercial, integrado o aportado por un complemento externo.
3. **El grafo como centro de gravedad**: toda la investigación vive en un grafo de entidades deduplicadas por identidad canónica `tipo:valor`.

```
+------------------------------------------------------------------+
|                        orion-app (JavaFX)                        |
|   MainWindow - GraphCanvas - AttackMatrixView - Dialogos - CSS   |
+---------------------------+--------------------------------------+
                            |
+---------------------------v---------------------+  +-------------+
|              orion-intel (orquestacion)         |  | orion-      |
|  IntelService - RiskScoring - AttackMatrix      |  | reports     |
+-----+------------------+-----------+------------+  | 7 tipos x   |
      |                  |           |               | 6 formatos  |
+-----v------+  +--------v---+  +----v---------+     +-----+-------+
| orion-     |  | orion-     |  | orion-       |           |
| transform  |  | correlation|  | persistence  |           |
| 22 transfs |  | 8 reglas + |  | H2/PostgreSQL|           |
+-----+------+  | hipotesis  |  +--------------+           |
      |         +------------+                             |
+-----v------------------+                    +------------v-------+
| orion-sources         |<------------------- | orion-plugin (SPI) |
| 24 conectores reales  |                     | ServiceLoader      |
+-----+-----------------+                     +--------------------+
      |
+-----v-------------------+
| orion-graph            |
| layouts, clustes,      |
| anti-solapamiento      |
+-----+-----------------+
      |
+-----v----------+
| orion-core     |
| 28 entidades   |
+----------------+
```

## Módulos

### orion-core
Modelo de dominio puro, sin dependencias de UI ni de red. Define los **28 tipos de entidad** (`EntityType`, con color canónico por tipo), la entidad `OrionEntity` con atributos dinámicos (los conectores añaden los que devuelven sus APIs), relaciones (`RelationshipType`, 23 tipos), el ciclo de vida del caso (`Investigation`, estados y prioridad), IOCs con confianza y el modelo de riesgo explicable (`RiskScore` con factores). `NetUtil` clasifica observables en texto libre (IP, dominio, hash, URL, CVE, ASN, técnica ATT&CK) mediante expresiones regulares y heurística de dominio registrable.

### orion-graph
Motor de grafo y fisica de disposición. `OrionGraph` indexa nodos por identidad canónica y deduplica entidades y aristas, de modo que el mismo hallazgo obtenido de tres fuentes se fusiona en un único nodo con la union de atributos y fuentes. Incluye detección de clústeres por componentes conexas (union-find), comunidades Louvain deterministas (`CommunityDetector`), ocho disposiciones (`LayoutType`) con la implementación destacada de `ForceDirectedLayout`: repulsión mediante **aproximación Barnes-Hut con quadtree** (O(n log n)), muelles sobre aristas, gravedad central y enfriamiento. `CollisionResolver` separa cajas AABB de nodos solapados y expone una metrica de solapamiento usada por los tests.

### orion-sources
Los conectores de datos. `SourceConnector` declara operaciones soportadas (`Operation`), categoría y si requiere clave. Cada conector traduce su API concreta al modelo Estoa y **nunca lanza excepciones**: devuelve `SourceResult` con ok=false y mensaje. Los feeds voluminosos (Feodo, OpenPhish, KEV, ransomware.live) usan `TtlCache` con expiración. `ApiKeyManager` cifra las claves con **AES-256-GCM** (IV aleatorio por entrada) y clave maestra derivada con PBKDF2; el archivo en disco nunca contiene material legible. El bundle **MITRE ATT&CK** (14 tácticas, ~90 técnicas, 13 grupos con aliases y TTPs) viaja embebido como recurso para operar sin conexión.

### orion-transform
El motor de transformaciones tipo Maltego. Cada `Transform` declara tipos de entrada/salida y categoría (enriquecimiento, TI, infraestructura, historico, comercial). La implementación `collect` de `DomainTransforms` agrega los resultados de todos los conectores capaces de resolver las operaciones de la transformación, con tolerancia a fallos por conector. El registro indexa por tipo de entrada para poblar menús contextuales.

### orion-persistence
JDBC puro sobre H2 o PostgreSQL con esquema compatible con ambos dialectos (MERGE vs ON CONFLICT según el motor detectado). El grafo completo del caso se serializa como documento JSON (entidades con atributos, aristas por clave canónica, notas y metadatos) junto a columnas de listado para consultas rapidas. Sin ORM: la intención es minimizar dependencias y mantener el SQL auditable.

### orion-correlation
Motor de 8 reglas heurísticas evaluadas sobre el grafo. Cada regla produce `Finding`s con confianza y evidencias (claves de entidades); el motor sintetiza `Hypothesis` con enunciado, estado (confirmada por encima del 85% de confianza, por validar o descartada) y **acción sugerida** contextual por regla. Las reglas se pueden habilitar/deshabilitar individualmente.

### orion-plugin
SPI basado en `ServiceLoader`. Un complemento registra conectores y transformaciones en carga; se descubren en el classpath y en `~/.orion/plugins/*.jar` (URLClassLoader aislado por JAR). La filosofia es que cualquier fuente nueva —interna o de un tercero— entre por la misma puerta.

### orion-intel
La capa de orquestación. `IntelService` posee el caso activo y su grafo, aplica transformaciones fusionando resultados (resolución de aristas por valor canónico, creación de nodos implicitos, reescala de riesgo), y delega en `PersistenceService`. `RiskScoringService` calcula el riesgo 0-100 con cuatro factores explicables y pesos `.24/.50/.15/.11`: corroboración (8/fuente, tope 24), amenaza (CVSS, EPSS, CISA KEV, VirusTotal y familia de malware; tope 50), conectividad (3/arista, tope 15) y tipo de entidad (2-12). `AttackMatrixService` construye la matriz de cobertura y los perfiles de actor.

### orion-reports
Generación de informes desacoplada de la UI. `ReportModel` consolida caso, grafo, riesgo, matriz, hallazgos e hipótesis; los exportadores lo materializan en **PDF** (OpenPDF, marca de agua CONFIDENCIAL, paginación, tablas), **HTML** (plantilla autocontenida con KPIs), **CSV** (escapado seguro), **JSON** (grafo completo), y **PNG/SVG** del grafo (render AWT headless y construcción SVG, reutilizando el motor de disposiciones).

### orion-app
La aplicación: Spring Boot (`headless(false)`, banner off) lanza JavaFX. `MainWindow` compone la interfaz de 12 menús, toolbar, tres paneles laterales, pestañas centrales (Grafo, Tabla, Árbol, Matriz, ATT&CK) y barra de estado. Las vistas de datos comparten filtros por texto (valor, tipo, fuente y atributos), tipo, TLP y nivel de riesgo; la tabla ordena por cualquier columna y el árbol agrupa por tipo, permite expandir relaciones y conserva el estado de expansion. `GraphCanvas` es un canvas de render directo: transformación afín (zoom a cursor, paneo), curvas de aristas con etiquetas, halos de riesgo crítico, selección múltiple por rectángulo con realce de vecindad y animación continua del layout; además ofrece agrupación expandible por tipo/riesgo/TLP/comunidad, foco ego N1 con historial, filtros de riesgo/TLP, etiquetas priorizadas con tooltip, leyenda adaptativa y atajos. `AdjacencyMatrixView` resume las 80 entidades de mayor riesgo en una matriz dirigida y delega el dimensionado adaptativo en `MatrixLayout`. `ScreenshotMode` implementa el modo de captura automática usado también como prueba de humo grafica en integración continua (`--demo --screenshot dir`).

## Modelo de datos

```
Investigation 1 --- * OrionEntity (28 tipos, atributos dinamicos, TLP, riesgo)
                1 --- * GraphEdge   (23 relaciones)
                1 --- * notas / etiquetas
OrionEntity ... fuente por OrionSource (24 conectores)
RiskScore = f(multifuente, atributos de amenaza, conectividad, tipo)
Finding -> Hypothesis (por regla de correlacion)
```

Identidad canónica: `TIPO:valor-normalizado` (minúsculas). Un mismo indicador enriquecido por cinco fuentes es un nodo con cinco fuentes registradas; la confianza y el riesgo crecen con la corroboración independiente.

## Decisiones de diseño relevantes

- **Deduplicación por identidad canónica** frente a identidad por instancia: los resultados de fuentes solapadas se fusionan en lugar de duplicar nodos, que es lo que espera un analista que pivota sobre Maltego o OpenCTI.
- **Sin ORM**: SQL mínimo y auditable, esquema idéntico en H2 y PostgreSQL, cero magia de mapeo para un modelo (atributos dinámicos) que no encaja bien en tabulas fijas.
- **Conectores que nunca lanzan**: la resiliencia ante caídas de fuentes públicas es un requisito de producto, no un refinamiento.
- **Claves cifradas localmente**: la posición de Estoa es que las credenciales del analista no deben salir de su equipo ni aparecer en exportaciones.
- **ATT&CK embebido**: la matriz debe funcionar en redes aisladas; el subconjunto incluye las tácticas completas y las técnicas y grupos más relevantes, y es ampliable sin tocar código (recurso JSON versionado).

## Pruebas

72 tests JUnit 5 distribuidos por módulo cubren: conteo y clasificación del modelo, deduplicación de grafo, los 8 disposiciones (sin NaN, dentro de rango), reducción de solapamientos, clústeres, comunidades Louvain deterministas, registro e indexación de conectores y transformaciones, bundle ATT&CK embebido (tácticas, técnicas, alias de grupos), cifrado en reposo del almacen de claves, roundtrip completo de persistencia (H2 en memoria), las reglas de correlación con grafos sinteticos, riesgo con factores (incluidos CVSS/EPSS/KEV), los seis formatos de informe sobre un caso de prueba (incluida la firma binaria de PNG y el trailer de PDF), la retirada de aristas sin perder nodos, la derivación de referencias externas, el dimensionado adaptativo de la matriz de adyacencia y los filtros combinados de las vistas de datos.

## Limitaciones conocidas

- El bundle ATT&CK es un subconjunto curado (~90 técnicas de las ~600 de Enterprise); la actualización en línea queda como trabajo futuro.
- ip-api gratuito solo funciona sobre HTTP y IPv4; el conector rechaza IPv6.
- La resolución inversa (IP a dominio) no tiene fuente abierta fiable integrada; se cubre indirectamente via URLHaus por host.
- El render de grafo es software (prism sw) priorizando portabilidad sobre aceleración GPU.
