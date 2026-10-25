#!/usr/bin/env python3
"""Pasada ortografica assistida para ORION.

Aplica un diccionario de correcciones solo en:
  - literales de cadena de Java (y bloques de texto)
  - valores de cadena de JSON
  - texto de Markdown/TXT, saltando bloques de codigo y codigo en linea

Uso:
  python tools/qa/accent_pass.py --report      # solo muestra los cambios
  python tools/qa/accent_pass.py --apply       # escribe los archivos
"""
import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

TECH_MARKERS = ("://", "=", "|", "\\", ".*", "^", "$", "?fields", "java.", "SELECT ", "select ")

DOC_MAP = {
    "Plugins": "Complementos",
    "plugins": "complementos",
    "Plugin": "Complemento",
    "plugin": "complemento",
    "Screenshot": "Captura",
    "screenshots": "capturas",
    "Layouts": "Disposiciones",
    "layouts": "disposiciones",
    "afin": "afín",
}

MAP = {
    # UI y textos generales
    "Edicion": "Edición", "edicion": "edición", "Ediciones": "Ediciones",
    "Criticos": "Críticos", "criticos": "críticos", "Critico": "Crítico", "critico": "crítico",
    "Analisis": "Análisis", "analisis": "análisis",
    "Informacion": "Información", "informacion": "información",
    "Configuracion": "Configuración", "configuracion": "configuración",
    "Opcion": "Opción", "opcion": "opción",
    "Sesion": "Sesión", "sesion": "sesión",
    "Version": "Versión", "version": "versión",
    "Aplicacion": "Aplicación", "aplicacion": "aplicación",
    "Generacion": "Generación", "generacion": "generación",
    "Exportacion": "Exportación", "exportacion": "exportación",
    "Deteccion": "Detección", "deteccion": "detección",
    "Atribucion": "Atribución", "atribucion": "atribución",
    "Reputacion": "Reputación", "reputacion": "reputación",
    "Busqueda": "Búsqueda", "busqueda": "búsqueda", "busquedas": "búsquedas",
    "Geolocalizacion": "Geolocalización", "geolocalizacion": "geolocalización",
    "Geografico": "Geográfico", "geografico": "geográfico",
    "Colocacion": "Colocación", "colocacion": "colocación",
    "Ejecucion": "Ejecución", "ejecucion": "ejecución",
    "Transformacion": "Transformación", "transformacion": "transformación",
    "Correlacion": "Correlación", "correlacion": "correlación",
    "Sincronizacion": "Sincronización", "sincronizacion": "sincronización",
    "Reorganizacion": "Reorganización", "reorganizacion": "reorganización",
    "Duplicacion": "Duplicación", "duplicacion": "duplicación",
    "Clasificacion": "Clasificación", "clasificacion": "clasificación",
    "Deduplicacion": "Deduplicación", "deduplicacion": "deduplicación",
    "Serializacion": "Serialización", "serializacion": "serialización",
    "Integracion": "Integración", "integracion": "integración",
    "Descripcion": "Descripción", "descripcion": "descripción",
    "Paginacion": "Paginación", "paginacion": "paginación",
    "Documentacion": "Documentación", "documentacion": "documentación",
    "Finalizacion": "Finalización", "finalizacion": "finalización",
    "Anotacion": "Anotación", "anotacion": "anotación",
    "Navegacion": "Navegación", "navegacion": "navegación",
    "Criptografia": "Criptografía", "criptografia": "criptografía",
    "Coneccion": "Conexión", "conexion": "conexión",
    "Peticion": "Petición", "peticion": "petición",
    "Seleccion": "Selección", "seleccion": "selección",
    "Posicion": "Posición", "posicion": "posición",
    "Revision": "Revisión", "revision": "revisión",
    "Resolucion": "Resolución", "resolucion": "resolución",
    "Confirmacion": "Confirmación", "confirmacion": "confirmación",
    "Explotacion": "Explotación", "explotacion": "explotación",
    "Cronologia": "Cronología", "cronologia": "cronología",
    "Supervision": "Supervisión", "supervision": "supervisión",
    "Justificacion": "Justificación", "justificacion": "justificación",
    "Monitorizacion": "Monitorización", "monitorizacion": "monitorización",
    "Correlacionado": "Correlacionado",
    "Actualizacion": "Actualización", "actualizacion": "actualización",
    "Campana": "Campaña", "campana": "campaña", "Campanas": "Campañas", "campanas": "campañas",
    "Pais": "País", "pais": "país",
    "Organizacion": "Organización", "organizacion": "organización",
    "Campanas": "Campañas",
    "Tecnica": "Técnica", "tecnica": "técnica",
    "Tecnicas": "Técnicas", "tecnicas": "técnicas",
    "Tactica": "Táctica", "tactica": "táctica",
    "Tacticas": "Tácticas", "tacticas": "tácticas",
    "Publico": "Público", "publico": "público",
    "Publicos": "Públicos", "publicos": "públicos",
    "Autonomo": "Autónomo", "autonomo": "autónomo",
    "Segun": "Según", "segun": "según",
    "Patron": "Patrón", "patron": "patrón",
    "Tipico": "Típico", "tipico": "típico",
    "Tipica": "Típica", "tipica": "típica",
    "Minima": "Mínima", "minima": "mínima",
    "Caracter": "Carácter", "caracter": "carácter",
    "Parametro": "Parámetro", "parametro": "parámetro",
    "Parametros": "Parámetros", "parametros": "parámetros",
    "Codigo": "Código", "codigo": "código",
    "Numero": "Número", "numero": "número",
    "Ultimo": "Último", "ultimo": "último",
    "Unico": "Único", "unico": "único",
    "Util": "Útil", "util": "útil",
    "Facil": "Fácil", "facil": "fácil",
    "Dificil": "Difícil", "dificil": "difícil",
    "Rapido": "Rápido", "rapido": "rápido",
    "Rapida": "Rápida", "rapida": "rápida",
    "Proximo": "Próximo", "proximo": "próximo",
    "Maximo": "Máximo", "maximo": "máximo",
    "Minimo": "Mínimo", "minimo": "mínimo",
    "Tamano": "Tamaño", "tamano": "tamaño",
    "Tamanos": "Tamaños", "tamanos": "tamaños",
    "Automatico": "Automático", "automatico": "automático",
    "Automatica": "Automática", "automatica": "automática",
    "Automaticamente": "Automáticamente", "automaticamente": "automáticamente",
    "Logica": "Lógica", "logica": "lógica",
    "Analitica": "Analítica", "analitica": "analítica",
    "Analiticas": "Analíticas", "analiticas": "analíticas",
    "Metricas": "Métricas", "metricas": "métricas",
    "Grafico": "Gráfico", "grafico": "gráfico",
    "Graficos": "Gráficos", "graficos": "gráficos",
    "Heuristica": "Heurística", "heuristica": "heurística",
    "Heuristicas": "Heurísticas", "heuristicas": "heurísticas",
    "Hipotesis": "Hipótesis", "hipotesis": "hipótesis",
    "Tambien": "También", "tambien": "también",
    "Despues": "Después", "despues": "después",
    "Ademas": "Además", "ademas": "además",
    "Aqui": "Aquí", "aqui": "aquí",
    "Anade": "Añade", "anade": "añade",
    "Anadir": "Añadir", "anadir": "añadir",
    "Anadido": "Añadido", "anadido": "añadido",
    "Anadidos": "Añadidos", "anadidos": "añadidos",
    "Anadidas": "Añadidas", "anadidas": "añadidas",
    "Menu": "Menú", "menu": "menú",
    "Menus": "Menús", "menus": "menús",
    "Boton": "Botón", "boton": "botón",
    "Dialogo": "Diálogo", "dialogo": "diálogo",
    "Pestana": "Pestaña", "pestana": "pestaña",
    "Pestanas": "Pestañas", "pestanas": "pestañas",
    "Arbol": "Árbol", "arbol": "árbol",
    "Linea": "Línea", "linea": "línea",
    "Lineas": "Líneas", "lineas": "líneas",
    "Direccion": "Dirección", "direccion": "dirección",
    "Graficas": "Gráficas", "graficas": "gráficas",
    "Unico": "Único",
    "Unicamente": "Únicamente", "unicamente": "únicamente",
    "Multiples": "Múltiples", "multiples": "múltiples",
    "Estandar": "Estándar", "estandar": "estándar",
    "Regimen": "Régimen", "regimen": "régimen",
    "Basico": "Básico", "basico": "básico",
    "Practica": "Práctica", "practica": "práctica",
    "Practicas": "Prácticas", "practicas": "prácticas",
    "Explicita": "Explícita", "explicita": "explícita",
    "Explicito": "Explícito", "explicito": "explícito",
    "Implicito": "Implícito", "implicito": "implícito",
    "Propósito": "Propósito", "proposito": "propósito",
    "Energia": "Energía", "energia": "energía",
    "Tecnologia": "Tecnología", "tecnologia": "tecnología",
    "Categoria": "Categoría", "categoria": "categoría",
    "Categorias": "Categorías", "categorias": "categorías",
    "Sintoma": "Síntoma", "sintoma": "síntoma",
    "Periodo": "Período", "periodo": "período",
    "Politica": "Política", "politica": "política",
    "Auditoria": "Auditoría", "auditoria": "auditoría",
    "Canonica": "Canónica", "canonica": "canónica",
    "Minusculas": "Minúsculas", "minusculas": "minúsculas",
    "Carga util": "Carga útil",
    "publicos": "públicos",
    "Proximos": "Próximos", "proximos": "próximos",
    "Proximas": "Próximas", "proximas": "próximas",
    "Dias": "Días", "dias": "días",
    "Espana": "España", "espana": "España",
    "Ingles": "Inglés", "ingles": "inglés",
    "Instalacion": "Instalación", "instalacion": "instalación",
    "Verificacion": "Verificación", "verificacion": "verificación",
    "Extraccion": "Extracción", "extraccion": "extracción",
    "Operacion": "Operación", "operacion": "operación",
    "Migracion": "Migración", "migracion": "migración",
    "Publicacion": "Publicación", "publicacion": "publicación",
    "Compilacion": "Compilación", "compilacion": "compilación",
    "Distribucion": "Distribución", "distribucion": "distribución",
    "Desinstalacion": "Desinstalación", "desinstalacion": "desinstalación",
    "Librerias": "Librerías", "librerias": "librerías",
    "Libreria": "Librería", "libreria": "librería",
    "Jerarquia": "Jerarquía", "jerarquia": "jerarquía",
    "Jerarquico": "Jerárquico", "jerarquico": "jerárquico",
    "Jerarquica": "Jerárquica", "jerarquica": "jerárquica",
    "Modulos": "Módulos", "modulos": "módulos",
    "Modulo": "Módulo", "modulo": "módulo",
    "Guia": "Guía", "guia": "guía",
    "Encontraras": "Encontrarás", "encontraras": "encontrarás",
    "Unica": "Única", "unica": "única",
    "Atenuan": "Atenúan", "atenuan": "atenúan",
    "Evalua": "Evalúa", "evalua": "evalúa",
    "Reevalua": "Reevalúa", "reevalua": "reevalúa",
    "Titulo": "Título", "titulo": "título",
    "Formula": "Fórmula", "formula": "fórmula",
    "Excepcion": "Excepción", "excepcion": "excepción",
    "Precision": "Precisión", "precision": "precisión",
    "Dimension": "Dimensión", "dimension": "dimensión",
    "Conversion": "Conversión", "conversion": "conversión",
    "Valida": "Válida", "valida": "válida",
    "Valido": "Válido", "valido": "válido",
    "Validas": "Válidas", "validas": "válidas",
    "Validos": "Válidos", "validos": "válidos",
    "Usara": "Usará", "usara": "usará",
    "Usaran": "Usarán", "usaran": "usarán",
    "Copialo": "Cópialo",
    "Mas": "Más", "mas": "más",
    "esta": "está",
    "Arboles": "Árboles", "arboles": "árboles",
    "Repulsion": "Repulsión", "repulsion": "repulsión",
    "Disposicion": "Disposición", "disposicion": "disposición",
    "Organico": "Orgánico", "organico": "orgánico",
    "Clusteres": "Clústeres", "clusters": "clústeres",
    "Analista": "Analista",
    "Accion": "Acción", "accion": "acción",
    "Acciones": "Acciones", "acciones": "acciones",
    "Identicos": "Idénticos", "identicos": "idénticos",
    "Especificos": "Específicos", "especificos": "específicos",
    "Limites": "Límites", "limites": "límites",
    "Retro-hunting": "búsqueda retrospectiva",
    "retro-hunting": "búsqueda retrospectiva",
    "Threat Actor": "actor de amenaza",
    "Threat Hunting": "caza de amenazas",
    "Campos de": "Campos de",
    "monitorizacion": "monitorización",
    "Deteccion": "Detección",
    "Pais": "País",
    # Faltantes detectados en docs (2026-10-24)
    "Aceleracion": "Aceleración", "aceleracion": "aceleración",
    "Animacion": "Animación", "animacion": "animación",
    "Aproximacion": "Aproximación", "aproximacion": "aproximación",
    "Campania": "Campaña", "campania": "campaña", "Campanias": "Campañas", "campanias": "campañas",
    "Canonico": "Canónico", "canonico": "canónico",
    "Composicion": "Composición", "composicion": "composición",
    "Comprobacion": "Comprobación", "comprobacion": "comprobación",
    "Construccion": "Construcción", "construccion": "construcción",
    "Corroboracion": "Corroboración", "corroboracion": "corroboración",
    "Creacion": "Creación", "creacion": "creación",
    "Demostracion": "Demostración", "demostracion": "demostración",
    "Derivacion": "Derivación", "derivacion": "derivación",
    "Dinamico": "Dinámico", "dinamico": "dinámico",
    "Dinamicos": "Dinámicos", "dinamicos": "dinámicos",
    "Dinamica": "Dinámica", "dinamica": "dinámica",
    "Dinamicas": "Dinámicas", "dinamicas": "dinámicas",
    "Eliminacion": "Eliminación", "eliminacion": "eliminación",
    "Expiracion": "Expiración", "expiracion": "expiración",
    "Filtracion": "Filtración", "filtracion": "filtración",
    "Implementacion": "Implementación", "implementacion": "implementación",
    "Indexacion": "Indexación", "indexacion": "indexación",
    "Intencion": "Intención", "intencion": "intención",
    "Investigacion": "Investigación", "investigacion": "investigación",
    "Investigaciones": "Investigaciones", "investigaciones": "investigaciones",
    "Legislacion": "Legislación", "legislacion": "legislación",
    "Limitacion": "Limitación", "limitacion": "limitación",
    "Orquestacion": "Orquestación", "orquestacion": "orquestación",
    "Presentacion": "Presentación", "presentacion": "presentación",
    "Puntuacion": "Puntuación", "puntuacion": "puntuación",
    "Reduccion": "Reducción", "reduccion": "reducción",
    "Relacion": "Relación", "relacion": "relación",
    "Solucion": "Solución", "solucion": "solución",
    "Victima": "Víctima", "victima": "víctima", "Victimas": "Víctimas", "victimas": "víctimas",
    "Visualizacion": "Visualización", "visualizacion": "visualización",
    "Evaluacion": "Evaluación", "evaluacion": "evaluación",
    "Correccion": "Corrección", "correccion": "corrección",
    "Combinacion": "Combinación", "combinacion": "combinación",
    "Conexion": "Conexión", "conexion": "conexión",
    "Explicacion": "Explicación", "explicacion": "explicación",
    "Restriccion": "Restricción", "restriccion": "restricción",
    "Produccion": "Producción", "produccion": "producción",
    "Especifico": "Específico", "especifico": "específico",
    "Especifica": "Específica", "especifica": "específica",
    "Logico": "Lógico", "logico": "lógico",
    "Empirico": "Empírico", "empirico": "empírico",
    "Tactico": "Táctico", "tactico": "táctico",
    "Invalido": "Inválido", "invalido": "inválido",
    "Simultaneo": "Simultáneo", "simultaneo": "simultáneo",
    "Division": "División", "division": "división",
    "Seccion": "Sección", "seccion": "sección",
    "Coleccion": "Colección", "coleccion": "colección",
    "Vision": "Visión", "vision": "visión",
    "Decision": "Decisión", "decision": "decisión",
    "Condicion": "Condición", "condicion": "condición",
    "Extension": "Extensión", "extension": "extensión",
    "Identico": "Idéntico", "identico": "idéntico",
    "Identica": "Idéntica", "identica": "idéntica",
    "Anaden": "Añaden", "anaden": "añaden",
    "Anadida": "Añadida", "anadida": "añadida",
    "Diseno": "Diseño", "diseno": "diseño",
    "Clustes": "Clústeres", "clustes": "clústeres",
    "Publicas": "Públicas", "publicas": "públicas",
    "Area": "Área", "area": "área", "Areas": "Áreas", "areas": "áreas",
    "Tecnico": "Técnico", "tecnico": "técnico",
    "Cibernetica": "Cibernética", "cibernetica": "cibernética",
    "Comun": "Común", "comun": "común",
    "Terminos": "Términos", "terminos": "términos",
    "Caidas": "Caídas", "caidas": "caídas",
    "Veras": "Verás", "veras": "verás",
}

