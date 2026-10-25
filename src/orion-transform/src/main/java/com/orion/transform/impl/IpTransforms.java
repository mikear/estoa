package com.orion.transform.impl;

import com.orion.core.model.EntityType;
import com.orion.transform.Transform;
import com.orion.core.model.OrionEntity;
import com.orion.sources.Operation;
import com.orion.sources.SourceQuery;
import com.orion.transform.BaseTransform;
import com.orion.transform.TransformContext;
import com.orion.transform.TransformResult;

import java.util.Set;

/**
 * Transformaciones sobre direcciones IP: geolocalización, ASN y reputación
 * contra múltiples feeds de amenazas.
 */
public final class IpTransforms {

    private IpTransforms() {}

    public static BaseTransform geo() {
        return new BaseTransform("ip-geo", "Geolocalización y red (ip-api)",
                "País, ciudad, ISP e indicadores de proxy o alojamiento para la IP.",
                Transform.Category.ENRICHMENT,
                Set.of(EntityType.IPV4), Set.of(EntityType.ORGANIZATION, EntityType.ASN), false) {
            @Override
            public TransformResult apply(OrionEntity input, TransformContext ctx) {
                return DomainTransforms.collect(ctx, input, "Geolocalización", Operation.IP_GEO);
            }
        };
    }

    public static BaseTransform asn() {
        return new BaseTransform("ip-asn", "Atribución ASN (Team Cymru)",
                "Sistema autónomo, bloque de red y país según el registro oficial.",
                Transform.Category.INFRASTRUCTURE,
                Set.of(EntityType.IPV4, EntityType.IPV6), Set.of(EntityType.ASN, EntityType.NETBLOCK), false) {
            @Override
            public TransformResult apply(OrionEntity input, TransformContext ctx) {
                return DomainTransforms.collect(ctx, input, "ASN", Operation.IP_ASN);
            }
        };
    }

    public static BaseTransform internetDb() {
        return new BaseTransform("ip-internetdb", "Exposición del equipo (InternetDB)",
                "Puertos abiertos, hostnames, productos y CVE publicados por la IP, sin clave.",
                Transform.Category.INFRASTRUCTURE,
                Set.of(EntityType.IPV4, EntityType.IPV6),
                Set.of(EntityType.PORT, EntityType.DOMAIN, EntityType.CVE, EntityType.SOFTWARE), false) {
            @Override
            public TransformResult apply(OrionEntity input, TransformContext ctx) {
                return DomainTransforms.collect(ctx, input, "Exposición", Operation.INTERNET_DB);
            }
        };
    }

    public static BaseTransform rdap() {
        return new BaseTransform("ip-rdap", "Titularidad y abuso (RDAP)",
                "Bloque de red, país, organización titular y contacto de abuso de la IP.",
                Transform.Category.ENRICHMENT,
                Set.of(EntityType.IPV4, EntityType.IPV6),
                Set.of(EntityType.NETBLOCK, EntityType.ORGANIZATION, EntityType.EMAIL), false) {
            @Override
            public TransformResult apply(OrionEntity input, TransformContext ctx) {
                return DomainTransforms.collect(ctx, input, "Registro RDAP", Operation.WHOIS);
            }
        };
    }

    public static BaseTransform reputation() {
        return new BaseTransform("ip-reputation", "Reputación (Feodo + ThreatFox + URLHaus)",
                "Comprobación contra fuentes de C2 y distribución de malware.",
                Transform.Category.THREAT_INTEL,
                Set.of(EntityType.IPV4, EntityType.IPV6), Set.of(EntityType.MALWARE, EntityType.URL), false) {
            @Override
            public TransformResult apply(OrionEntity input, TransformContext ctx) {
                return DomainTransforms.collect(ctx, input, "Reputación",
                        Operation.IP_REPUTATION, Operation.DOMAIN_THREATS);
            }
        };
    }
}
