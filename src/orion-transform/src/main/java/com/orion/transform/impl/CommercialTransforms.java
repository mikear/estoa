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
 * Transformaciones que requieren claves de API comerciales.
 */
public final class CommercialTransforms {

    private CommercialTransforms() {}

    public static BaseTransform virusTotal() {
        return new BaseTransform("vt-report", "Informe agregado (VirusTotal)",
                "Detecciones de motores, reputación y atributos de red del observable.",
                Transform.Category.COMMERCIAL,
                Set.of(EntityType.IPV4, EntityType.IPV6, EntityType.DOMAIN, EntityType.HASH, EntityType.URL),
                Set.of(EntityType.MALWARE), true) {
            @Override
            public TransformResult apply(OrionEntity input, TransformContext ctx) {
                return DomainTransforms.collect(ctx, input, "VirusTotal", Operation.VT_REPORT);
            }
        };
    }

    public static BaseTransform shodan() {
        return new BaseTransform("shodan-host", "Superficie expuesta (Shodan)",
                "Puertos abiertos, servicios y banners del equipo.",
                Transform.Category.COMMERCIAL,
                Set.of(EntityType.IPV4), Set.of(EntityType.PORT, EntityType.PROTOCOL), true) {
            @Override
            public TransformResult apply(OrionEntity input, TransformContext ctx) {
                return DomainTransforms.collect(ctx, input, "Shodan", Operation.SHODAN_HOST);
            }
        };
    }

    public static BaseTransform urlscan() {
        return new BaseTransform("urlscan-search", "Escaneos previos (urlscan.io)",
                "Escaneos recientes del dominio con veredictos asociados.",
                Transform.Category.COMMERCIAL,
                Set.of(EntityType.URL, EntityType.DOMAIN), Set.of(EntityType.URL), true) {
            @Override
            public TransformResult apply(OrionEntity input, TransformContext ctx) {
                return DomainTransforms.collect(ctx, input, "urlscan", Operation.SCAN_INFO);
            }
        };
    }
}
