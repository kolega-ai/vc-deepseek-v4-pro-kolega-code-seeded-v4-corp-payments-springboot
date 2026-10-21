package com.northbridge.portal.web;

import com.northbridge.portal.client.PaymentApiClient;
import com.northbridge.portal.domain.PortalUser;
import com.northbridge.portal.security.AuthContext;
import com.northbridge.portal.service.AssetVersionService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Adds the shared UI model attributes used by the layout: the acting user, the asset cache-bust
 * suffix (precision location #2) and the unread notification count.
 */
@ControllerAdvice
public class UiModelAdvice {

    private final AuthContext auth;
    private final PaymentApiClient paymentApiClient;
    private final AssetVersionService assetVersionService;

    public UiModelAdvice(AuthContext auth, PaymentApiClient paymentApiClient,
                         AssetVersionService assetVersionService) {
        this.auth = auth;
        this.paymentApiClient = paymentApiClient;
        this.assetVersionService = assetVersionService;
    }

    @ModelAttribute("currentUser")
    public PortalUser currentUser() {
        return auth.currentUser();
    }

    @ModelAttribute("assetVersion")
    public String assetVersion() {
        return assetVersionService.suffix();
    }

    @ModelAttribute("unreadNotifications")
    public long unreadNotifications() {
        if (auth.currentUser() == null) {
            return 0;
        }
        try {
            return paymentApiClient.unreadCount(auth.token());
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
