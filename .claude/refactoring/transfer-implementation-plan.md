# Transfer Refactoring - Plano de Implementação

## Visão Geral

Este plano implementa a especificação documentada em `transfer-refactoring-spec.md`, transformando o sistema de transferências de um "StockMovement do tipo TRANSFER" para um agregado próprio com separação clara de papéis (origem/destino).

---

## Convenções do Projeto

| Aspecto | Convenção |
|---------|-----------|
| Package base | `br.com.stockshift` |
| Entidades | Estendem `TenantAwareEntity` |
| Localização entidades | `model/entity/` |
| Localização enums | `model/enums/` |
| Localização DTOs | `dto/{domain}/` |
| Response wrapper | `ApiResponse<T>` |
| Base path API | `/stockshift` |
| Migrations | `V{n}__{description}.sql` (próxima: V26) |
| Testes integração | Estendem `BaseIntegrationTest` |

---

## Fases de Implementação

### Fase 1: Fundação (Banco + Entidades)
### Fase 2: Camada de Domínio (Services + State Machine)
### Fase 3: Camada de API (Controllers + DTOs)
### Fase 4: Testes
### Fase 5: Migração de Dados (opcional)

---

## Fase 1: Fundação

### 1.1 Migrations

| Arquivo | Descrição |
|---------|-----------|
| `V26__create_transfer_tables.sql` | Transfer, TransferItem, TransferInTransit |
| `V27__create_inventory_ledger.sql` | InventoryLedger com trigger append-only |
| `V28__create_transfer_audit_tables.sql` | TransferEvent, TransferDiscrepancy, ScanLog |
| `V29__add_transfer_permissions.sql` | Novas permissões |
| `V30__add_batch_origin_fields.sql` | Campos origin_transfer_id, origin_batch_id em Batch |

**Localização:** `src/main/resources/db/migration/`

---

### 1.2 Enums

| Arquivo | Enum |
|---------|------|
| `TransferStatus.kt` | DRAFT, IN_TRANSIT, VALIDATION_IN_PROGRESS, COMPLETED, COMPLETED_WITH_DISCREPANCY, CANCELLED |
| `TransferAction.kt` | CREATE, UPDATE, CANCEL, DISPATCH, START_VALIDATION, SCAN_ITEM, COMPLETE |
| `TransferRole.kt` | OUTBOUND, INBOUND, NONE |
| `TransferItemStatus.kt` | PENDING, RECEIVED, PARTIAL, EXCESS, MISSING |
| `LedgerEntryType.kt` | PURCHASE_IN, SALE_OUT, ADJUSTMENT_IN, ADJUSTMENT_OUT, TRANSFER_OUT, TRANSFER_IN_TRANSIT, TRANSFER_IN, TRANSFER_TRANSIT_CONSUMED, TRANSFER_LOSS, RETURN_IN |
| `DiscrepancyType.kt` | SHORTAGE, EXCESS |
| `DiscrepancyStatus.kt` | PENDING_RESOLUTION, RESOLVED, WRITTEN_OFF |
| `DiscrepancyResolution.kt` | WRITE_OFF, FOUND, RETURN_TRANSIT, ACCEPTED |
| `TransferEventType.kt` | CREATED, UPDATED, DISPATCHED, VALIDATION_STARTED, ITEM_SCANNED, COMPLETED, COMPLETED_WITH_DISCREPANCY, CANCELLED, DISCREPANCY_RESOLVED |

**Localização:** `src/main/kotlin/br/com/stockshift/model/enums/`

---

### 1.3 Entidades

| Arquivo | Descrição |
|---------|-----------|
| `Transfer.kt` | Agregado principal com @Version para optimistic locking |
| `TransferItem.kt` | Itens da transferência (esperado/recebido) |
| `TransferInTransit.kt` | Saldo em trânsito por item |
| `InventoryLedger.kt` | Registro contábil imutável |
| `TransferDiscrepancy.kt` | Divergências para resolução |
| `TransferEvent.kt` | Histórico de transições |
| `ScanLog.kt` | Idempotência de scans |

**Localização:** `src/main/kotlin/br/com/stockshift/model/entity/`

**Modificação:** `Batch.kt` - adicionar campos `originTransferId`, `originBatchId`

---

### 1.4 Repositories

