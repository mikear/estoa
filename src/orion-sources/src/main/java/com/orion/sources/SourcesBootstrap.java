package com.orion.sources;

import java.util.List;

/**
 * Registro de los conectores integrados. Invocado por la aplicacion al
 * inicializar; los plugins pueden anadir mas via SPI.
 */
public final class SourcesBootstrap {

    private SourcesBootstrap() {}

    public static List<SourceConnector> defaults() {
        return List.of(
                new DnsOverHttpsSource(),
                new RdapSource(),
                new CrtShSource(),
                new IpApiSource(),
                new TeamCymruSource(),
                new ThreatFoxSource(),
                new UrlHausSource(),
                new MalwareBazaarSource(),
                new FeodoSource(),
                new OpenPhishSource(),
                new RansomwareLiveSource(),
                new RansomLookSource(),
                new NvdSource(),
                new CisaKevSource(),
                new EpssSource(),
                new MitreAttackSource(),
                new ShodanInternetDbSource(),
                new SiteProbeSource(),
                new HibpSource(),
                new HudsonRockSource(),
                new VirusTotalSource(),
                new ShodanSource(),
                new UrlscanSource(),
                new UrlquerySource());
    }

    public static void registerDefaults(SourceRegistry registry) {
        registry.registerAll(defaults());
    }

    /** Conector ATT&CC tipico para la matriz (instancia compartida). */
    public static MitreAttackSource attack() {
        return SourceRegistry.getInstance().byId(MitreAttackSource.ID)
                .filter(c -> c instanceof MitreAttackSource)
                .map(c -> (MitreAttackSource) c)
                .orElseGet(MitreAttackSource::new);
    }
}
