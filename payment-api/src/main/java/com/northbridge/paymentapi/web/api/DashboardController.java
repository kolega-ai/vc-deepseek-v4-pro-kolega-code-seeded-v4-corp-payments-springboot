package com.northbridge.paymentapi.web.api;

import com.northbridge.paymentapi.security.AuthPrincipal;
import com.northbridge.paymentapi.security.AuthSupport;
import com.northbridge.paymentapi.service.BankAdminService;
import com.northbridge.paymentapi.service.DashboardService;
import com.northbridge.paymentapi.service.LimitService;
import com.northbridge.paymentapi.web.ViewMapper;
import com.northbridge.paymentapi.web.dto.BankDtos;
import com.northbridge.paymentapi.web.dto.CompanyDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequestMapping("/api")
public class DashboardController {

    private final DashboardService dashboardService;
    private final BankAdminService bankAdminService;
    private final ViewMapper mapper;

    public DashboardController(DashboardService dashboardService,
                               BankAdminService bankAdminService,
                               ViewMapper mapper) {
        this.dashboardService = dashboardService;
        this.bankAdminService = bankAdminService;
        this.mapper = mapper;
    }

    @GetMapping("/dashboard")
    public CompanyDtos.Dashboard dashboard() {
        AuthPrincipal actor = AuthSupport.require();
        DashboardService.CompanyDashboard d = dashboardService.companyDashboard(actor);
        return new CompanyDtos.Dashboard(
                new CompanyDtos.Dashboard.PendingApprovals(d.pendingApprovals(), d.awaitingMyApproval()),
                new CompanyDtos.Dashboard.Released(d.releasedTodayCount(), d.releasedTodayValue(),
                        d.utilisation().currency()),
                new CompanyDtos.Dashboard.Failed(d.failedReleases()),
                new CompanyDtos.Dashboard.Utilisation(d.utilisation().used(), d.utilisation().limit(),
                        d.utilisation().headroom(), d.utilisation().currency(), d.utilisation().percent()),
                d.unreadNotifications(),
                d.recentPayments().stream().map(p -> mapper.summary(p, actor)).toList(),
                d.recentActivity().stream()
                        .map(e -> new CompanyDtos.Dashboard.ActivityItem(e.getEventAt(), e.getActorName(),
                                e.getAction(), e.getSummary()))
                        .toList());
    }

    @GetMapping("/ops/dashboard")
    public BankDtos.OpsDashboard opsDashboard() {
        AuthPrincipal actor = AuthSupport.require();
        DashboardService.OpsDashboard d = dashboardService.opsDashboard(actor);
        return new BankDtos.OpsDashboard(d.releasedTodayCount(), d.releasedTodayValue(),
                d.failedReleases(), d.investigatingReleases(), d.dueToday(), d.pendingReversals(),
                d.recentReleases().stream().map(r -> {
                    var p = r.getPayment();
                    return new com.northbridge.paymentapi.web.dto.PaymentDtos.ReleaseView(
                            r.getId(), p.getId(), p.getRef(), p.getStatus().name(),
                            r.getCoreRef(), r.getPostedAmount(),
                            r.getResponseCode(), r.getReason(), r.getRetryCount(),
                            r.getInvestigationStatus().name(), r.getInvestigationNotes(),
                            r.getAttemptedAt(), r.getReleasedAt());
                }).toList(),
                d.recentFailures().stream().map(p -> mapper.summary(p, actor)).toList());
    }

    @GetMapping("/admin/dashboard")
    public BankDtos.AdminDashboard adminDashboard() {
        AuthPrincipal actor = AuthSupport.require();
        DashboardService.AdminDashboard d = dashboardService.adminDashboard(actor);
        List<BankDtos.CompanyView> companies = bankAdminService.companyStats(actor).stream()
                .map(s -> mapper.company(s.company(), s.utilisation(), s.userCount(), s.pendingReversals()))
                .toList();
        return new BankDtos.AdminDashboard(companies, d.pendingReversals(), d.auditEvents24h(),
                d.totalReleasedToday(), List.of());
    }
}
