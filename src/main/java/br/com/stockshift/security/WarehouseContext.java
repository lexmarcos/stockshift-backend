package br.com.stockshift.security;

import java.util.UUID;

public class WarehouseContext {
    private static final ThreadLocal<UUID> currentWarehouse = new ThreadLocal<>();

    public static void setWarehouseId(UUID warehouseId) {
        currentWarehouse.set(warehouseId);
    }

    public static UUID getWarehouseId() {
        return currentWarehouse.get();
    }

    public static void clear() {
        currentWarehouse.remove();
    }
}
