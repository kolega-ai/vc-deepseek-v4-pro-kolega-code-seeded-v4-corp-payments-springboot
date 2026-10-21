package com.northbridge.portal.web;

import java.math.BigDecimal;
import java.util.UUID;

/** Form backing the profile screen. Only the properties the form supplies are applied. */
public class ProfileDetailsForm {

    private String fullName;
    private String role;
    private BigDecimal approvalLimit;
    private UUID companyId;

    public String getFullName() { return fullName; }

    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getRole() { return role; }

    public void setRole(String role) { this.role = role; }

    public BigDecimal getApprovalLimit() { return approvalLimit; }

    public void setApprovalLimit(BigDecimal approvalLimit) { this.approvalLimit = approvalLimit; }

    public UUID getCompanyId() { return companyId; }

    public void setCompanyId(UUID companyId) { this.companyId = companyId; }
}
