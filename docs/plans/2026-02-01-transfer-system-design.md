# Sistema de Transferências entre Warehouses

## Visão Geral

Sistema de movimentação de estoque entre warehouses com máquina de estados, validação por código de barras e auditoria completa.

## Modelo de Dados

### Entidades Principais

```
Transfer (TenantAwareEntity)
├── id: UUID
├── code: String (ex: "TRF-2024-0001", gerado)
├── sourceWarehouseId: UUID
├── destinationWarehouseId: UUID
├── status: TransferStatus (enum)
├── notes: String (opcional)
├── createdByUserId: UUID
├── executedByUserId: UUID (nullable)
├── executedAt: Instant (nullable)
├── validatedByUserId: UUID (nullable)
├── validatedAt: Instant (nullable)
├── cancelledByUserId: UUID (nullable)
├── cancelledAt: Instant (nullable)
├── cancellationReason: String (nullable)
└── timestamps (createdAt, updatedAt)

TransferItem
├── id: UUID
├── transferId: UUID (FK)
├── sourceBatchId: UUID (FK)
├── productId: UUID (FK, desnormalizado)
├── productBarcode: String (snapshot)
├── productName: String (snapshot)
├── productSku: String (snapshot)
├── quantitySent: BigDecimal
├── quantityReceived: BigDecimal (default 0)
└── destinationBatchId: UUID (nullable, criado na validação)

TransferValidationLog
├── id: UUID
├── transferItemId: UUID (FK)
├── barcode: String (o que foi escaneado)
├── validatedByUserId: UUID
├── validatedAt: Instant
└── valid: Boolean (se o barcode correspondeu)
```

### Alteração no Batch existente

Adicionar campo `transitQuantity: BigDecimal` (default 0) para rastrear estoque em trânsito.

## Máquina de Estados

### Estados

```java
public enum TransferStatus {
    DRAFT,                      // Criada, editável
    IN_TRANSIT,                 // Executada, produtos em trânsito
    PENDING_VALIDATION,         // Aguardando checagem no destino
    COMPLETED,                  // Finalizada com sucesso
    COMPLETED_WITH_DISCREPANCY, // Finalizada com diferenças
    CANCELLED                   // Cancelada
}
```

### Transições Permitidas

```
DRAFT → IN_TRANSIT
    Ação: execute()
    Quem: sourceWarehouse + TRANSFER_EXECUTE
    Efeito:
      - Batch.quantity -= quantitySent
      - Batch.transitQuantity += quantitySent
      - Registro no InventoryLedger (tipo: TRANSFER_OUT)

DRAFT → CANCELLED
    Ação: cancel()
    Quem: sourceWarehouse + TRANSFER_CANCEL
    Efeito: nenhum (ainda não moveu estoque)

IN_TRANSIT → PENDING_VALIDATION
    Ação: startValidation()
    Quem: destinationWarehouse + TRANSFER_VALIDATE
    Efeito: nenhum (apenas muda estado)

IN_TRANSIT → CANCELLED
    Ação: cancel()
    Quem: sourceWarehouse + TRANSFER_CANCEL
    Efeito:
      - Batch.transitQuantity -= quantitySent
      - Batch.quantity += quantitySent
      - Registro no InventoryLedger (tipo: TRANSFER_CANCELLED)

PENDING_VALIDATION → COMPLETED
    Ação: completeValidation()
    Quem: destinationWarehouse + TRANSFER_VALIDATE
    Condição: quantityReceived == quantitySent para todos os itens
    Efeito:
      - Cria novos Batches no destino
      - Batch origem: transitQuantity -= quantitySent
      - Registro no InventoryLedger (tipo: TRANSFER_IN)

PENDING_VALIDATION → COMPLETED_WITH_DISCREPANCY
    Ação: completeValidation()
    Quem: destinationWarehouse + TRANSFER_VALIDATE
    Condição: quantityReceived != quantitySent para algum item
    Efeito:
      - Cria novos Batches com quantityReceived
      - Batch origem: transitQuantity -= quantitySent
      - Gera DiscrepancyReport
      - Registro no InventoryLedger (tipo: TRANSFER_IN_DISCREPANCY)
```

## API Endpoints

### Transferências

```
POST   /stockshift/transfers
       Cria transferência em DRAFT
       Body: { destinationWarehouseId, notes, items[] }
       Quem: sourceWarehouse (do JWT) + TRANSFER_EXECUTE

GET    /stockshift/transfers
       Lista transferências do tenant
       Query: ?status=IN_TRANSIT&sourceWarehouseId=...&destinationWarehouseId=...
       Quem: qualquer usuário do tenant

GET    /stockshift/transfers/{id}
       Detalhes da transferência com itens
       Quem: qualquer usuário do tenant

PATCH  /stockshift/transfers/{id}
       Edita transferência em DRAFT (notes, items)
       Quem: sourceWarehouse + TRANSFER_EXECUTE

DELETE /stockshift/transfers/{id}
       Cancela transferência (DRAFT ou IN_TRANSIT)
       Body: { reason } (obrigatório se IN_TRANSIT)
       Quem: sourceWarehouse + TRANSFER_CANCEL
```