PHRASES = {
    "Threat Actor": "actor de amenazas",
    "Threat Hunting": "caza de amenazas",
    "Carga util": "Carga útil",
    "si mismo": "sí mismo",
}

WORD_RE = re.compile(r"[A-Za-zÁÉÍÓÚÜÑáéíóúüñ_-]+")


def apply_map(text, extra=None):
    hits = []
    table = dict(MAP)
    if extra:
        table.update(extra)

    def repl(match):
        word = match.group(0)
        if word in table and table[word] != word:
            hits.append((word, table[word]))
            return table[word]
        return word

    result = WORD_RE.sub(repl, text)
    for phrase, replacement in PHRASES.items():
        if phrase in result:
            hits.append((phrase, replacement))
            result = result.replace(phrase, replacement)
    return result, hits


def java_literals(text):
    """Devuelve (start, end) de literales de cadena Java."""
    spans = []
    i = 0
    n = len(text)
    while i < n:
        ch = text[i]
        if ch == '"':
            if text.startswith('"""', i):
                end = text.find('"""', i + 3)
                end = n if end == -1 else end + 3
                spans.append((i, end))
                i = end
                continue
            j = i + 1
            while j < n:
                if text[j] == "\\":
                    j += 2
                    continue
                if text[j] == '"':
                    break
                j += 1
            end = min(j + 1, n)
            spans.append((i, end))
            i = end
            continue
        if text.startswith("//", i):
            j = text.find("\n", i)
            i = n if j == -1 else j
            continue
        if text.startswith("/*", i):
            j = text.find("*/", i)
            i = n if j == -1 else j + 2
            continue
        i += 1
    return spans


