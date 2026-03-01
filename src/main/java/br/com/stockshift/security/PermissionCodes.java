package br.com.stockshift.security;

import java.util.List;

public final class PermissionCodes {

    public static final String USERS_READ = "users:read";
    public static final String USERS_CREATE = "users:create";
    public static final String USERS_UPDATE = "users:update";
    public static final String USERS_DELETE = "users:delete";

    public static final String ROLES_READ = "roles:read";
    public static final String ROLES_CREATE = "roles:create";
    public static final String ROLES_UPDATE = "roles:update";
    public static final String ROLES_DELETE = "roles:delete";

    public static final String BRANDS_READ = "brands:read";
    public static final String BRANDS_CREATE = "brands:create";
    public static final String BRANDS_UPDATE = "brands:update";
    public static final String BRANDS_DELETE = "brands:delete";

    public static final String CATEGORIES_READ = "categories:read";
    public static final String CATEGORIES_CREATE = "categories:create";
    public static final String CATEGORIES_UPDATE = "categories:update";
    public static final String CATEGORIES_DELETE = "categories:delete";

    public static final String PRODUCTS_READ = "products:read";
    public static final String PRODUCTS_CREATE = "products:create";
    public static final String PRODUCTS_UPDATE = "products:update";
    public static final String PRODUCTS_DELETE = "products:delete";
    public static final String PRODUCTS_ANALYZE_IMAGE = "products:analyze_image";

    public static final String WAREHOUSES_READ = "warehouses:read";
    public static final String WAREHOUSES_CREATE = "warehouses:create";
    public static final String WAREHOUSES_UPDATE = "warehouses:update";
    public static final String WAREHOUSES_DELETE = "warehouses:delete";

    public static final String BATCHES_READ = "batches:read";
    public static final String BATCHES_CREATE = "batches:create";
    public static final String BATCHES_UPDATE = "batches:update";
    public static final String BATCHES_DELETE = "batches:delete";

    public static final String TRANSFERS_READ = "transfers:read";
    public static final String TRANSFERS_CREATE = "transfers:create";
    public static final String TRANSFERS_UPDATE = "transfers:update";
    public static final String TRANSFERS_DELETE = "transfers:delete";
    public static final String TRANSFERS_EXECUTE = "transfers:execute";
    public static final String TRANSFERS_VALIDATE = "transfers:validate";

    public static final String REPORTS_READ = "reports:read";
    public static final String PERMISSIONS_READ = "permissions:read";

    public static final String STOCK_MOVEMENTS_READ = "stock_movements:read";
    public static final String STOCK_MOVEMENTS_CREATE = "stock_movements:create";

    private static final List<String> ALL = List.of(
            USERS_READ,
            USERS_CREATE,
            USERS_UPDATE,
            USERS_DELETE,
            ROLES_READ,
            ROLES_CREATE,
            ROLES_UPDATE,
            ROLES_DELETE,
            BRANDS_READ,
            BRANDS_CREATE,
            BRANDS_UPDATE,
            BRANDS_DELETE,
            CATEGORIES_READ,
            CATEGORIES_CREATE,
            CATEGORIES_UPDATE,
            CATEGORIES_DELETE,
            PRODUCTS_READ,
            PRODUCTS_CREATE,
            PRODUCTS_UPDATE,
            PRODUCTS_DELETE,
            PRODUCTS_ANALYZE_IMAGE,
            WAREHOUSES_READ,
            WAREHOUSES_CREATE,
            WAREHOUSES_UPDATE,
            WAREHOUSES_DELETE,
            BATCHES_READ,
            BATCHES_CREATE,
            BATCHES_UPDATE,
            BATCHES_DELETE,
            TRANSFERS_READ,
            TRANSFERS_CREATE,
            TRANSFERS_UPDATE,
            TRANSFERS_DELETE,
            TRANSFERS_EXECUTE,
            TRANSFERS_VALIDATE,
            REPORTS_READ,
            PERMISSIONS_READ,
            STOCK_MOVEMENTS_READ,
            STOCK_MOVEMENTS_CREATE);

    private PermissionCodes() {
    }

    public static List<String> all() {
        return ALL;
    }
}
