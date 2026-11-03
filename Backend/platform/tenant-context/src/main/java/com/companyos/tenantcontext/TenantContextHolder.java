package com.companyos.tenantcontext;

public final class TenantContextHolder {
    private static final ThreadLocal<TenantContext> CURRENT = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void set(TenantContext context) {
        CURRENT.set(context);
    }

    public static TenantContext required() {
        TenantContext context = CURRENT.get();
        if (context == null) {
            throw new IllegalStateException("Verified tenant context is required");
        }
        return context;
    }

    public static TenantContext current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