def process_java(path, report):
    text = path.read_text(encoding="utf-8")
    spans = java_literals(text)
    if not spans:
        return None
    out = []
    last = 0
    changed = False
    for start, end in spans:
        chunk = text[start:end]
        if chunk.startswith('"""'):
            inner = chunk[3:-3]
            if any(marker in inner for marker in TECH_MARKERS):
                out.append(text[last:start])
                out.append(chunk)
                last = end
                continue
            new_inner, hits = apply_map(inner)
            if hits:
                changed = True
                report.append((path, text[:start].count("\n") + 1, hits, inner.strip()[:90]))
                chunk = '"""' + new_inner + '"""'
        else:
            inner = chunk[1:-1]
            if any(marker in inner for marker in TECH_MARKERS):
                out.append(text[last:start])
                out.append(chunk)
                last = end
                continue
            new_inner, hits = apply_map(inner)
            if hits:
                changed = True
                report.append((path, text[:start].count("\n") + 1, hits, inner[:90]))
                chunk = '"' + new_inner + '"'
        out.append(text[last:start])
        out.append(chunk)
        last = end
    out.append(text[last:])
    return "".join(out) if changed else None


def process_json(path, report):
    text = path.read_text(encoding="utf-8")
    out = []
    last = 0
    changed = False
    for match in re.finditer(r'"(?:[^"\\]|\\.)*"', text):
        literal = match.group(0)
        tail = text[match.end():].lstrip()
        if tail.startswith(":"):
            continue
        inner = literal[1:-1]
        new_inner, hits = apply_map(inner)
        if hits:
            changed = True
            report.append((path, text[:match.start()].count("\n") + 1, hits, inner[:90]))
            out.append(text[last:match.start()])
            out.append('"' + new_inner + '"')
            last = match.end()
    if not changed:
        return None
    out.append(text[last:])
    return "".join(out)


