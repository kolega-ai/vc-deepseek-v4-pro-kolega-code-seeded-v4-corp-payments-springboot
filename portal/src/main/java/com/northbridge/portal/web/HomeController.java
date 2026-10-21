package com.northbridge.portal.web;

import com.northbridge.portal.domain.PortalUser;
import com.northbridge.portal.security.AuthContext;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final AuthContext auth;

    public HomeController(AuthContext auth) {
        this.auth = auth;
    }

    @GetMapping("/")
    public String home() {
        PortalUser user = auth.requireUser();
        return switch (user.getRole()) {
            case "bank_ops" -> "redirect:/ops";
            case "bank_admin" -> "redirect:/admin";
            default -> "redirect:/dashboard";
        };
    }
}
