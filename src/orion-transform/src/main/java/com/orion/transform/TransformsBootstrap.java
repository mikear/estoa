package com.orion.transform;

import com.orion.transform.impl.CommercialTransforms;
import com.orion.transform.impl.DomainTransforms;
import com.orion.transform.impl.FileUrlTransforms;
import com.orion.transform.impl.IntelTransforms;
import com.orion.transform.impl.IpTransforms;

import java.util.List;

/**
 * Registro de las transformaciones integradas (22). Los plugins pueden
 * anadir las suyas via el SPI de orion-plugin.
 */
public final class TransformsBootstrap {

    private TransformsBootstrap() {}

    public static List<Transform> defaults() {
        return List.of(
                DomainTransforms.dnsRecords(),
                DomainTransforms.whois(),
                DomainTransforms.subdomains(),
                DomainTransforms.certificates(),
                DomainTransforms.siteProbe(),
                DomainTransforms.breaches(),
                DomainTransforms.infostealer(),
                IpTransforms.geo(),
                IpTransforms.asn(),
                IpTransforms.internetDb(),
                IpTransforms.rdap(),
                IpTransforms.reputation(),
                FileUrlTransforms.hashLookup(),
                FileUrlTransforms.urlReputation(),
                FileUrlTransforms.urlqueryReports(),
                IntelTransforms.ransomwareCheck(),
                IntelTransforms.cveInfo(),
                IntelTransforms.attackTechnique(),
                IntelTransforms.actorProfile(),
                CommercialTransforms.virusTotal(),
                CommercialTransforms.shodan(),
                CommercialTransforms.urlscan());
    }

    public static void registerDefaults(TransformRegistry registry) {
        registry.registerAll(defaults());
    }
}
