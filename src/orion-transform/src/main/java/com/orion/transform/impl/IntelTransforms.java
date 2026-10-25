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
 * Transformaciones de inteligencia: ransomware, vulnerabilidades y ATT&CK.
 */
public final class IntelTransforms {

    private IntelTransforms() {}

    public static BaseTransform ransomwareCheck() {
        return new BaseTransform("ransomware-check", "Víctimas de ransomware (ransomware.live)",
                "Comprueba si el dominio u organización aparece publicado por grupos de ransomware.",
                Transform.Category.THREAT_INTEL,
                Set.of(EntityType.DOMAIN, EntityType.ORGANIZATION), Set.of(EntityType.THREAT_ACTOR, EntityType.CAMPAIGN), false) {
            @Override
            public TransformResult apply(OrionEntity input, TransformContext ctx) {
                return DomainTransforms.collect(ctx, input, "Ransomware", Operation.DOMAIN_THREATS);
            }
        };
    }

    public static BaseTransform cveInfo() {
        return new BaseTransform("cve-info", "Detalle de CVE (NVD + KEV + EPSS)",
                "CVSS, confirmación de explotación en KEV y probabilidad de explotación EPSS.",
                Transform.Category.THREAT_INTEL,
                Set.of(EntityType.CVE, EntityType.EXPLOIT), Set.of(EntityType.CVE, EntityType.EXPLOIT), false) {
            @Override
            public TransformResult apply(OrionEntity input, TransformContext ctx) {
                return DomainTransforms.collect(ctx, input, "CVE",
                        Operation.CVE_INFO, Operation.KEV_CHECK, Operation.EPSS);
            }
        };
    }

    public static BaseTransform attackTechnique() {
        return new BaseTransform("attack-technique", "Técnica ATT&CK",
                "Táctica asociada y grupos que emplean la técnica.",
                Transform.Category.THREAT_INTEL,
                Set.of(EntityType.ATTCK_TECHNIQUE), Set.of(EntityType.ATTCK_TACTIC, EntityType.APT), false) {
            @Override
            public TransformResult apply(OrionEntity input, TransformContext ctx) {
                return DomainTransforms.collect(ctx, input, "ATT&CK", Operation.TECHNIQUE_INFO);
            }
        };
    }

    public static BaseTransform actorProfile() {
        return new BaseTransform("actor-profile", "Perfil del actor (ATT&CK)",
                "Alias, origen, motivación y TTPs documentadas del grupo.",
                Transform.Category.THREAT_INTEL,
                Set.of(EntityType.APT, EntityType.THREAT_ACTOR), Set.of(EntityType.ATTCK_TECHNIQUE, EntityType.ATTCK_TACTIC), false) {
            @Override
            public TransformResult apply(OrionEntity input, TransformContext ctx) {
                return DomainTransforms.collect(ctx, input, "Actor", Operation.ACTOR_INFO, Operation.GROUP_INFO);
            }
        };
    }
}
