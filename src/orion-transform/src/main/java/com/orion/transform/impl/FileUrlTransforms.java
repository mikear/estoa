package com.orion.transform.impl;

import com.orion.core.model.EntityType;
import com.orion.transform.Transform;
import com.orion.core.model.OrionEntity;
import com.orion.sources.Operation;
import com.orion.transform.BaseTransform;
import com.orion.transform.TransformContext;
import com.orion.transform.TransformResult;

import java.util.Set;

/**
 * Transformaciones sobre ficheros, hashes, URLs y dominios comprometidos.
 */
public final class FileUrlTransforms {

    private FileUrlTransforms() {}

    public static BaseTransform hashLookup() {
        return new BaseTransform("hash-lookup", "Análisis de muestra (MalwareBazaar)",
                "Familia, tipo de fichero, etiquetas YARA y fechas de la muestra.",
                Transform.Category.THREAT_INTEL,
                Set.of(EntityType.HASH, EntityType.FILE), Set.of(EntityType.MALWARE), false) {
            @Override
            public TransformResult apply(OrionEntity input, TransformContext ctx) {
                return DomainTransforms.collect(ctx, input, "Muestra", Operation.HASH_INFO);
            }
        };
    }

    public static BaseTransform urlReputation() {
        return new BaseTransform("url-reputation", "Reputación de URL (OpenPhish + URLHaus)",
                "Comprobación de phishing y distribución de malware para la URL o su equipo.",
                Transform.Category.THREAT_INTEL,
                Set.of(EntityType.URL, EntityType.DOMAIN), Set.of(EntityType.URL, EntityType.MALWARE), false) {
            @Override
            public TransformResult apply(OrionEntity input, TransformContext ctx) {
                return DomainTransforms.collect(ctx, input, "Reputación URL",
                        Operation.URL_REPUTATION, Operation.IP_REPUTATION);
            }
        };
    }

    public static BaseTransform urlqueryReports() {
        return new BaseTransform("urlquery-reports", "Reportes de navegación (urlquery)",
                "Análisis público del dominio, IP o URL con alertas de seguridad (clave requerida).",
                Transform.Category.HISTORICAL,
                Set.of(EntityType.URL, EntityType.DOMAIN, EntityType.IPV4, EntityType.IPV6),
                Set.of(EntityType.URL), true) {
            @Override
            public TransformResult apply(OrionEntity input, TransformContext ctx) {
                return DomainTransforms.collect(ctx, input, "Reportes urlquery", Operation.URLQUERY);
            }
        };
    }
}
