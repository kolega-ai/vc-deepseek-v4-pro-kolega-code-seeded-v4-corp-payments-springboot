package com.northbridge.paymentapi.domain;

public enum Role {
    COMPANY_MAKER("company_maker"),
    COMPANY_CHECKER("company_checker"),
    COMPANY_ADMIN("company_admin"),
    BANK_OPS("bank_ops"),
    BANK_ADMIN("bank_admin");

    private final String apiName;

    Role(String apiName) {
        this.apiName = apiName;
    }

    public String apiName() {
        return apiName;
    }

    public String securityRole() {
        return "ROLE_" + apiName.toUpperCase();
    }

    public static Role fromApiName(String name) {
        for (Role r : values()) {
            if (r.apiName.equalsIgnoreCase(name)) {
                return r;
            }
        }
        throw new IllegalArgumentException("Unknown role: " + name);
    }

    public boolean isBankRole() {
        return this == BANK_OPS || this == BANK_ADMIN;
    }
}