def process_doc(path, report):
    lines = path.read_text(encoding="utf-8").splitlines(keepends=True)
    out = []
    in_code = False
    changed = False
    for number, line in enumerate(lines, 1):
        if line.lstrip().startswith("```"):
            in_code = not in_code
            out.append(line)
            continue
        if in_code:
            out.append(line)
            continue
        pieces = re.split(r"(`[^`]*`)", line)
        for index, piece in enumerate(pieces):
            if index % 2 == 1:
                continue
            new, hits = apply_map(piece, DOC_MAP)
            if hits:
                changed = True
                report.append((path, number, hits, piece.strip()[:90]))
                pieces[index] = new
        out.append("".join(pieces))
    return "".join(out) if changed else None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--root", default=str(ROOT))
    args = parser.parse_args()
    root = Path(args.root)

    java_files = [p for p in (root / "src").rglob("*.java")
                  if "target" not in p.parts and "src/main/java" in p.as_posix()]
    json_files = list((root / "src").rglob("*.json"))
    doc_files = [p for p in root.rglob("*.md")
                 if ".opencode" not in p.parts and "node_modules" not in p.parts
                 and ".serena" not in p.parts and "target" not in p.parts
                 and p.name not in ("QA_BITACORA.md",)]
    doc_files += [p for p in root.rglob("*.txt")
                  if "node_modules" not in p.parts and "target" not in p.parts
                  and p.name != "LEEME.txt"]

    report = []
    changed_files = {}

    for path in java_files:
        result = process_java(path, report)
        if result:
            changed_files[path] = result
    for path in json_files:
        result = process_json(path, report)
        if result:
            changed_files[path] = result
    for path in doc_files:
        result = process_doc(path, report)
        if result:
            changed_files[path] = result

    for path, number, hits, context in report:
        pairs = ", ".join(f"{a}->{b}" for a, b in hits)
        print(f"{path.relative_to(root)}:{number} [{pairs}] :: {context}")

    print(f"\n{len(report)} fragmentos con {len(changed_files)} archivos afectados")
    if args.apply:
        for path, content in changed_files.items():
            path.write_text(content, encoding="utf-8", newline="\n")
        print("escrito")
    else:
        print("dry-run (use --apply para escribir)")


if __name__ == "__main__":
    sys.exit(main())