| Arquivo | Métodos Especiais |
|---------|-------------------|
| `TransferRepository.kt` | `findByIdForUpdate()`, `findByTenantAndWarehouse()` |
| `TransferItemRepository.kt` | `findByTransferId()` |
| `TransferInTransitRepository.kt` | `findByTransferItemId()`, `findPendingByTransferId()` |
| `InventoryLedgerRepository.kt` | `findByReferenceId()`, `findQuantityDiscrepancies()` |
| `TransferDiscrepancyRepository.kt` | `findPendingByTransferId()` |
| `TransferEventRepository.kt` | `findByTransferId()` |
| `ScanLogRepository.kt` | `findByIdempotencyKey()`, `deleteExpired()` |

**Localização:** `src/main/kotlin/br/com/stockshift/repository/`

---

## Fase 2: Camada de Domínio

### 2.1 State Machine

| Arquivo | Descrição |
|---------|-----------|
| `TransferStateMachine.kt` | Validação de transições por status + role |

**Localização:** `src/main/kotlin/br/com/stockshift/service/transfer/`

---

### 2.2 Services

| Arquivo | Responsabilidade |
|---------|------------------|
| `TransferService.kt` | Orquestração principal (create, update, cancel, dispatch) |
| `TransferValidationService.kt` | Validação no destino (start, scan, complete) |
| `TransferLedgerService.kt` | Criação de entradas no ledger |
| `TransferDiscrepancyService.kt` | Resolução de divergências |
| `TransferEventPublisher.kt` | Publicação de eventos de auditoria |
| `TransferRoleResolver.kt` | Determina papel do usuário (OUTBOUND/INBOUND) |
| `TransferBatchResolver.kt` | Resolve/cria batch no destino |

**Localização:** `src/main/kotlin/br/com/stockshift/service/transfer/`

---

### 2.3 Exceptions

| Arquivo | Uso |
|---------|-----|
| `TransferNotFoundException.kt` | Transfer não encontrado |
| `InvalidTransferStateException.kt` | Transição de estado inválida |
| `InsufficientStockException.kt` | Saldo insuficiente para despacho |
| `TransferPermissionException.kt` | Papel incorreto para ação |
| `ExcessQuantityException.kt` | Quantidade escaneada > esperada |
| `TransferConcurrencyException.kt` | Conflito de concorrência |

**Localização:** `src/main/kotlin/br/com/stockshift/exception/`

---

## Fase 3: Camada de API

### 3.1 DTOs - Request

| Arquivo | Descrição |
|---------|-----------|
| `CreateTransferRequest.kt` | sourceWarehouseId, destinationWarehouseId, items, notes |
| `UpdateTransferRequest.kt` | items, notes |
| `ScanItemRequest.kt` | barcode, quantity, idempotencyKey |
| `ResolveDiscrepancyRequest.kt` | resolution, justification |

**Localização:** `src/main/kotlin/br/com/stockshift/dto/transfer/request/`

---

### 3.2 DTOs - Response

| Arquivo | Descrição |
|---------|-----------|
| `TransferResponse.kt` | Detalhes completos + direction + allowedActions |
| `TransferSummaryResponse.kt` | Para listagem |
| `TransferItemResponse.kt` | Item com status |
| `TransferValidationResponse.kt` | Estado da validação |
| `ValidationItemResponse.kt` | Item na validação |
| `ValidationSummary.kt` | Resumo da validação |
| `TransferDiscrepancyResponse.kt` | Divergência |
| `LedgerEntryResponse.kt` | Entrada do ledger |
| `TransferEventResponse.kt` | Evento de histórico |

**Localização:** `src/main/kotlin/br/com/stockshift/dto/transfer/response/`

---

### 3.3 Controllers

| Arquivo | Endpoints |
|---------|-----------|
| `TransferController.kt` | CRUD + dispatch + listagem |
| `TransferValidationController.kt` | start, scan, complete |
| `TransferDiscrepancyController.kt` | listar, resolver |
| `TransferHistoryController.kt` | histórico de eventos |

**Localização:** `src/main/kotlin/br/com/stockshift/controller/`

**Endpoints:**
```
POST   /stockshift/transfers
GET    /stockshift/transfers
GET    /stockshift/transfers/{id}
PUT    /stockshift/transfers/{id}
DELETE /stockshift/transfers/{id}
POST   /stockshift/transfers/{id}/dispatch

POST   /stockshift/transfers/{id}/validation/start
POST   /stockshift/transfers/{id}/validation/scan
POST   /stockshift/transfers/{id}/validation/complete

GET    /stockshift/transfers/{id}/discrepancies
POST   /stockshift/transfers/{id}/discrepancies/{discrepancyId}/resolve

GET    /stockshift/transfers/{id}/history
GET    /stockshift/transfers/{id}/ledger
```