### Ações de Estado

```
POST   /stockshift/transfers/{id}/execute
       DRAFT → IN_TRANSIT
       Quem: sourceWarehouse + TRANSFER_EXECUTE

POST   /stockshift/transfers/{id}/start-validation
       IN_TRANSIT → PENDING_VALIDATION
       Quem: destinationWarehouse + TRANSFER_VALIDATE

POST   /stockshift/transfers/{id}/scan
       Registra escaneamento de código de barras
       Body: { barcode }
       Retorna: { item, newQuantityReceived, valid, message }
       Quem: destinationWarehouse + TRANSFER_VALIDATE

POST   /stockshift/transfers/{id}/complete-validation
       PENDING_VALIDATION → COMPLETED ou COMPLETED_WITH_DISCREPANCY
       Quem: destinationWarehouse + TRANSFER_VALIDATE
```

### Relatórios

```
GET    /stockshift/transfers/{id}/discrepancy-report
       Retorna diferenças entre enviado e recebido
       Disponível apenas se status = COMPLETED_WITH_DISCREPANCY
       Quem: qualquer usuário do tenant

GET    /stockshift/transfers/{id}/validation-logs
       Histórico de escaneamentos
       Quem: qualquer usuário do tenant
```

## Lógica de Validação por Código de Barras

### Fluxo do Endpoint `/scan`

```
1. Recebe barcode
2. Busca TransferItem onde productBarcode == barcode

   Se não encontrar:
     → Registra log com valid=false
     → Retorna { valid: false, message: "Produto não pertence a esta transferência" }

   Se encontrar:
     → Incrementa quantityReceived no item
     → Registra log com valid=true
     → Retorna {
         valid: true,
         item: { productName, quantitySent, quantityReceived },
         message: "Produto registrado"
       }

   Se quantityReceived > quantitySent:
     → Ainda registra (não bloqueia)
     → Retorna {
         valid: true,
         warning: "Quantidade recebida excede quantidade enviada",
         item: { ... }
       }
```

### Resposta do `/complete-validation`

```json
{
  "transferId": "uuid",
  "status": "COMPLETED_WITH_DISCREPANCY",
  "summary": {
    "totalItemTypes": 5,
    "itemsOk": 3,
    "itemsWithDiscrepancy": 2
  },
  "discrepancies": [
    {
      "productName": "Produto X",
      "productBarcode": "123456",
      "quantitySent": 10,
      "quantityReceived": 8,
      "difference": -2,
      "type": "SHORTAGE"
    },
    {
      "productName": "Produto Y",
      "productBarcode": "789012",
      "quantitySent": 5,
      "quantityReceived": 7,
      "difference": 2,
      "type": "OVERAGE"
    }
  ]
}
```

### Regras de Negócio

- Permitir escanear mesmo após exceder quantidade (registra overage)
- Permitir finalizar mesmo sem escanear tudo (registra shortage)
- Warehouse B recebe exatamente `quantityReceived`, não `quantitySent`
- Tudo é registrado no `InventoryLedger` e `TransferValidationLog`

## Integração com InventoryLedger

### Novos Tipos de Entrada no Ledger

```java
public enum LedgerEntryType {
    // Existentes...
    IN,
    OUT,
    ADJUSTMENT,

    // Novos para transferências
    TRANSFER_OUT,           // Saída do warehouse origem (execute)
    TRANSFER_CANCELLED,     // Reversão por cancelamento
    TRANSFER_IN,            // Entrada no warehouse destino (complete)
    TRANSFER_IN_DISCREPANCY // Entrada com quantidade diferente
}
```

### Registros Gerados por Ação

**Ao executar (DRAFT → IN_TRANSIT):**
```
Para cada TransferItem:
  InventoryLedger {
    warehouseId: sourceWarehouseId,
    batchId: sourceBatchId,
    productId: productId,
    entryType: TRANSFER_OUT,
    quantity: -quantitySent,
    referenceType: "TRANSFER",
    referenceId: transferId,
    notes: "Transfer to {destinationWarehouseName}"
  }
```

**Ao cancelar em IN_TRANSIT:**
```
Para cada TransferItem:
  InventoryLedger {
    warehouseId: sourceWarehouseId,
    batchId: sourceBatchId,
    productId: productId,
    entryType: TRANSFER_CANCELLED,
    quantity: +quantitySent,
    referenceType: "TRANSFER",
    referenceId: transferId,
    notes: "Transfer cancelled: {reason}"
  }
```

