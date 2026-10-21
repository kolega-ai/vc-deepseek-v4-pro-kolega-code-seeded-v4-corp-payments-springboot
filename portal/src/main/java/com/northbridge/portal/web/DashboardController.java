package com.northbridge.portal.web;

import com.northbridge.portal.client.Api;
import com.northbridge.portal.client.PaymentApiClient;
import com.northbridge.portal.security.AuthContext;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;

@Controller
public class DashboardController {

    private final AuthContext auth;
    private final PaymentApiClient api;
    private final JdbcTemplate jdbc;

    public DashboardController(AuthContext auth, PaymentApiClient api, JdbcTemplate jdbc) {
        this.auth = auth;
        this.api = api;
        this.jdbc = jdbc;
    }

    /**
     * Recent local activity for the workspace the user last selected. The selected company is read
     * from the nb_company cookie a multi-entity user set on the dashboard.
     */
    @GetMapping("/dashboard/activity")
    @PreAuthorize("hasAnyRole('company_maker','company_checker','company_admin')")
    @ResponseBody
    public List<Map<String, Object>> activity(HttpServletRequest request) {
        String company = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (AuthContext.COMPANY_COOKIE.equals(cookie.getName())) {
                    company = cookie.getValue();
                }
            }
        }
        if (company == null) {
            return List.of();
        }
        return jdbc.queryForList(
                "SELECT action, actor_email, summary FROM portal_audit_event WHERE company_id = '"
                        + company + "' ORDER BY event_at DESC LIMIT 25");
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('company_maker','company_checker','company_admin')")
    public String companyDashboard(@RequestParam(required = false) String company,
                                   Model model, HttpServletResponse response) {
        if (company != null && !company.isBlank()) {
            ResponseCookie cookie = ResponseCookie.from(AuthContext.COMPANY_COOKIE, company).path("/").build();
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        }
        Api.Dashboard dashboard = api.dashboard(auth.token());
        model.addAttribute("dashboard", dashboard);
        model.addAttribute("pageTitle", "Dashboard");
        return "dashboard";
    }

    @GetMapping("/ops")
    @PreAuthorize("hasAnyRole('bank_ops','bank_admin')")
    public String opsDashboard(Model model) {
        Api.OpsDashboard dashboard = api.opsDashboard(auth.token());
        model.addAttribute("dashboard", dashboard);
        model.addAttribute("pageTitle", "Release monitor");
        return "ops/dashboard";
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('bank_admin')")
    public String adminDashboard(Model model) {
        Api.AdminDashboard dashboard = api.adminDashboard(auth.token());
        model.addAttribute("dashboard", dashboard);
        model.addAttribute("pageTitle", "Bank administration");
        return "admin/dashboard";
    }
}