---

### 3.4 Exception Handlers

| Arquivo | Modificação |
|---------|-------------|
| `GlobalExceptionHandler.kt` | Adicionar handlers para novas exceções |

---

## Fase 4: Testes

### 4.1 Testes Unitários

| Arquivo | Cobertura |
|---------|-----------|
| `TransferStateMachineTest.kt` | Todas as transições válidas/inválidas |
| `TransferRoleResolverTest.kt` | Determinação de papel |
| `TransferServiceTest.kt` | Lógica de negócio (mocked repos) |
| `TransferValidationServiceTest.kt` | Lógica de validação |

**Localização:** `src/test/kotlin/br/com/stockshift/service/transfer/`

---

### 4.2 Testes de Integração

| Arquivo | Cenários |
|---------|----------|
| `TransferIntegrationTest.kt` | Happy path completo, permissões, idempotência |
| `TransferConcurrencyTest.kt` | Conflitos de concorrência |
| `TransferDiscrepancyTest.kt` | Divergências e resolução |
| `TransferLedgerTest.kt` | Consistência do ledger |

**Localização:** `src/test/kotlin/br/com/stockshift/integration/transfer/`

---

## Ordem de Implementação

### Sprint 1: Fundação (Estimativa: base)

```
1. Migrations (V26-V30)
2. Enums (todos)
3. Entidades (todas)
4. Repositories (todos)
5. Testes unitários básicos de entidades
```

### Sprint 2: Lógica de Negócio (Estimativa: base)

```
1. TransferStateMachine
2. TransferRoleResolver
3. TransferEventPublisher
4. TransferLedgerService
5. TransferBatchResolver
6. TransferService (create, update, cancel, dispatch)
7. Testes unitários de services
```

### Sprint 3: Validação (Estimativa: base)

```
1. TransferValidationService (start, scan, complete)
2. TransferDiscrepancyService
3. Exceptions
4. Testes unitários de validação
```

### Sprint 4: API (Estimativa: base)

```
1. DTOs (request + response)
2. TransferController
3. TransferValidationController
4. TransferDiscrepancyController
5. TransferHistoryController
6. Exception handlers
7. Testes de integração
```

### Sprint 5: Polimento (Estimativa: base)

```
1. Testes E2E completos
2. Testes de concorrência
3. Documentação de API
4. Code review
5. Performance testing (índices, queries)
```

---

## Arquivos a Criar (Total: ~45)

### Migrations (5)
```
src/main/resources/db/migration/
├── V26__create_transfer_tables.sql
├── V27__create_inventory_ledger.sql
├── V28__create_transfer_audit_tables.sql
├── V29__add_transfer_permissions.sql
└── V30__add_batch_origin_fields.sql
```

### Enums (9)
```
src/main/kotlin/br/com/stockshift/model/enums/
├── TransferStatus.kt
├── TransferAction.kt
├── TransferRole.kt
├── TransferItemStatus.kt
├── LedgerEntryType.kt
├── DiscrepancyType.kt
├── DiscrepancyStatus.kt
├── DiscrepancyResolution.kt
└── TransferEventType.kt
```

### Entities (7)
```
src/main/kotlin/br/com/stockshift/model/entity/
├── Transfer.kt
├── TransferItem.kt
├── TransferInTransit.kt
├── InventoryLedger.kt
├── TransferDiscrepancy.kt
├── TransferEvent.kt
└── ScanLog.kt
```

### Repositories (7)
```
src/main/kotlin/br/com/stockshift/repository/
├── TransferRepository.kt
├── TransferItemRepository.kt
├── TransferInTransitRepository.kt
├── InventoryLedgerRepository.kt
├── TransferDiscrepancyRepository.kt
├── TransferEventRepository.kt
└── ScanLogRepository.kt
```

### Services (7)
```
src/main/kotlin/br/com/stockshift/service/transfer/
├── TransferService.kt
├── TransferValidationService.kt
├── TransferLedgerService.kt
├── TransferDiscrepancyService.kt
├── TransferEventPublisher.kt
├── TransferRoleResolver.kt
├── TransferBatchResolver.kt
└── TransferStateMachine.kt
```