**Ao completar validação:**
```
Para cada TransferItem:
  InventoryLedger {
    warehouseId: destinationWarehouseId,
    batchId: destinationBatchId (novo),
    productId: productId,
    entryType: TRANSFER_IN ou TRANSFER_IN_DISCREPANCY,
    quantity: +quantityReceived,
    referenceType: "TRANSFER",
    referenceId: transferId,
    notes: "Transfer from {sourceWarehouseName}"
  }
```

## Casos de Borda e Validações

### Validações na Criação (DRAFT)

```
- destinationWarehouseId != sourceWarehouseId (do JWT)
- destinationWarehouseId existe e pertence ao mesmo tenant
- items não pode ser vazio
- Para cada item:
  - sourceBatchId existe, pertence ao sourceWarehouse e ao tenant
  - quantitySent > 0
  - quantitySent <= batch.quantity (estoque disponível)
  - Batch não está soft-deleted
```

### Validações na Execução

```
- Status atual == DRAFT
- Revalidar que todos os batches ainda têm quantidade suficiente
  (pode ter mudado desde a criação)
- Se algum batch não tem mais estoque suficiente:
  → Retorna erro com detalhes de quais itens falharam
  → Nenhuma movimentação é feita (transação atômica)
```

### Validações no Cancelamento

```
- Status atual == DRAFT ou IN_TRANSIT
- Se IN_TRANSIT: reason é obrigatório
- Não pode cancelar se já está em PENDING_VALIDATION ou posterior
```

### Validações na Validação

```
- startValidation: Status atual == IN_TRANSIT
- scan: Status atual == PENDING_VALIDATION
- completeValidation: Status atual == PENDING_VALIDATION
```

### Concorrência

```
- Batch usa @Version (optimistic locking) para evitar race conditions
- Se dois usuários tentarem executar transferências do mesmo batch
  simultaneamente, um receberá OptimisticLockException
- Tratamento: "Estoque foi alterado, tente novamente"
```

### Edge Cases

| Cenário | Tratamento |
|---------|------------|
| Batch deletado após criar DRAFT | Erro ao executar, transferência fica em DRAFT |
| Warehouse destino desativado | Erro ao executar |
| Produto editado após snapshot | Sem impacto, snapshot preserva dados originais |
| Scan de barcode duplicado em sequência | Incrementa normalmente (pode ser intencional) |
| Complete sem nenhum scan | Permitido, gera COMPLETED_WITH_DISCREPANCY com 100% shortage |

## Estrutura de Arquivos

```
src/main/java/.../stockshift/
├── entity/
│   ├── Transfer.java
│   ├── TransferItem.java
│   ├── TransferValidationLog.java
│   └── enums/
│       └── TransferStatus.java
│
├── repository/
│   ├── TransferRepository.java
│   ├── TransferItemRepository.java
│   └── TransferValidationLogRepository.java
│
├── service/
│   └── transfer/
│       ├── TransferService.java           (orquestra operações)
│       ├── TransferValidationService.java (lógica de scan/complete)
│       └── TransferStateMachine.java      (transições de estado)
│
├── controller/
│   └── TransferController.java
│
├── dto/
│   └── transfer/
│       ├── CreateTransferRequest.java
│       ├── UpdateTransferRequest.java
│       ├── TransferResponse.java
│       ├── TransferItemResponse.java
│       ├── ScanBarcodeRequest.java
│       ├── ScanBarcodeResponse.java
│       ├── CompleteValidationResponse.java
│       ├── DiscrepancyReportResponse.java
│       └── CancelTransferRequest.java
│
└── mapper/
    └── TransferMapper.java

src/main/resources/db/migration/
├── V{next}__create_transfer_tables.sql
└── V{next+1}__add_transit_quantity_to_batch.sql
```

## Migrations Necessárias

```sql
-- V{next}__create_transfer_tables.sql
CREATE TABLE transfer (...)
CREATE TABLE transfer_item (...)
CREATE TABLE transfer_validation_log (...)

-- V{next+1}__add_transit_quantity_to_batch.sql
ALTER TABLE batch ADD COLUMN transit_quantity NUMERIC(19,4) DEFAULT 0;
```

## Resumo das Decisões

| Aspecto | Decisão |
|---------|---------|
| Movimentação de estoque | Decrementa origem, cria novo batch no destino |
| Estados | 6: DRAFT, IN_TRANSIT, PENDING_VALIDATION, COMPLETED, COMPLETED_WITH_DISCREPANCY, CANCELLED |
| Validação | Sequencial por código de barras |
| Discrepâncias | Relatório informativo, não bloqueia |
| Decremento | Imediato com `transitQuantity` separado |
| Cancelamento | Reversão automática com registro no ledger |
| Permissões | warehouseId do JWT + permissions específicas |
| Dados | Snapshot no TransferItem + logs de validação |
| Visibilidade | Todo tenant visualiza, ações restritas |
