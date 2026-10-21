package com.northbridge.paymentapi.seed;

import java.util.UUID;

/** Fixed seed identifiers shared conceptually with the other tiers (each tier keeps its own copy). */
public final class SeedRefs {

    private SeedRefs() {
    }

    public static final UUID COMPANY_ACME = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    public static final UUID COMPANY_BRIGHTON = UUID.fromString("00000000-0000-4000-8000-0000000000c2");
    public static final UUID COMPANY_CRANFIELD = UUID.fromString("00000000-0000-4000-8000-0000000000c3");
    public static final UUID COMPANY_DELOS = UUID.fromString("00000000-0000-4000-8000-0000000000c4");

    public static final UUID USER_ACME_MAKER1 = UUID.fromString("00000000-0000-4000-8000-000000000071");
    public static final UUID USER_ACME_MAKER2 = UUID.fromString("00000000-0000-4000-8000-000000000072");
    public static final UUID USER_ACME_CHECKER1 = UUID.fromString("00000000-0000-4000-8000-000000000073");
    public static final UUID USER_ACME_CHECKER2 = UUID.fromString("00000000-0000-4000-8000-000000000074");
    public static final UUID USER_ACME_ADMIN = UUID.fromString("00000000-0000-4000-8000-000000000075");
    public static final UUID USER_BRIGHTON_MAKER = UUID.fromString("00000000-0000-4000-8000-000000000076");
    public static final UUID USER_BRIGHTON_CHECKER = UUID.fromString("00000000-0000-4000-8000-000000000077");
    public static final UUID USER_BRIGHTON_ADMIN = UUID.fromString("00000000-0000-4000-8000-000000000078");
    public static final UUID USER_CRANFIELD_MAKER = UUID.fromString("00000000-0000-4000-8000-000000000079");
    public static final UUID USER_CRANFIELD_CHECKER = UUID.fromString("00000000-0000-4000-8000-00000000007a");
    public static final UUID USER_CRANFIELD_ADMIN = UUID.fromString("00000000-0000-4000-8000-00000000007b");
    public static final UUID USER_DELOS_MAKER = UUID.fromString("00000000-0000-4000-8000-00000000007c");
    public static final UUID USER_DELOS_CHECKER = UUID.fromString("00000000-0000-4000-8000-00000000007d");
    public static final UUID USER_DELOS_ADMIN = UUID.fromString("00000000-0000-4000-8000-00000000007e");
    public static final UUID USER_OPS1 = UUID.fromString("00000000-0000-4000-8000-00000000007f");
    public static final UUID USER_OPS2 = UUID.fromString("00000000-0000-4000-8000-000000000081");
    public static final UUID USER_BANK_ADMIN = UUID.fromString("00000000-0000-4000-8000-000000000082");
    public static final UUID USER_BRIGHTON_INVITED = UUID.fromString("00000000-0000-4000-8000-000000000083");

    public static final UUID ACCOUNT_ACME_OPS = UUID.fromString("00000000-0000-4000-8000-000000000a01");
    public static final UUID ACCOUNT_ACME_PAYROLL = UUID.fromString("00000000-0000-4000-8000-000000000a02");
    public static final UUID ACCOUNT_ACME_LOWBAL = UUID.fromString("00000000-0000-4000-8000-000000000a03");
    public static final UUID ACCOUNT_BRIGHTON_TRADING = UUID.fromString("00000000-0000-4000-8000-000000000a04");
    public static final UUID ACCOUNT_BRIGHTON_FLOAT = UUID.fromString("00000000-0000-4000-8000-000000000a05");
    public static final UUID ACCOUNT_BRIGHTON_EUR = UUID.fromString("00000000-0000-4000-8000-000000000a06");
    public static final UUID ACCOUNT_CRANFIELD_MAIN = UUID.fromString("00000000-0000-4000-8000-000000000a07");
    public static final UUID ACCOUNT_CRANFIELD_COLLECTIONS = UUID.fromString("00000000-0000-4000-8000-000000000a08");
    public static final UUID ACCOUNT_CRANFIELD_TREASURY = UUID.fromString("00000000-0000-4000-8000-000000000a09");
    public static final UUID ACCOUNT_DELOS_OPERATING = UUID.fromString("00000000-0000-4000-8000-000000000a0a");
    public static final UUID ACCOUNT_DELOS_CAPITAL = UUID.fromString("00000000-0000-4000-8000-000000000a0b");
    public static final UUID ACCOUNT_DELOS_USD = UUID.fromString("00000000-0000-4000-8000-000000000a0c");
}
