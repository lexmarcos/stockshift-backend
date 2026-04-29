package br.com.stockshift.security.audit;

public final class AuditContextHolder {

    private static final ThreadLocal<AuditContext> CURRENT = new ThreadLocal<>();

    private AuditContextHolder() {
    }

    public static void set(AuditContext context) {
        CURRENT.set(context);
    }

    public static AuditContext get() {
        return CURRENT.get();
    }

    public static void setHttpStatus(Integer httpStatus) {
        AuditContext context = CURRENT.get();
        if (context != null) {
            context.setHttpStatus(httpStatus);
        }
    }

    public static void clear() {
        CURRENT.remove();
    }
}
