package com.stockshift.backend.domain.warehouse;

public enum WarehouseType {
    MAIN,           // Armazém principal
    DISTRIBUTION,   // Centro de distribuição
    STORE,          // Loja física
    TRANSIT,        // Depósito de trânsito
    SUPPLIER,       // Fornecedor externo
    CUSTOMER        // Cliente (para consignação)
}