### Exceptions (6)
```
src/main/kotlin/br/com/stockshift/exception/
├── TransferNotFoundException.kt
├── InvalidTransferStateException.kt
├── InsufficientStockException.kt
├── TransferPermissionException.kt
├── ExcessQuantityException.kt
└── TransferConcurrencyException.kt
```

### DTOs (13)
```
src/main/kotlin/br/com/stockshift/dto/transfer/
├── request/
│   ├── CreateTransferRequest.kt
│   ├── UpdateTransferRequest.kt
│   ├── ScanItemRequest.kt
│   └── ResolveDiscrepancyRequest.kt
└── response/
    ├── TransferResponse.kt
    ├── TransferSummaryResponse.kt
    ├── TransferItemResponse.kt
    ├── TransferValidationResponse.kt
    ├── ValidationItemResponse.kt
    ├── ValidationSummary.kt
    ├── TransferDiscrepancyResponse.kt
    ├── LedgerEntryResponse.kt
    └── TransferEventResponse.kt
```

### Controllers (4)
```
src/main/kotlin/br/com/stockshift/controller/
├── TransferController.kt
├── TransferValidationController.kt
├── TransferDiscrepancyController.kt
└── TransferHistoryController.kt
```

### Tests (~10)
```
src/test/kotlin/br/com/stockshift/
├── service/transfer/
│   ├── TransferStateMachineTest.kt
│   ├── TransferRoleResolverTest.kt
│   ├── TransferServiceTest.kt
│   └── TransferValidationServiceTest.kt
└── integration/transfer/
    ├── TransferIntegrationTest.kt
    ├── TransferConcurrencyTest.kt
    ├── TransferDiscrepancyTest.kt
    └── TransferLedgerTest.kt
```

---

## Arquivos a Modificar (Total: ~5)

| Arquivo | Modificação |
|---------|-------------|
| `Batch.kt` | Adicionar originTransferId, originBatchId |
| `Permission.kt` | Adicionar novas permissões de transfer |
| `GlobalExceptionHandler.kt` | Handlers para novas exceções |
| `SecurityConfig.kt` | Configurar endpoints de transfer |
| `application.yml` | Configurações de transfer (políticas) |

---

## Dependências Entre Arquivos

```
Migrations
    ↓
Enums → Entities → Repositories
                       ↓
              Services (StateMachine → RoleResolver → LedgerService → TransferService)
                       ↓
              DTOs → Controllers
                       ↓
                    Tests
```

---

## Checklist de Implementação

### Fase 1: Fundação
- [ ] V26__create_transfer_tables.sql
- [ ] V27__create_inventory_ledger.sql
- [ ] V28__create_transfer_audit_tables.sql
- [ ] V29__add_transfer_permissions.sql
- [ ] V30__add_batch_origin_fields.sql
- [ ] Todos os enums (9 arquivos)
- [ ] Todas as entidades (7 arquivos)
- [ ] Modificar Batch.kt
- [ ] Todos os repositories (7 arquivos)
- [ ] Rodar migrations e verificar

### Fase 2: Lógica de Negócio
- [ ] TransferStateMachine.kt
- [ ] TransferRoleResolver.kt
- [ ] TransferEventPublisher.kt
- [ ] TransferLedgerService.kt
- [ ] TransferBatchResolver.kt
- [ ] TransferService.kt
- [ ] Testes unitários de services

### Fase 3: Validação
- [ ] TransferValidationService.kt
- [ ] TransferDiscrepancyService.kt
- [ ] Todas as exceptions (6 arquivos)
- [ ] Testes unitários de validação

### Fase 4: API
- [ ] Todos os DTOs request (4 arquivos)
- [ ] Todos os DTOs response (9 arquivos)
- [ ] TransferController.kt
- [ ] TransferValidationController.kt
- [ ] TransferDiscrepancyController.kt
- [ ] TransferHistoryController.kt
- [ ] Modificar GlobalExceptionHandler.kt
- [ ] Testes de integração

### Fase 5: Polimento
- [ ] Testes E2E completos (8 cenários)
- [ ] Testes de concorrência
- [ ] Verificar índices e performance
- [ ] Code review final

---

## Pronto para Implementar

A especificação completa está em:
- `.claude/refactoring/transfer-refactoring-spec.md`

Este plano de implementação está em:
- `.claude/refactoring/transfer-implementation-plan.md`

**Comando para iniciar:** Confirme qual fase/sprint deseja iniciar.
