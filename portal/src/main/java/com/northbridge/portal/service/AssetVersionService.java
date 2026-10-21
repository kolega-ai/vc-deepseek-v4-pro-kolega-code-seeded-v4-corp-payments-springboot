package com.northbridge.portal.service;

import org.springframework.stereotype.Service;

import java.util.Random;

/**
 * Adds a cache-busting suffix to static dashboard assets so browsers pick up redeployed styling.
 *
 * <p>PRECISION LOCATION #2: java.util.Random is used ONLY for this cache-busting suffix on a static
 * asset — this is a NON-CRYPTOGRAPHIC use and must remain java.util.Random per the corpus contract.
 * It is never used for tokens, identifiers with security meaning, or anything cryptographic.</p>
 */
@Service
public class AssetVersionService {

    private final String assetVersion;

    public AssetVersionService() {
        Random random = new Random(); // non-cryptographic: cache-busting suffix only
        this.assetVersion = "v" + Math.abs(random.nextInt(1_000_000));
    }

    /** Suffix to append to static dashboard asset URLs, e.g. /css/app.css?v123456. */
    public String suffix() {
        return assetVersion;
    }
}
