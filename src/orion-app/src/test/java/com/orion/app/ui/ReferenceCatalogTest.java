package com.orion.app.ui;

import com.orion.core.model.EntityType;
import com.orion.core.model.OrionEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReferenceCatalogTest {

    @Test
    void derivesExternalReferencesFromAttributesAndType() {
        OrionEntity domain = OrionEntity.of(EntityType.DOMAIN, "osde.com.ar")
                .attr("finalUrl", "https://osde.com.ar/")
                .attr("scanId", "abc123");
        domain.addSource("urlscan");
        List<String> domainRefs = ReferenceCatalog.of(domain);
        assertTrue(domainRefs.contains("https://osde.com.ar/"));
        assertTrue(domainRefs.contains("https://urlscan.io/result/abc123/"));

        OrionEntity cve = OrionEntity.of(EntityType.CVE, "CVE-2021-44228");
        assertTrue(ReferenceCatalog.of(cve).contains("https://nvd.nist.gov/vuln/detail/CVE-2021-44228"));

        OrionEntity tech = OrionEntity.of(EntityType.ATTCK_TECHNIQUE, "T1566.001");
        assertTrue(ReferenceCatalog.of(tech).contains("https://attack.mitre.org/techniques/T1566/001/"));
    }
}
