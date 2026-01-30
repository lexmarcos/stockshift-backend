# Transfer Refactoring - Especificação Completa

## Problema

Atualmente ambos os warehouses (origem e destino) podem executar as mesmas ações em um stock-movement de transferência. Deveria haver separação clara:

- **Origem:** cria, edita, cancela, despacha (execute)
- **Destino:** recebe, valida, finaliza (validation)

---

## Etapa 1 — Novo Modelo de Domínio e Invariantes

### Objetivo

Separar claramente:
- **Transfer**: processo de negócio (workflow)
- **InventoryLedger**: registros contábeis que alteram saldo

### Entidades

#### Transfer (Agregado de Processo)

```
Transfer
├── id: UUID
├── tenantId: UUID
├── transferCode: String (código único legível, ex: "TRF-2024-00123")
├── status: TransferStatus
│
├── sourceWarehouseId: UUID
├── destinationWarehouseId: UUID
│
├── items: List<TransferItem>
│   ├── id: UUID
│   ├── productId: UUID
│   ├── sourceBatchId: UUID
│   ├── expectedQuantity: BigDecimal
│   ├── receivedQuantity: BigDecimal? (preenchido na validação)
│   └── destinationBatchId: UUID? (preenchido na validação)
│
├── notes: String?
│
├── createdBy: UUID
├── createdAt: Instant
├── dispatchedBy: UUID?
├── dispatchedAt: Instant?
├── validationStartedBy: UUID?
├── validationStartedAt: Instant?
├── completedBy: UUID?
├── completedAt: Instant?
└── cancelledBy: UUID?
    cancelledAt: Instant?
```

#### InventoryLedger (Registro Contábil)

```
InventoryLedger
├── id: UUID
├── tenantId: UUID
├── warehouseId: UUID
├── productId: UUID
├── batchId: UUID
│
├── entryType: LedgerEntryType
│   ├── PURCHASE_IN
│   ├── SALE_OUT
│   ├── ADJUSTMENT_IN
│   ├── ADJUSTMENT_OUT
│   ├── TRANSFER_OUT        ← debita origem
│   ├── TRANSFER_IN_TRANSIT ← crédito virtual (em trânsito)
│   ├── TRANSFER_IN         ← credita destino
│   └── TRANSFER_TRANSIT_CONSUMED ← baixa do trânsito
│
├── quantity: BigDecimal (sempre positivo, direção dada pelo entryType)
├── balanceAfter: BigDecimal (saldo do batch após este movimento)
│
├── referenceType: String (ex: "TRANSFER", "SALE", "PURCHASE")
├── referenceId: UUID (ex: transferId, saleId)
│
├── createdBy: UUID
├── createdAt: Instant
└── notes: String?
```

#### TransferInTransit (Saldo em Trânsito)

```
TransferInTransit
├── id: UUID
├── tenantId: UUID
├── transferId: UUID
├── transferItemId: UUID
├── productId: UUID
├── sourceBatchId: UUID
├── quantity: BigDecimal (quantidade ainda em trânsito)
├── createdAt: Instant
└── consumedAt: Instant?
```

### Invariantes

| # | Invariante | Enforcement |
|---|------------|-------------|
| I1 | Somente source warehouse pode despachar | Service layer + permission check |
| I2 | Somente destination warehouse pode validar/receber | Service layer + permission check |
| I3 | Saldo em trânsito nunca fica negativo | Constraint no TransferInTransit + validação |
| I4 | Transfer só pode ser despachada se status = DRAFT | State machine no agregado |
| I5 | Transfer só pode ser validada se status = IN_TRANSIT | State machine no agregado |
| I6 | Quantidade recebida não pode exceder esperada + tolerância | Política configurável |
| I7 | Ledger é append-only | Sem UPDATE/DELETE, apenas INSERT |
| I8 | Todo movimento de ledger tem referência rastreável | NOT NULL em referenceType/referenceId |
| I9 | Source e destination warehouse devem ser diferentes | Validação na criação |
| I10 | Batch de origem deve ter saldo suficiente para despacho | Validação antes de TRANSFER_OUT |

### Política de Lote no Destino

#### Regra Principal: Criar Lote Novo (Espelhado)

```
1. Gerar novo batchCode: "{originalBatchCode}-{destinationWarehouseCode}"
   Exemplo: "LOTE-2024-001" → "LOTE-2024-001-WH02"

2. Copiar metadados do lote original:
   - expirationDate
   - manufacturingDate
   - supplier (se aplicável)

3. Criar Batch com:
   - warehouseId = destinationWarehouseId
   - quantity = receivedQuantity
   - productId = mesmo do original

4. Registrar destinationBatchId no TransferItem
```

#### Exceção: Lote Compatível Existente

Se já existir um batch no destino com:
- Mesmo productId
- Mesmo batchCode base (sem sufixo de warehouse)
- Mesma expirationDate

→ Incrementar quantidade no batch existente.

### Política de Divergência

| Situação | Ação |
|----------|------|
| receivedQuantity < expectedQuantity | Registrar discrepância, completar com COMPLETED_WITH_DISCREPANCY, manter diferença em trânsito |
| receivedQuantity = expectedQuantity | Completar com COMPLETED |
| receivedQuantity > expectedQuantity | Rejeitar ou aceitar com flag de auditoria (configurável) |

### Fluxo de Movimentos no Ledger

```
DESPACHO (Source):
  1. TRANSFER_OUT       → warehouse=source  → batch=origem → qty=expected
  2. TRANSFER_IN_TRANSIT→ warehouse=virtual → transfer=X  → qty=expected
  3. TransferInTransit criado com quantity=expected

RECEBIMENTO (Destination):
  1. TRANSFER_IN              → warehouse=dest   → batch=novo → qty=received
  2. TRANSFER_TRANSIT_CONSUMED→ warehouse=virtual→ transfer=X → qty=received
  3. TransferInTransit.quantity -= received
  4. Se quantity=0, marcar consumedAt
```

---

## Etapa 2 — Máquina de Estados + Papéis

### Objetivo

Substituir o fluxo atual onde qualquer warehouse faz tudo por uma **máquina de estados** com ações permitidas conforme:
- Status do Transfer
- Papel do warehouse (OUTBOUND/INBOUND)
- Permissões do usuário no warehouse

### Estados do Transfer (TransferStatus)

```
┌─────────┐
│  DRAFT  │ ← Estado inicial (criada, editável)
└────┬────┘
     │ dispatch()
     ▼
┌─────────────┐
│  IN_TRANSIT │ ← Despachada, aguardando destino
└──────┬──────┘
       │ startValidation()
       ▼
┌────────────────────────┐
│ VALIDATION_IN_PROGRESS │ ← Sessão de validação aberta
└──────────┬─────────────┘
           │ completeValidation()
           ▼
     ┌─────┴─────┐
     ▼           ▼
┌───────────┐ ┌─────────────────────────────┐
│ COMPLETED │ │ COMPLETED_WITH_DISCREPANCY  │
└───────────┘ └─────────────────────────────┘

Estado especial (a partir de DRAFT apenas):
┌───────────┐
│ CANCELLED │
└───────────┘
```

### Enum TransferStatus

```kotlin
enum class TransferStatus {
    DRAFT,                      // Criada, editável
    IN_TRANSIT,                 // Despachada, em trânsito
    VALIDATION_IN_PROGRESS,     // Validação em andamento no destino
    COMPLETED,                  // Finalizada sem divergência
    COMPLETED_WITH_DISCREPANCY, // Finalizada com divergência
    CANCELLED                   // Cancelada antes do despacho
}
```

### Papéis (TransferRole)

```kotlin
enum class TransferRole {
    OUTBOUND,  // Usuário pertence ao warehouse de ORIGEM
    INBOUND,   // Usuário pertence ao warehouse de DESTINO
    NONE       // Usuário não tem acesso a nenhum dos warehouses
}
```

### Determinação do Papel do Usuário

```kotlin
fun determineUserRole(transfer: Transfer, userWarehouseIds: Set<UUID>): TransferRole {
    return when {
        transfer.sourceWarehouseId in userWarehouseIds -> TransferRole.OUTBOUND
        transfer.destinationWarehouseId in userWarehouseIds -> TransferRole.INBOUND
        else -> TransferRole.NONE
    }
}
```

**Regra:** Se o usuário tem acesso a ambos os warehouses, prevalece OUTBOUND (origem tem prioridade para evitar conflitos).

### Ações e Permissões por Papel

#### Matriz de Ações

| Ação | Papel | Status Requerido | Novo Status | Permissão Necessária |
|------|-------|------------------|-------------|---------------------|
| `create` | OUTBOUND | - | DRAFT | `TRANSFER_CREATE` |
| `update` | OUTBOUND | DRAFT | DRAFT | `TRANSFER_UPDATE` |
| `cancel` | OUTBOUND | DRAFT | CANCELLED | `TRANSFER_DELETE` |
| `dispatch` | OUTBOUND | DRAFT | IN_TRANSIT | `TRANSFER_EXECUTE` |
| `startValidation` | INBOUND | IN_TRANSIT | VALIDATION_IN_PROGRESS | `TRANSFER_VALIDATE` |
| `scanItem` | INBOUND | VALIDATION_IN_PROGRESS | VALIDATION_IN_PROGRESS | `TRANSFER_VALIDATE` |
| `completeValidation` | INBOUND | VALIDATION_IN_PROGRESS | COMPLETED ou COMPLETED_WITH_DISCREPANCY | `TRANSFER_VALIDATE` |

#### Tabela de Transições Válidas

| De | Para | Ação | Papel |
|----|------|------|-------|
| (novo) | DRAFT | create | OUTBOUND |
| DRAFT | DRAFT | update | OUTBOUND |
| DRAFT | IN_TRANSIT | dispatch | OUTBOUND |
| DRAFT | CANCELLED | cancel | OUTBOUND |
| IN_TRANSIT | VALIDATION_IN_PROGRESS | startValidation | INBOUND |
| VALIDATION_IN_PROGRESS | COMPLETED | completeValidation (sem divergência) | INBOUND |
| VALIDATION_IN_PROGRESS | COMPLETED_WITH_DISCREPANCY | completeValidation (com divergência) | INBOUND |

#### Transições Inválidas (Bloqueadas)

| Tentativa | Motivo |
|-----------|--------|
| INBOUND tenta dispatch | Papel incorreto |
| OUTBOUND tenta startValidation | Papel incorreto |
| cancel em IN_TRANSIT | Status não permite (já despachada) |
| update em IN_TRANSIT | Status não permite (já despachada) |
| dispatch em CANCELLED | Status terminal |
| qualquer ação em COMPLETED* | Status terminal |

### Implementação da State Machine

```kotlin
class TransferStateMachine {

    fun canTransition(
        currentStatus: TransferStatus,
        action: TransferAction,
        role: TransferRole
    ): Boolean {
        return allowedTransitions[Triple(currentStatus, action, role)] != null
    }

    fun transition(
        currentStatus: TransferStatus,
        action: TransferAction,
        role: TransferRole
    ): TransferStatus {
        return allowedTransitions[Triple(currentStatus, action, role)]
            ?: throw InvalidTransferStateException(
                "Cannot $action transfer in status $currentStatus with role $role"
            )
    }

    companion object {
        private val allowedTransitions = mapOf(
            // OUTBOUND actions
            Triple(TransferStatus.DRAFT, TransferAction.UPDATE, TransferRole.OUTBOUND)
                to TransferStatus.DRAFT,
            Triple(TransferStatus.DRAFT, TransferAction.CANCEL, TransferRole.OUTBOUND)
                to TransferStatus.CANCELLED,
            Triple(TransferStatus.DRAFT, TransferAction.DISPATCH, TransferRole.OUTBOUND)
                to TransferStatus.IN_TRANSIT,

            // INBOUND actions
            Triple(TransferStatus.IN_TRANSIT, TransferAction.START_VALIDATION, TransferRole.INBOUND)
                to TransferStatus.VALIDATION_IN_PROGRESS,
            Triple(TransferStatus.VALIDATION_IN_PROGRESS, TransferAction.SCAN_ITEM, TransferRole.INBOUND)
                to TransferStatus.VALIDATION_IN_PROGRESS,
            Triple(TransferStatus.VALIDATION_IN_PROGRESS, TransferAction.COMPLETE, TransferRole.INBOUND)
                to TransferStatus.COMPLETED, // ou COMPLETED_WITH_DISCREPANCY (decidido na lógica)
        )
    }
}

enum class TransferAction {
    CREATE,
    UPDATE,
    CANCEL,
    DISPATCH,
    START_VALIDATION,
    SCAN_ITEM,
    COMPLETE
}
```

### Comportamento em Divergência

#### Critério de Divergência

```kotlin
data class ValidationResult(
    val hasDiscrepancy: Boolean,
    val discrepancies: List<TransferDiscrepancy>
)

fun evaluateValidation(transfer: Transfer): ValidationResult {
    val discrepancies = transfer.items.mapNotNull { item ->
        val expected = item.expectedQuantity
        val received = item.receivedQuantity ?: BigDecimal.ZERO

        when {
            received < expected -> TransferDiscrepancy(
                transferItemId = item.id,
                type = DiscrepancyType.SHORTAGE,
                expectedQuantity = expected,
                receivedQuantity = received,
                difference = expected - received
            )
            received > expected -> TransferDiscrepancy(
                transferItemId = item.id,
                type = DiscrepancyType.EXCESS,
                expectedQuantity = expected,
                receivedQuantity = received,
                difference = received - expected
            )
            else -> null
        }
    }

    return ValidationResult(
        hasDiscrepancy = discrepancies.isNotEmpty(),
        discrepancies = discrepancies
    )
}
```

#### Impacto no Ledger por Tipo de Divergência

| Tipo | Ledger | TransferInTransit |
|------|--------|-------------------|
| Sem divergência | TRANSFER_IN = expectedQty | Consumido totalmente |
| SHORTAGE (falta) | TRANSFER_IN = receivedQty | Mantém diferença pendente |
| EXCESS (excesso) | TRANSFER_IN = receivedQty* | Consumido + ADJUSTMENT_IN para excesso |

*Se política permitir excesso; caso contrário, rejeitar.

### Segurança e Autorização

#### Validação no Service Layer

```kotlin
@Service
class TransferService(
    private val stateMachine: TransferStateMachine,
    private val securityContext: SecurityContext
) {

    fun dispatch(transferId: UUID) {
        val transfer = repository.findById(transferId)
        val user = securityContext.currentUser

        // 1. Determinar papel
        val role = determineUserRole(transfer, user.warehouseIds)

        // 2. Verificar se tem permissão no warehouse
        requirePermission(user, transfer.sourceWarehouseId, Permission.TRANSFER_EXECUTE)

        // 3. Verificar transição válida
        if (!stateMachine.canTransition(transfer.status, TransferAction.DISPATCH, role)) {
            throw ForbiddenException("User cannot dispatch this transfer")
        }

        // 4. Executar
        doDispatch(transfer)
    }
}
```

#### Novas Permissões (a adicionar)

```kotlin
enum class Permission {
    // ... existentes ...

    // Transfer-specific
    TRANSFER_CREATE,    // Criar transferência (origem)
    TRANSFER_UPDATE,    // Editar transferência em DRAFT (origem)
    TRANSFER_DELETE,    // Cancelar transferência em DRAFT (origem)
    TRANSFER_EXECUTE,   // Despachar transferência (origem)
    TRANSFER_VALIDATE,  // Validar/receber transferência (destino)
    TRANSFER_VIEW,      // Visualizar transferências
}
```

### Visibilidade de Transferências

| Papel | Pode Ver | Detalhes |
|-------|----------|----------|
| OUTBOUND | Todas que criou/origem é seu warehouse | Ver itens, status, destino |
| INBOUND | Todas onde destino é seu warehouse | Ver itens esperados, iniciar validação |
| Ambos | União das duas visões | Indicar papel atual na resposta |

### Critérios de Conclusão

| Critério | Status |
|----------|--------|
| Tabela de transições válidas por papel | ✅ Definido |
| Comportamento em caso de divergência | ✅ SHORTAGE mantém em trânsito, EXCESS configurável |
| Como identificar papel do usuário | ✅ Via warehouseIds do usuário vs source/destination |
| Permissões por ação | ✅ Mapeado para cada ação |

---

## Etapa 3 — Redesenhar a API

### Objetivo

Refletir o novo domínio na API, eliminando a ambiguidade de `/stock-movements` com tipo TRANSFER e criando endpoints orientados ao processo de Transfer.

### Estratégia

1. **`/stockshift/transfers`** — Recurso principal do processo de transferência
2. **`/stockshift/stock-movements`** — Mantido apenas para consultas/auditoria e outros tipos (PURCHASE, SALE, ADJUSTMENT)
3. Transferências geram ledger internamente; consumidores não criam ledger diretamente

---

### Endpoints de Transfer

#### Lifecycle do Transfer (OUTBOUND - Origem)

```
POST   /stockshift/transfers
       → Criar transfer em DRAFT
       → Requer: TRANSFER_CREATE no sourceWarehouse
       → Body: CreateTransferRequest
       → Response: TransferResponse (status: DRAFT)

GET    /stockshift/transfers/{id}
       → Obter detalhes do transfer
       → Requer: TRANSFER_VIEW no source OU destination warehouse
       → Response: TransferResponse

PUT    /stockshift/transfers/{id}
       → Atualizar transfer em DRAFT
       → Requer: TRANSFER_UPDATE no sourceWarehouse
       → Apenas em status DRAFT
       → Body: UpdateTransferRequest
       → Response: TransferResponse

DELETE /stockshift/transfers/{id}
       → Cancelar transfer em DRAFT
       → Requer: TRANSFER_DELETE no sourceWarehouse
       → Apenas em status DRAFT
       → Response: 204 No Content

POST   /stockshift/transfers/{id}/dispatch
       → Despachar transfer (DRAFT → IN_TRANSIT)
       → Requer: TRANSFER_EXECUTE no sourceWarehouse
       → Gera: TRANSFER_OUT + TRANSFER_IN_TRANSIT no ledger
       → Response: TransferResponse (status: IN_TRANSIT)
```

#### Validação do Transfer (INBOUND - Destino)

```
POST   /stockshift/transfers/{id}/validation/start
       → Iniciar sessão de validação (IN_TRANSIT → VALIDATION_IN_PROGRESS)
       → Requer: TRANSFER_VALIDATE no destinationWarehouse
       → Response: TransferValidationResponse

POST   /stockshift/transfers/{id}/validation/scan
       → Escanear item durante validação
       → Requer: TRANSFER_VALIDATE no destinationWarehouse
       → Apenas em status VALIDATION_IN_PROGRESS
       → Body: ScanItemRequest
       → Response: TransferValidationResponse (atualizado)

POST   /stockshift/transfers/{id}/validation/complete
       → Finalizar validação (VALIDATION_IN_PROGRESS → COMPLETED*)
       → Requer: TRANSFER_VALIDATE no destinationWarehouse
       → Gera: TRANSFER_IN + TRANSFER_TRANSIT_CONSUMED no ledger
       → Response: TransferResponse (status: COMPLETED ou COMPLETED_WITH_DISCREPANCY)
```

#### Listagem e Consulta

```
GET    /stockshift/transfers
       → Listar transfers visíveis para o usuário
       → Query params: warehouseId, status, direction, page, size, sort
       → Response: Page<TransferSummaryResponse>

GET    /stockshift/transfers/{id}/ledger
       → Obter movimentos de ledger associados ao transfer
       → Response: List<LedgerEntryResponse>

GET    /stockshift/transfers/{id}/discrepancies
       → Obter discrepâncias (se houver)
       → Response: List<TransferDiscrepancyResponse>
```

---

### Request DTOs

#### CreateTransferRequest

```kotlin
data class CreateTransferRequest(
    val sourceWarehouseId: UUID,
    val destinationWarehouseId: UUID,
    val items: List<TransferItemRequest>,
    val notes: String? = null
)

data class TransferItemRequest(
    val productId: UUID,
    val batchId: UUID,
    val quantity: BigDecimal
)
```

#### UpdateTransferRequest

```kotlin
data class UpdateTransferRequest(
    val items: List<TransferItemRequest>? = null,
    val notes: String? = null
)
```

#### ScanItemRequest

```kotlin
data class ScanItemRequest(
    val barcode: String,          // Código de barras escaneado
    val quantity: BigDecimal = BigDecimal.ONE
)
```

---

### Response DTOs

#### TransferResponse (Detalhado)

```kotlin
data class TransferResponse(
    val id: UUID,
    val transferCode: String,
    val status: TransferStatus,

    // Warehouses
    val sourceWarehouse: WarehouseSummary,
    val destinationWarehouse: WarehouseSummary,

    // Itens
    val items: List<TransferItemResponse>,

    // Metadados derivados para UI
    val direction: TransferDirection,           // OUTBOUND ou INBOUND
    val allowedActions: List<TransferAction>,   // Ações permitidas para o usuário atual
    val summary: TransferSummary,               // Resumo de quantidades

    // Auditoria
    val createdBy: UserSummary,
    val createdAt: Instant,
    val dispatchedBy: UserSummary?,
    val dispatchedAt: Instant?,
    val completedBy: UserSummary?,
    val completedAt: Instant?,

    val notes: String?
)

data class TransferItemResponse(
    val id: UUID,
    val product: ProductSummary,
    val sourceBatch: BatchSummary,
    val expectedQuantity: BigDecimal,
    val receivedQuantity: BigDecimal?,       // null se ainda não validado
    val destinationBatch: BatchSummary?,     // null se ainda não recebido
    val status: TransferItemStatus           // PENDING, RECEIVED, PARTIAL, MISSING
)

data class TransferSummary(
    val totalItems: Int,
    val totalExpectedQuantity: BigDecimal,
    val totalReceivedQuantity: BigDecimal?,  // null se não iniciou validação
    val itemsValidated: Int,
    val hasDiscrepancy: Boolean
)

enum class TransferDirection {
    OUTBOUND,  // Usuário está no warehouse de origem
    INBOUND    // Usuário está no warehouse de destino
}

enum class TransferItemStatus {
    PENDING,   // Aguardando validação
    RECEIVED,  // Recebido com quantidade correta
    PARTIAL,   // Recebido com quantidade menor
    EXCESS,    // Recebido com quantidade maior
    MISSING    // Não recebido (quantidade = 0)
}
```

#### TransferSummaryResponse (Para Listagem)

```kotlin
data class TransferSummaryResponse(
    val id: UUID,
    val transferCode: String,
    val status: TransferStatus,
    val sourceWarehouse: WarehouseSummary,
    val destinationWarehouse: WarehouseSummary,
    val direction: TransferDirection,
    val allowedActions: List<TransferAction>,
    val itemCount: Int,
    val totalQuantity: BigDecimal,
    val createdAt: Instant,
    val dispatchedAt: Instant?,
    val completedAt: Instant?
)
```

#### TransferValidationResponse

```kotlin
data class TransferValidationResponse(
    val transferId: UUID,
    val status: TransferStatus,
    val validationStartedAt: Instant,
    val validationStartedBy: UserSummary,
    val items: List<ValidationItemResponse>,
    val summary: ValidationSummary,
    val allowedActions: List<TransferAction>
)

data class ValidationItemResponse(
    val transferItemId: UUID,
    val product: ProductSummary,
    val sourceBatch: BatchSummary,
    val expectedQuantity: BigDecimal,
    val scannedQuantity: BigDecimal,
    val status: TransferItemStatus
)

data class ValidationSummary(
    val totalItems: Int,
    val itemsScanned: Int,
    val itemsPending: Int,
    val hasDiscrepancy: Boolean,
    val canComplete: Boolean  // true se pelo menos um item foi escaneado
)
```

---

### Cálculo de allowedActions

```kotlin
fun calculateAllowedActions(
    transfer: Transfer,
    userRole: TransferRole,
    userPermissions: Set<Permission>
): List<TransferAction> {
    val actions = mutableListOf<TransferAction>()

    when (userRole) {
        TransferRole.OUTBOUND -> {
            if (transfer.status == TransferStatus.DRAFT) {
                if (Permission.TRANSFER_UPDATE in userPermissions) {
                    actions.add(TransferAction.UPDATE)
                }
                if (Permission.TRANSFER_DELETE in userPermissions) {
                    actions.add(TransferAction.CANCEL)
                }
                if (Permission.TRANSFER_EXECUTE in userPermissions) {
                    actions.add(TransferAction.DISPATCH)
                }
            }
            // OUTBOUND não tem ações após dispatch
        }

        TransferRole.INBOUND -> {
            if (Permission.TRANSFER_VALIDATE in userPermissions) {
                when (transfer.status) {
                    TransferStatus.IN_TRANSIT -> {
                        actions.add(TransferAction.START_VALIDATION)
                    }
                    TransferStatus.VALIDATION_IN_PROGRESS -> {
                        actions.add(TransferAction.SCAN_ITEM)
                        actions.add(TransferAction.COMPLETE)
                    }
                    else -> { /* sem ações */ }
                }
            }
        }

        TransferRole.NONE -> { /* sem ações */ }
    }

    return actions
}
```

---

### Filtros de Listagem

```
GET /stockshift/transfers?warehouseId={uuid}&direction=INBOUND&status=IN_TRANSIT
```

| Parâmetro | Tipo | Descrição |
|-----------|------|-----------|
| `warehouseId` | UUID | Filtrar por warehouse (obrigatório se usuário tem múltiplos) |
| `direction` | OUTBOUND/INBOUND | Filtrar por direção relativa ao warehouse |
| `status` | TransferStatus | Filtrar por status |
| `fromDate` | ISO DateTime | Transfers criados a partir de |
| `toDate` | ISO DateTime | Transfers criados até |
| `page` | Int | Página (default: 0) |
| `size` | Int | Tamanho (default: 20, max: 100) |
| `sort` | String | Campo de ordenação (default: createdAt,desc) |

---

### Endpoints Legados (Deprecar/Remover)

| Endpoint Antigo | Ação |
|-----------------|------|
| `POST /stockshift/stock-movements` (type=TRANSFER) | ❌ Remover - usar `/transfers` |
| `POST /stockshift/stock-movements/{id}/execute` | ❌ Remover - usar `/transfers/{id}/dispatch` |
| `POST /stockshift/stock-movements/{id}/validation/*` | ❌ Remover - usar `/transfers/{id}/validation/*` |

**Manter para outros tipos:**
```
GET  /stockshift/stock-movements          → Consulta/auditoria (todos os tipos)
GET  /stockshift/stock-movements/{id}     → Detalhes de movimento
POST /stockshift/stock-movements          → Apenas PURCHASE, SALE, ADJUSTMENT
POST /stockshift/stock-movements/{id}/execute → Apenas para não-TRANSFER
```

---

### Exemplos de Uso pela UI

#### 1. Listagem de Transfers Pendentes (Destino)

```http
GET /stockshift/transfers?warehouseId=wh-destino&direction=INBOUND&status=IN_TRANSIT
```

Response:
```json
{
  "content": [
    {
      "id": "trf-123",
      "transferCode": "TRF-2024-00123",
      "status": "IN_TRANSIT",
      "sourceWarehouse": { "id": "wh-origem", "name": "Depósito Central" },
      "destinationWarehouse": { "id": "wh-destino", "name": "Loja 01" },
      "direction": "INBOUND",
      "allowedActions": ["START_VALIDATION"],
      "itemCount": 5,
      "totalQuantity": 150,
      "dispatchedAt": "2024-01-15T10:30:00Z"
    }
  ]
}
```

#### 2. UI Renderiza Baseado em allowedActions

```typescript
// Frontend
const canStartValidation = transfer.allowedActions.includes('START_VALIDATION');
const canDispatch = transfer.allowedActions.includes('DISPATCH');

return (
  <div>
    {canDispatch && <Button onClick={dispatch}>Despachar</Button>}
    {canStartValidation && <Button onClick={startValidation}>Iniciar Recebimento</Button>}
  </div>
);
```

---

### Critérios de Conclusão

| Critério | Status |
|----------|--------|
| API explicita quem é responsável por cada etapa | ✅ Endpoints separados para origem/destino |
| Destino não consegue dispatch, origem não consegue validate | ✅ Validação por role + state machine |
| UI pode montar tela a partir de direction + allowedActions | ✅ Campos incluídos em todas as respostas |
| Endpoints legados identificados para remoção | ✅ Mapeado |

---

## Etapa 4 — Regras de Estoque via Ledger

### Objetivo

Garantir integridade do inventário com um **ledger consistente**, evitando "limbo" de estoque e permitindo auditoria completa de cada unidade movimentada.

### Princípio Fundamental

> **Todo saldo é derivado do ledger. Não existe atualização de estoque sem registro correspondente.**

O campo `Batch.quantity` é uma **visão materializada** do ledger, atualizada transacionalmente junto com cada entrada.

---

### Movimentos de Ledger para Transfer

| Tipo | Momento | Warehouse | Efeito no Saldo | Referência |
|------|---------|-----------|-----------------|------------|
| `TRANSFER_OUT` | Despacho | Origem | -quantity | transferId, transferItemId, batchId |
| `TRANSFER_IN_TRANSIT` | Despacho | Virtual* | +quantity | transferId, transferItemId |
| `TRANSFER_IN` | Recebimento | Destino | +quantity | transferId, transferItemId, batchId |
| `TRANSFER_TRANSIT_CONSUMED` | Recebimento | Virtual* | -quantity | transferId, transferItemId |
| `TRANSFER_LOSS` | Resolução divergência | Virtual* | -quantity | transferId, transferItemId, resolutionId |

*Virtual = entidade contábil de trânsito, não um warehouse físico. Pode ser implementado como `warehouseId = null` com flag `isTransit = true`, ou um warehouse especial por tenant.

---

### Fluxo Detalhado: Despacho

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           DISPATCH TRANSACTION                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  1. VALIDAR                                                                 │
│     ├── Transfer.status == DRAFT                                           │
│     ├── Usuário tem papel OUTBOUND                                         │
│     └── Para cada item:                                                     │
│         └── Batch.quantity >= item.expectedQuantity                        │
│                                                                             │
│  2. REGISTRAR LEDGER (para cada item)                                      │
│     ├── INSERT InventoryLedger (TRANSFER_OUT)                              │
│     │   ├── warehouseId = sourceWarehouseId                                │
│     │   ├── batchId = item.sourceBatchId                                   │
│     │   ├── quantity = item.expectedQuantity                               │
│     │   └── balanceAfter = batch.quantity - item.expectedQuantity          │
│     │                                                                       │
│     └── INSERT InventoryLedger (TRANSFER_IN_TRANSIT)                       │
│         ├── warehouseId = null (virtual)                                   │
│         ├── transferId = transfer.id                                       │
│         ├── transferItemId = item.id                                       │
│         └── quantity = item.expectedQuantity                               │
│                                                                             │
│  3. ATUALIZAR SALDO MATERIALIZADO                                          │
│     └── Batch.quantity -= item.expectedQuantity                            │
│                                                                             │
│  4. CRIAR REGISTRO DE TRÂNSITO                                             │
│     └── INSERT TransferInTransit                                           │
│         ├── transferId, transferItemId                                     │
│         ├── productId, sourceBatchId                                       │
│         └── quantity = item.expectedQuantity                               │
│                                                                             │
│  5. ATUALIZAR TRANSFER                                                     │
│     ├── status = IN_TRANSIT                                                │
│     ├── dispatchedBy = currentUser                                         │
│     └── dispatchedAt = now()                                               │
│                                                                             │
│  6. COMMIT TRANSACTION                                                     │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### Código Conceitual

```kotlin
@Transactional
fun dispatch(transferId: UUID) {
    val transfer = transferRepository.findByIdForUpdate(transferId)  // SELECT FOR UPDATE

    // Validações
    validateStatus(transfer, TransferStatus.DRAFT)
    validateUserRole(transfer, TransferRole.OUTBOUND)

    transfer.items.forEach { item ->
        val batch = batchRepository.findByIdForUpdate(item.sourceBatchId)

        // Validar saldo
        if (batch.quantity < item.expectedQuantity) {
            throw InsufficientStockException(
                "Batch ${batch.batchCode} has ${batch.quantity}, needs ${item.expectedQuantity}"
            )
        }

        // Registrar TRANSFER_OUT
        ledgerRepository.save(InventoryLedger(
            tenantId = transfer.tenantId,
            warehouseId = transfer.sourceWarehouseId,
            productId = item.productId,
            batchId = item.sourceBatchId,
            entryType = LedgerEntryType.TRANSFER_OUT,
            quantity = item.expectedQuantity,
            balanceAfter = batch.quantity - item.expectedQuantity,
            referenceType = "TRANSFER",
            referenceId = transfer.id,
            createdBy = currentUserId()
        ))

        // Registrar TRANSFER_IN_TRANSIT
        ledgerRepository.save(InventoryLedger(
            tenantId = transfer.tenantId,
            warehouseId = null,  // virtual
            productId = item.productId,
            batchId = null,
            entryType = LedgerEntryType.TRANSFER_IN_TRANSIT,
            quantity = item.expectedQuantity,
            referenceType = "TRANSFER",
            referenceId = transfer.id,
            transferItemId = item.id,
            createdBy = currentUserId()
        ))

        // Atualizar saldo materializado
        batch.quantity -= item.expectedQuantity
        batchRepository.save(batch)

        // Criar registro de trânsito
        transferInTransitRepository.save(TransferInTransit(
            tenantId = transfer.tenantId,
            transferId = transfer.id,
            transferItemId = item.id,
            productId = item.productId,
            sourceBatchId = item.sourceBatchId,
            quantity = item.expectedQuantity
        ))
    }

    // Atualizar transfer
    transfer.status = TransferStatus.IN_TRANSIT
    transfer.dispatchedBy = currentUserId()
    transfer.dispatchedAt = Instant.now()
    transferRepository.save(transfer)
}
```

---

### Fluxo Detalhado: Recebimento/Validação

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      COMPLETE VALIDATION TRANSACTION                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  1. VALIDAR                                                                 │
│     ├── Transfer.status == VALIDATION_IN_PROGRESS                          │
│     ├── Usuário tem papel INBOUND                                          │
│     └── Pelo menos um item foi escaneado                                   │
│                                                                             │
│  2. PARA CADA ITEM COM receivedQuantity > 0                                │
│     │                                                                       │
│     ├── 2a. RESOLVER BATCH DE DESTINO                                      │
│     │   ├── Buscar batch compatível no destino                             │
│     │   │   (mesmo product, batchCode base, expirationDate)                │
│     │   ├── Se encontrar: usar existente                                   │
│     │   └── Se não: criar novo batch espelhado                             │
│     │                                                                       │
│     ├── 2b. REGISTRAR LEDGER                                               │
│     │   ├── INSERT InventoryLedger (TRANSFER_IN)                           │
│     │   │   ├── warehouseId = destinationWarehouseId                       │
│     │   │   ├── batchId = destinationBatchId                               │
│     │   │   ├── quantity = receivedQuantity                                │
│     │   │   └── balanceAfter = batch.quantity + receivedQuantity           │
│     │   │                                                                   │
│     │   └── INSERT InventoryLedger (TRANSFER_TRANSIT_CONSUMED)             │
│     │       ├── warehouseId = null (virtual)                               │
│     │       └── quantity = receivedQuantity                                │
│     │                                                                       │
│     ├── 2c. ATUALIZAR SALDO MATERIALIZADO                                  │
│     │   └── destinationBatch.quantity += receivedQuantity                  │
│     │                                                                       │
│     └── 2d. CONSUMIR TRÂNSITO                                              │
│         ├── TransferInTransit.quantity -= receivedQuantity                 │
│         └── Se quantity == 0: TransferInTransit.consumedAt = now()         │
│                                                                             │
│  3. AVALIAR DIVERGÊNCIAS                                                   │
│     ├── Se algum item: received < expected → hasDiscrepancy = true         │
│     ├── Registrar TransferDiscrepancy para cada divergência                │
│     └── Itens com received > expected: aplicar política (ver abaixo)       │
│                                                                             │
│  4. ATUALIZAR TRANSFER                                                     │
│     ├── status = COMPLETED ou COMPLETED_WITH_DISCREPANCY                   │
│     ├── completedBy = currentUser                                          │
│     └── completedAt = now()                                                │
│                                                                             │
│  5. COMMIT TRANSACTION                                                     │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### Política de Criação de Batch no Destino

```kotlin
fun resolveDestinationBatch(
    transfer: Transfer,
    item: TransferItem,
    sourceBatch: Batch
): Batch {
    // Tentar encontrar batch compatível
    val existingBatch = batchRepository.findCompatible(
        warehouseId = transfer.destinationWarehouseId,
        productId = item.productId,
        batchCodeBase = sourceBatch.batchCode.substringBefore("-"),
        expirationDate = sourceBatch.expirationDate
    )

    if (existingBatch != null) {
        return existingBatch
    }

    // Criar novo batch espelhado
    val destinationWarehouse = warehouseRepository.findById(transfer.destinationWarehouseId)

    return batchRepository.save(Batch(
        tenantId = transfer.tenantId,
        productId = item.productId,
        warehouseId = transfer.destinationWarehouseId,
        batchCode = "${sourceBatch.batchCode}-${destinationWarehouse.code}",
        quantity = BigDecimal.ZERO,  // Será incrementado pelo ledger
        expirationDate = sourceBatch.expirationDate,
        manufacturingDate = sourceBatch.manufacturingDate,
        // Copiar outros metadados relevantes
        originTransferId = transfer.id,
        originBatchId = sourceBatch.id
    ))
}
```

---

### Políticas de Divergência

#### Falta (SHORTAGE): `received < expected`

| Política | Descrição | Implementação |
|----------|-----------|---------------|
| **KEEP_PENDING** (Recomendado) | Manter diferença em trânsito para investigação | TransferInTransit.quantity mantém saldo residual |
| **AUTO_LOSS** | Baixar automaticamente como perda | Gerar TRANSFER_LOSS no ledger + zerar TransferInTransit |

```kotlin
// Configurável por tenant
enum class ShortagePolicy {
    KEEP_PENDING,  // Default - exige resolução manual
    AUTO_LOSS      // Baixa automática
}

fun handleShortage(
    item: TransferItem,
    shortage: BigDecimal,
    policy: ShortagePolicy
) {
    when (policy) {
        ShortagePolicy.KEEP_PENDING -> {
            // TransferInTransit já tem o saldo residual
            // Apenas registrar discrepância para investigação
            transferDiscrepancyRepository.save(TransferDiscrepancy(
                transferId = item.transferId,
                transferItemId = item.id,
                type = DiscrepancyType.SHORTAGE,
                quantity = shortage,
                status = DiscrepancyStatus.PENDING_RESOLUTION
            ))
        }
        ShortagePolicy.AUTO_LOSS -> {
            // Registrar perda no ledger
            ledgerRepository.save(InventoryLedger(
                entryType = LedgerEntryType.TRANSFER_LOSS,
                quantity = shortage,
                referenceType = "TRANSFER_DISCREPANCY",
                // ...
            ))
            // Zerar TransferInTransit
            transferInTransit.quantity = BigDecimal.ZERO
            transferInTransit.consumedAt = Instant.now()
        }
    }
}
```

#### Excesso (EXCESS): `received > expected`

| Política | Descrição | Implementação |
|----------|-----------|---------------|
| **REJECT** (Default) | Não permitir quantidade maior que esperada | Lançar exceção na validação do scan |
| **ACCEPT_WITH_FLAG** | Aceitar mas exigir justificativa | Registrar no ledger + criar discrepância para auditoria |

```kotlin
enum class ExcessPolicy {
    REJECT,           // Default - bloqueia
    ACCEPT_WITH_FLAG  // Aceita com auditoria
}

fun validateScan(
    item: TransferItem,
    scannedQuantity: BigDecimal,
    policy: ExcessPolicy
) {
    val newTotal = (item.receivedQuantity ?: BigDecimal.ZERO) + scannedQuantity

    if (newTotal > item.expectedQuantity) {
        when (policy) {
            ExcessPolicy.REJECT -> {
                throw ExcessQuantityException(
                    "Cannot receive more than expected. " +
                    "Expected: ${item.expectedQuantity}, Scanned: $newTotal"
                )
            }
            ExcessPolicy.ACCEPT_WITH_FLAG -> {
                // Permitir, mas marcar para auditoria
                item.hasExcessFlag = true
            }
        }
    }

    item.receivedQuantity = newTotal
}
```

---

### Resolução Manual de Pendências

Para itens que ficaram em trânsito (política KEEP_PENDING):

```
POST /stockshift/transfers/{id}/discrepancies/{discrepancyId}/resolve
```

```kotlin
data class ResolveDiscrepancyRequest(
    val resolution: DiscrepancyResolution,
    val justification: String
)

enum class DiscrepancyResolution {
    WRITE_OFF,      // Baixar como perda
    FOUND,          // Item encontrado - criar entrada manual
    RETURN_TRANSIT  // Devolver à origem (gera transfer reversa)
}
```

---

### Consistência do Saldo Materializado

#### Invariante

```sql
-- O saldo do batch deve sempre igualar a soma do ledger
SELECT b.id, b.quantity as materialized,
       COALESCE(SUM(
           CASE
               WHEN l.entry_type IN ('PURCHASE_IN', 'ADJUSTMENT_IN', 'TRANSFER_IN', 'RETURN_IN')
               THEN l.quantity
               ELSE -l.quantity
           END
       ), 0) as calculated
FROM batch b
LEFT JOIN inventory_ledger l ON l.batch_id = b.id
GROUP BY b.id
HAVING b.quantity != calculated;
-- Deve retornar 0 linhas
```

#### Job de Reconciliação (Safety Net)

```kotlin
@Scheduled(cron = "0 0 2 * * *")  // Daily at 2 AM
fun reconcileBatchQuantities() {
    val discrepancies = ledgerRepository.findQuantityDiscrepancies()

    if (discrepancies.isNotEmpty()) {
        alertService.sendCriticalAlert(
            "Batch quantity mismatch detected",
            discrepancies
        )
        // Não corrigir automaticamente - exigir investigação
    }
}
```

---

### Diagrama de Saldo ao Longo do Tempo

```
Origem (Batch A)          Trânsito              Destino (Batch B)
     100                     0                       0
      │                      │                       │
      │ ── DISPATCH ──────►  │                       │
      │    (-50 OUT)         │ (+50 IN_TRANSIT)      │
      │                      │                       │
     50                     50                       0
      │                      │                       │
      │                      │ ── COMPLETE ─────────►│
      │                      │    (-50 CONSUMED)     │ (+50 IN)
      │                      │                       │
     50                      0                      50
```

Com divergência (recebeu 40 de 50):
```
Origem (Batch A)          Trânsito              Destino (Batch B)
     100                     0                       0
      │                      │                       │
      │ ── DISPATCH ──────►  │                       │
     50                     50                       0
      │                      │                       │
      │                      │ ── COMPLETE ─────────►│
      │                      │    (-40 CONSUMED)     │ (+40 IN)
      │                      │                       │
     50                     10 ← PENDENTE           40
                             │
                             │ (resolução manual)
                             │ ── WRITE_OFF ────────►
                             │    (-10 LOSS)
                             0
```

---

### Critérios de Conclusão

| Critério | Status |
|----------|--------|
| Conjunto fechado de movimentos para Transfer | ✅ OUT, IN_TRANSIT, IN, TRANSIT_CONSUMED, LOSS |
| Não existe atualização sem registro no ledger | ✅ Saldo materializado é derivado do ledger |
| Política de divergência definida e auditável | ✅ KEEP_PENDING (default) e AUTO_LOSS, com resolução manual |
| Fluxo transacional detalhado | ✅ Dispatch e Complete com rollback em caso de erro |

---

## Etapa 5 — Robustez Operacional

### Objetivo

Eliminar bugs de dupla execução, clique duplo, retries e concorrência, garantindo que cada etapa do processo seja atômica e rastreável.

---

### 1. Atomicidade (Transações)

#### Princípio
> Cada operação de negócio que altera estado deve ser uma única transação. Se qualquer parte falhar, tudo é revertido.

#### Operações Transacionais

| Operação | Escopo da Transação |
|----------|---------------------|
| `dispatch` | Transfer + Ledger (OUT, IN_TRANSIT) + Batch + TransferInTransit |
| `startValidation` | Transfer + ValidationSession |
| `scanItem` | ValidationSession + TransferItem (receivedQuantity) |
| `completeValidation` | Transfer + Ledger (IN, TRANSIT_CONSUMED) + Batch + TransferInTransit + Discrepancies |

#### Implementação

```kotlin
@Service
class TransferService(
    private val transferRepository: TransferRepository,
    private val ledgerRepository: InventoryLedgerRepository,
    private val batchRepository: BatchRepository
) {

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    fun dispatch(transferId: UUID): TransferResponse {
        // Toda a lógica aqui está na mesma transação
        // Se qualquer exceção for lançada, tudo é revertido

        val transfer = transferRepository.findByIdForUpdate(transferId)
            ?: throw TransferNotFoundException(transferId)

        // ... validações e lógica ...

        transfer.items.forEach { item ->
            val batch = batchRepository.findByIdForUpdate(item.sourceBatchId)
            // ... ledger + batch update ...
        }

        // Se chegou aqui, commit automático
        return transfer.toResponse()
    }
}
```

#### Níveis de Isolamento

| Operação | Isolation Level | Justificativa |
|----------|-----------------|---------------|
| `dispatch` | REPEATABLE_READ | Evita leitura de saldo inconsistente durante validação |
| `completeValidation` | REPEATABLE_READ | Mesma razão |
| `scanItem` | READ_COMMITTED | Operação mais simples, menor contenção |
| Consultas | READ_COMMITTED | Default, sem locks |

---

### 2. Controle de Concorrência

#### Estratégia: Optimistic Locking + SELECT FOR UPDATE

##### Optimistic Locking (Versionamento)

```kotlin
@Entity
@Table(name = "transfer")
class Transfer(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Version
    var version: Long = 0,  // Incrementado automaticamente pelo JPA

    var status: TransferStatus,
    // ...
)
```

Comportamento:
- JPA incrementa `version` a cada UPDATE
- Se duas transações tentam atualizar a mesma versão, a segunda recebe `OptimisticLockException`

##### Pessimistic Locking (SELECT FOR UPDATE)

```kotlin
interface TransferRepository : JpaRepository<Transfer, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transfer t WHERE t.id = :id")
    fun findByIdForUpdate(id: UUID): Transfer?
}

interface BatchRepository : JpaRepository<Batch, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Batch b WHERE b.id = :id")
    fun findByIdForUpdate(id: UUID): Batch?
}
```

Uso:
- `SELECT FOR UPDATE` no Transfer e Batches envolvidos
- Garante que ninguém mais modifica enquanto a transação está em andamento

##### Ordem de Locks (Evitar Deadlock)

```kotlin
// SEMPRE adquirir locks na mesma ordem:
// 1. Transfer
// 2. Batches (ordenados por ID)
// 3. TransferInTransit

fun dispatch(transferId: UUID) {
    // 1. Lock no Transfer
    val transfer = transferRepository.findByIdForUpdate(transferId)

    // 2. Lock nos Batches (ordenados)
    val batchIds = transfer.items.map { it.sourceBatchId }.sorted()
    val batches = batchIds.map { batchRepository.findByIdForUpdate(it) }

    // 3. Processar
    // ...
}
```

#### Tratamento de Conflitos

```kotlin
@ControllerAdvice
class TransferExceptionHandler {

    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleOptimisticLock(ex: OptimisticLockingFailureException): ResponseEntity<ApiResponse<Nothing>> {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(
                code = "CONCURRENT_MODIFICATION",
                message = "Transfer was modified by another user. Please refresh and try again."
            ))
    }

    @ExceptionHandler(PessimisticLockingFailureException::class)
    fun handlePessimisticLock(ex: PessimisticLockingFailureException): ResponseEntity<ApiResponse<Nothing>> {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(
                code = "RESOURCE_LOCKED",
                message = "Transfer is being processed. Please try again in a moment."
            ))
    }
}
```

---

### 3. Idempotência

#### Princípio
> Chamar a mesma operação N vezes produz o mesmo resultado que chamar 1 vez.

#### Estratégia por Operação

##### Dispatch (Idempotência por Estado)

```kotlin
@Transactional
fun dispatch(transferId: UUID): TransferResponse {
    val transfer = transferRepository.findByIdForUpdate(transferId)

    // Idempotência: se já está despachado, retornar sucesso sem fazer nada
    if (transfer.status == TransferStatus.IN_TRANSIT) {
        log.info("Transfer $transferId already dispatched, returning existing state")
        return transfer.toResponse()
    }

    // Se está em estado inválido, erro
    if (transfer.status != TransferStatus.DRAFT) {
        throw InvalidTransferStateException(
            "Cannot dispatch transfer in status ${transfer.status}"
        )
    }

    // Processar...
    doDispatch(transfer)
    return transfer.toResponse()
}
```

##### Complete Validation (Idempotência por Estado)

```kotlin
@Transactional
fun completeValidation(transferId: UUID): TransferResponse {
    val transfer = transferRepository.findByIdForUpdate(transferId)

    // Idempotência: se já está completo, retornar sucesso
    if (transfer.status in listOf(
            TransferStatus.COMPLETED,
            TransferStatus.COMPLETED_WITH_DISCREPANCY
        )) {
        log.info("Transfer $transferId already completed, returning existing state")
        return transfer.toResponse()
    }

    // Se está em estado inválido, erro
    if (transfer.status != TransferStatus.VALIDATION_IN_PROGRESS) {
        throw InvalidTransferStateException(
            "Cannot complete validation for transfer in status ${transfer.status}"
        )
    }

    // Processar...
    doCompleteValidation(transfer)
    return transfer.toResponse()
}
```

##### Scan Item (Idempotência por Idempotency Key)

Para operações que podem ser chamadas múltiplas vezes legitimamente (scan), usar idempotency key:

```kotlin
data class ScanItemRequest(
    val barcode: String,
    val quantity: BigDecimal = BigDecimal.ONE,
    val idempotencyKey: UUID  // Gerado pelo cliente
)

@Transactional
fun scanItem(transferId: UUID, request: ScanItemRequest): ValidationItemResponse {
    // Verificar se já processamos esta key
    val existingScan = scanLogRepository.findByIdempotencyKey(request.idempotencyKey)
    if (existingScan != null) {
        log.info("Scan ${request.idempotencyKey} already processed, returning cached result")
        return existingScan.toResponse()
    }

    // Processar scan...
    val result = doScanItem(transferId, request)

    // Registrar para idempotência futura
    scanLogRepository.save(ScanLog(
        idempotencyKey = request.idempotencyKey,
        transferId = transferId,
        barcode = request.barcode,
        quantity = request.quantity,
        processedAt = Instant.now()
    ))

    return result
}
```

#### Tabela de Idempotência

```sql
CREATE TABLE scan_log (
    id UUID PRIMARY KEY,
    idempotency_key UUID UNIQUE NOT NULL,
    transfer_id UUID NOT NULL,
    barcode VARCHAR(100) NOT NULL,
    quantity DECIMAL(15,4) NOT NULL,
    processed_at TIMESTAMP NOT NULL,
    -- TTL para limpeza
    expires_at TIMESTAMP NOT NULL DEFAULT (NOW() + INTERVAL '24 hours')
);

CREATE INDEX idx_scan_log_idempotency ON scan_log(idempotency_key);
CREATE INDEX idx_scan_log_expires ON scan_log(expires_at);
```

#### Job de Limpeza

```kotlin
@Scheduled(cron = "0 0 3 * * *")  // Daily at 3 AM
fun cleanupExpiredIdempotencyKeys() {
    scanLogRepository.deleteExpired(Instant.now())
}
```

---

### 4. Auditoria Completa

#### Campos de Auditoria no Transfer

```kotlin
@Entity
class Transfer(
    // ... campos existentes ...

    // Criação
    @Column(nullable = false, updatable = false)
    val createdBy: UUID,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    // Despacho
    var dispatchedBy: UUID? = null,
    var dispatchedAt: Instant? = null,

    // Validação
    var validationStartedBy: UUID? = null,
    var validationStartedAt: Instant? = null,

    // Conclusão
    var completedBy: UUID? = null,
    var completedAt: Instant? = null,

    // Cancelamento
    var cancelledBy: UUID? = null,
    var cancelledAt: Instant? = null,
    var cancellationReason: String? = null
)
```

#### Histórico de Transições (Event Sourcing Light)

```kotlin
@Entity
@Table(name = "transfer_event")
class TransferEvent(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    val transferId: UUID,

    @Column(nullable = false)
    val tenantId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val eventType: TransferEventType,

    @Column(nullable = false)
    val fromStatus: TransferStatus?,

    @Column(nullable = false)
    val toStatus: TransferStatus,

    @Column(nullable = false)
    val performedBy: UUID,

    @Column(nullable = false)
    val performedAt: Instant = Instant.now(),

    @Column(columnDefinition = "jsonb")
    val metadata: String? = null  // Dados adicionais em JSON
)

enum class TransferEventType {
    CREATED,
    UPDATED,
    DISPATCHED,
    VALIDATION_STARTED,
    ITEM_SCANNED,
    COMPLETED,
    COMPLETED_WITH_DISCREPANCY,
    CANCELLED,
    DISCREPANCY_RESOLVED
}
```

#### Registro de Eventos

```kotlin
@Component
class TransferEventPublisher(
    private val eventRepository: TransferEventRepository
) {

    fun publish(
        transfer: Transfer,
        eventType: TransferEventType,
        fromStatus: TransferStatus?,
        metadata: Map<String, Any>? = null
    ) {
        eventRepository.save(TransferEvent(
            transferId = transfer.id,
            tenantId = transfer.tenantId,
            eventType = eventType,
            fromStatus = fromStatus,
            toStatus = transfer.status,
            performedBy = SecurityContext.currentUserId(),
            metadata = metadata?.let { objectMapper.writeValueAsString(it) }
        ))
    }
}

// Uso no service
@Transactional
fun dispatch(transferId: UUID): TransferResponse {
    val transfer = transferRepository.findByIdForUpdate(transferId)
    val previousStatus = transfer.status

    // ... lógica de despacho ...

    transfer.status = TransferStatus.IN_TRANSIT
    transfer.dispatchedBy = currentUserId()
    transfer.dispatchedAt = Instant.now()

    // Publicar evento
    eventPublisher.publish(
        transfer = transfer,
        eventType = TransferEventType.DISPATCHED,
        fromStatus = previousStatus,
        metadata = mapOf(
            "itemCount" to transfer.items.size,
            "totalQuantity" to transfer.items.sumOf { it.expectedQuantity }
        )
    )

    return transfer.toResponse()
}
```

#### Auditoria no Ledger

Cada entrada do ledger já carrega auditoria:

```kotlin
@Entity
class InventoryLedger(
    // ... campos existentes ...

    @Column(nullable = false, updatable = false)
    val createdBy: UUID,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    // Referências para rastreabilidade
    @Column(nullable = false)
    val referenceType: String,  // "TRANSFER", "SALE", etc.

    @Column(nullable = false)
    val referenceId: UUID,      // transferId, saleId, etc.

    val transferItemId: UUID? = null  // Para transfers, link direto ao item
)
```

#### Endpoint de Histórico

```
GET /stockshift/transfers/{id}/history
```

Response:
```json
{
  "events": [
    {
      "eventType": "CREATED",
      "fromStatus": null,
      "toStatus": "DRAFT",
      "performedBy": { "id": "user-1", "name": "João Silva" },
      "performedAt": "2024-01-15T09:00:00Z"
    },
    {
      "eventType": "DISPATCHED",
      "fromStatus": "DRAFT",
      "toStatus": "IN_TRANSIT",
      "performedBy": { "id": "user-1", "name": "João Silva" },
      "performedAt": "2024-01-15T10:30:00Z",
      "metadata": { "itemCount": 5, "totalQuantity": 150 }
    },
    {
      "eventType": "VALIDATION_STARTED",
      "fromStatus": "IN_TRANSIT",
      "toStatus": "VALIDATION_IN_PROGRESS",
      "performedBy": { "id": "user-2", "name": "Maria Santos" },
      "performedAt": "2024-01-16T14:00:00Z"
    }
  ]
}
```

---

### 5. Retry e Timeout

#### Timeouts de Transação

```yaml
# application.yml
spring:
  transaction:
    default-timeout: 30  # 30 segundos

  datasource:
    hikari:
      connection-timeout: 10000   # 10s para obter conexão
      max-lifetime: 1800000       # 30 min lifetime
```

#### Retry com Backoff (Cliente)

O backend não faz retry automático. O cliente (frontend) deve implementar:

```typescript
// Frontend
async function dispatchTransfer(transferId: string, retries = 3): Promise<Transfer> {
  for (let attempt = 1; attempt <= retries; attempt++) {
    try {
      return await api.post(`/transfers/${transferId}/dispatch`);
    } catch (error) {
      if (error.status === 409) {
        // Conflito - verificar estado atual
        const current = await api.get(`/transfers/${transferId}`);
        if (current.status === 'IN_TRANSIT') {
          return current; // Já despachado, sucesso
        }
        throw error; // Conflito real
      }
      if (error.status >= 500 && attempt < retries) {
        await sleep(Math.pow(2, attempt) * 1000); // Exponential backoff
        continue;
      }
      throw error;
    }
  }
}
```

---

### 6. Resumo de Garantias

| Cenário | Comportamento |
|---------|---------------|
| Dois cliques em "Despachar" | Segundo retorna sucesso sem duplicar movimentos |
| Retry após timeout | Idempotente - verifica estado antes de processar |
| Duas abas despachando | Uma ganha, outra recebe 409 Conflict |
| Scan duplicado | Idempotency key previne duplicação |
| Falha no meio do dispatch | Rollback completo, nenhum movimento criado |
| Quem fez o quê | Transfer + Events + Ledger têm createdBy/performedBy |

---

### Critérios de Conclusão

| Critério | Status |
|----------|--------|
| Duas chamadas de dispatch não duplicam movimentos | ✅ Idempotência por estado |
| Duas chamadas de complete não duplicam entrada | ✅ Idempotência por estado |
| Toda mudança de status tem auditoria | ✅ Transfer fields + TransferEvent |
| Concorrência tratada com locks | ✅ Optimistic + Pessimistic locking |
| Transações atômicas | ✅ @Transactional com isolation adequado |

---

## Etapa 6 — Recriar Banco e Validar E2E

### Objetivo

Executar a refatoração com banco limpo, garantindo que o novo modelo funcione de ponta a ponta e que todos os cenários de negócio estejam cobertos por testes.

---

### 1. Estrutura de Banco de Dados

#### Novas Tabelas

##### transfer

```sql
CREATE TABLE transfer (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    transfer_code VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',

    source_warehouse_id UUID NOT NULL REFERENCES warehouse(id),
    destination_warehouse_id UUID NOT NULL REFERENCES warehouse(id),

    notes TEXT,

    -- Auditoria
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    dispatched_by UUID REFERENCES users(id),
    dispatched_at TIMESTAMP,
    validation_started_by UUID REFERENCES users(id),
    validation_started_at TIMESTAMP,
    completed_by UUID REFERENCES users(id),
    completed_at TIMESTAMP,
    cancelled_by UUID REFERENCES users(id),
    cancelled_at TIMESTAMP,
    cancellation_reason TEXT,

    -- Concorrência
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_transfer_status CHECK (status IN (
        'DRAFT', 'IN_TRANSIT', 'VALIDATION_IN_PROGRESS',
        'COMPLETED', 'COMPLETED_WITH_DISCREPANCY', 'CANCELLED'
    )),
    CONSTRAINT chk_different_warehouses CHECK (source_warehouse_id != destination_warehouse_id),
    CONSTRAINT uq_transfer_code_tenant UNIQUE (tenant_id, transfer_code)
);

CREATE INDEX idx_transfer_tenant ON transfer(tenant_id);
CREATE INDEX idx_transfer_status ON transfer(status);
CREATE INDEX idx_transfer_source ON transfer(source_warehouse_id);
CREATE INDEX idx_transfer_destination ON transfer(destination_warehouse_id);
CREATE INDEX idx_transfer_created_at ON transfer(created_at DESC);
```

##### transfer_item

```sql
CREATE TABLE transfer_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_id UUID NOT NULL REFERENCES transfer(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenant(id),

    product_id UUID NOT NULL REFERENCES product(id),
    source_batch_id UUID NOT NULL REFERENCES batch(id),
    destination_batch_id UUID REFERENCES batch(id),

    expected_quantity DECIMAL(15,4) NOT NULL,
    received_quantity DECIMAL(15,4),

    item_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',

    CONSTRAINT chk_expected_positive CHECK (expected_quantity > 0),
    CONSTRAINT chk_received_non_negative CHECK (received_quantity IS NULL OR received_quantity >= 0),
    CONSTRAINT chk_item_status CHECK (item_status IN (
        'PENDING', 'RECEIVED', 'PARTIAL', 'EXCESS', 'MISSING'
    ))
);

CREATE INDEX idx_transfer_item_transfer ON transfer_item(transfer_id);
CREATE INDEX idx_transfer_item_product ON transfer_item(product_id);
CREATE INDEX idx_transfer_item_source_batch ON transfer_item(source_batch_id);
```

##### transfer_in_transit

```sql
CREATE TABLE transfer_in_transit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    transfer_id UUID NOT NULL REFERENCES transfer(id),
    transfer_item_id UUID NOT NULL REFERENCES transfer_item(id),

    product_id UUID NOT NULL REFERENCES product(id),
    source_batch_id UUID NOT NULL REFERENCES batch(id),

    quantity DECIMAL(15,4) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    consumed_at TIMESTAMP,

    CONSTRAINT chk_transit_quantity_non_negative CHECK (quantity >= 0)
);

CREATE INDEX idx_transit_transfer ON transfer_in_transit(transfer_id);
CREATE INDEX idx_transit_pending ON transfer_in_transit(consumed_at) WHERE consumed_at IS NULL;
```

##### inventory_ledger

```sql
CREATE TABLE inventory_ledger (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),

    warehouse_id UUID REFERENCES warehouse(id),  -- NULL para movimentos virtuais (trânsito)
    product_id UUID NOT NULL REFERENCES product(id),
    batch_id UUID REFERENCES batch(id),          -- NULL para movimentos virtuais

    entry_type VARCHAR(50) NOT NULL,
    quantity DECIMAL(15,4) NOT NULL,
    balance_after DECIMAL(15,4),                 -- NULL para movimentos virtuais

    reference_type VARCHAR(50) NOT NULL,
    reference_id UUID NOT NULL,
    transfer_item_id UUID REFERENCES transfer_item(id),

    notes TEXT,

    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_entry_type CHECK (entry_type IN (
        'PURCHASE_IN', 'SALE_OUT', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT',
        'TRANSFER_OUT', 'TRANSFER_IN_TRANSIT', 'TRANSFER_IN',
        'TRANSFER_TRANSIT_CONSUMED', 'TRANSFER_LOSS', 'RETURN_IN'
    ))
);

-- Índices para consultas comuns
CREATE INDEX idx_ledger_tenant ON inventory_ledger(tenant_id);
CREATE INDEX idx_ledger_warehouse ON inventory_ledger(warehouse_id);
CREATE INDEX idx_ledger_batch ON inventory_ledger(batch_id);
CREATE INDEX idx_ledger_reference ON inventory_ledger(reference_type, reference_id);
CREATE INDEX idx_ledger_created_at ON inventory_ledger(created_at DESC);
CREATE INDEX idx_ledger_entry_type ON inventory_ledger(entry_type);
```

##### transfer_discrepancy

```sql
CREATE TABLE transfer_discrepancy (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    transfer_id UUID NOT NULL REFERENCES transfer(id),
    transfer_item_id UUID NOT NULL REFERENCES transfer_item(id),

    discrepancy_type VARCHAR(20) NOT NULL,
    expected_quantity DECIMAL(15,4) NOT NULL,
    received_quantity DECIMAL(15,4) NOT NULL,
    difference DECIMAL(15,4) NOT NULL,

    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_RESOLUTION',
    resolution VARCHAR(30),
    resolution_notes TEXT,
    resolved_by UUID REFERENCES users(id),
    resolved_at TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_discrepancy_type CHECK (discrepancy_type IN ('SHORTAGE', 'EXCESS')),
    CONSTRAINT chk_discrepancy_status CHECK (status IN (
        'PENDING_RESOLUTION', 'RESOLVED', 'WRITTEN_OFF'
    )),
    CONSTRAINT chk_resolution CHECK (resolution IS NULL OR resolution IN (
        'WRITE_OFF', 'FOUND', 'RETURN_TRANSIT', 'ACCEPTED'
    ))
);

CREATE INDEX idx_discrepancy_transfer ON transfer_discrepancy(transfer_id);
CREATE INDEX idx_discrepancy_pending ON transfer_discrepancy(status) WHERE status = 'PENDING_RESOLUTION';
```

##### transfer_event (auditoria)

```sql
CREATE TABLE transfer_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    transfer_id UUID NOT NULL REFERENCES transfer(id),

    event_type VARCHAR(50) NOT NULL,
    from_status VARCHAR(50),
    to_status VARCHAR(50) NOT NULL,

    performed_by UUID NOT NULL REFERENCES users(id),
    performed_at TIMESTAMP NOT NULL DEFAULT NOW(),

    metadata JSONB,

    CONSTRAINT chk_event_type CHECK (event_type IN (
        'CREATED', 'UPDATED', 'DISPATCHED', 'VALIDATION_STARTED',
        'ITEM_SCANNED', 'COMPLETED', 'COMPLETED_WITH_DISCREPANCY',
        'CANCELLED', 'DISCREPANCY_RESOLVED'
    ))
);

CREATE INDEX idx_event_transfer ON transfer_event(transfer_id);
CREATE INDEX idx_event_performed_at ON transfer_event(performed_at DESC);
```

##### scan_log (idempotência)

```sql
CREATE TABLE scan_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key UUID UNIQUE NOT NULL,
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    transfer_id UUID NOT NULL REFERENCES transfer(id),

    barcode VARCHAR(100) NOT NULL,
    quantity DECIMAL(15,4) NOT NULL,

    processed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL DEFAULT (NOW() + INTERVAL '24 hours')
);

CREATE INDEX idx_scan_log_idempotency ON scan_log(idempotency_key);
CREATE INDEX idx_scan_log_expires ON scan_log(expires_at);
```

#### Alterações em Tabelas Existentes

##### batch (adicionar campos de origem)

```sql
ALTER TABLE batch ADD COLUMN origin_transfer_id UUID REFERENCES transfer(id);
ALTER TABLE batch ADD COLUMN origin_batch_id UUID REFERENCES batch(id);
```

##### Novas Permissões

```sql
INSERT INTO permission (id, name, description, category) VALUES
    (gen_random_uuid(), 'TRANSFER_CREATE', 'Create transfers', 'TRANSFER'),
    (gen_random_uuid(), 'TRANSFER_UPDATE', 'Update transfers in DRAFT', 'TRANSFER'),
    (gen_random_uuid(), 'TRANSFER_DELETE', 'Cancel transfers in DRAFT', 'TRANSFER'),
    (gen_random_uuid(), 'TRANSFER_EXECUTE', 'Dispatch transfers', 'TRANSFER'),
    (gen_random_uuid(), 'TRANSFER_VALIDATE', 'Validate/receive transfers', 'TRANSFER'),
    (gen_random_uuid(), 'TRANSFER_VIEW', 'View transfers', 'TRANSFER'),
    (gen_random_uuid(), 'TRANSFER_RESOLVE_DISCREPANCY', 'Resolve transfer discrepancies', 'TRANSFER');
```

---

### 2. Migrations

#### Ordem de Execução

```
V30__create_transfer_tables.sql
V31__create_inventory_ledger.sql
V32__create_transfer_audit_tables.sql
V33__add_batch_origin_fields.sql
V34__add_transfer_permissions.sql
V35__deprecate_stock_movement_transfer.sql  (opcional, fase 2)
```

#### V30__create_transfer_tables.sql

```sql
-- Transfer principal
CREATE TABLE transfer ( ... );
CREATE TABLE transfer_item ( ... );
CREATE TABLE transfer_in_transit ( ... );

-- Sequence para transfer_code
CREATE SEQUENCE transfer_code_seq START 1;

-- Função para gerar código
CREATE OR REPLACE FUNCTION generate_transfer_code(p_tenant_id UUID)
RETURNS VARCHAR AS $$
DECLARE
    v_year VARCHAR(4);
    v_seq INTEGER;
BEGIN
    v_year := TO_CHAR(NOW(), 'YYYY');
    v_seq := NEXTVAL('transfer_code_seq');
    RETURN 'TRF-' || v_year || '-' || LPAD(v_seq::TEXT, 5, '0');
END;
$$ LANGUAGE plpgsql;
```

#### V31__create_inventory_ledger.sql

```sql
CREATE TABLE inventory_ledger ( ... );

-- Trigger para validar ledger append-only
CREATE OR REPLACE FUNCTION prevent_ledger_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Inventory ledger entries cannot be modified or deleted';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_immutable
    BEFORE UPDATE OR DELETE ON inventory_ledger
    FOR EACH ROW EXECUTE FUNCTION prevent_ledger_modification();
```

---

### 3. Plano de Migração de Dados

#### Estratégia: Dual-Write + Shadow Read

**Fase 1: Coexistência**
- Novas transfers usam novo modelo
- Stock movements TRANSFER existentes permanecem como estão
- Endpoints antigos continuam funcionando

**Fase 2: Migração**
- Migrar stock movements TRANSFER históricos para novo modelo (script)
- Gerar ledger entries retroativamente

**Fase 3: Deprecação**
- Remover endpoints antigos
- Marcar tipo TRANSFER em StockMovement como deprecated

#### Script de Migração (Fase 2)

```sql
-- Migrar stock_movement do tipo TRANSFER para nova estrutura
-- EXECUTAR APENAS APÓS VALIDAÇÃO COMPLETA DO NOVO MODELO

INSERT INTO transfer (
    id, tenant_id, transfer_code, status,
    source_warehouse_id, destination_warehouse_id,
    created_by, created_at, dispatched_at, completed_at,
    version
)
SELECT
    sm.id,
    sm.tenant_id,
    'TRF-LEGACY-' || sm.id::TEXT,
    CASE sm.status
        WHEN 'COMPLETED' THEN 'COMPLETED'
        WHEN 'COMPLETED_WITH_DISCREPANCY' THEN 'COMPLETED_WITH_DISCREPANCY'
        WHEN 'IN_TRANSIT' THEN 'IN_TRANSIT'
        WHEN 'PENDING' THEN 'DRAFT'
        WHEN 'CANCELLED' THEN 'CANCELLED'
    END,
    sm.source_warehouse_id,
    sm.destination_warehouse_id,
    sm.user_id,
    sm.created_at,
    sm.executed_at,
    sm.completed_at,
    0
FROM stock_movement sm
WHERE sm.movement_type = 'TRANSFER'
  AND NOT EXISTS (SELECT 1 FROM transfer t WHERE t.id = sm.id);

-- Migrar items
INSERT INTO transfer_item (
    id, transfer_id, tenant_id,
    product_id, source_batch_id, destination_batch_id,
    expected_quantity, received_quantity, item_status
)
SELECT
    smi.id,
    smi.stock_movement_id,
    sm.tenant_id,
    smi.product_id,
    smi.batch_id,
    smi.destination_batch_id,
    smi.quantity,
    smi.received_quantity,
    CASE
        WHEN smi.received_quantity IS NULL THEN 'PENDING'
        WHEN smi.received_quantity = smi.quantity THEN 'RECEIVED'
        WHEN smi.received_quantity < smi.quantity THEN 'PARTIAL'
        WHEN smi.received_quantity > smi.quantity THEN 'EXCESS'
        ELSE 'PENDING'
    END
FROM stock_movement_item smi
JOIN stock_movement sm ON sm.id = smi.stock_movement_id
WHERE sm.movement_type = 'TRANSFER'
  AND NOT EXISTS (SELECT 1 FROM transfer_item ti WHERE ti.id = smi.id);
```

---

### 4. Cenários de Teste E2E

#### Cenário 1: Fluxo Perfeito (Happy Path)

```gherkin
Feature: Transfer - Fluxo Completo Sem Divergência

Scenario: Criar, despachar e receber transferência sem divergências
  Given usuário "origin_user" com permissão TRANSFER_EXECUTE no warehouse "WH_ORIGIN"
  And usuário "dest_user" com permissão TRANSFER_VALIDATE no warehouse "WH_DEST"
  And produto "PROD_A" com batch "BATCH_001" no warehouse "WH_ORIGIN" com quantidade 100

  # Criação
  When "origin_user" cria transfer de "WH_ORIGIN" para "WH_DEST" com:
    | product  | batch     | quantity |
    | PROD_A   | BATCH_001 | 50       |
  Then transfer está em status "DRAFT"
  And saldo de "BATCH_001" em "WH_ORIGIN" é 100

  # Despacho
  When "origin_user" despacha a transfer
  Then transfer está em status "IN_TRANSIT"
  And saldo de "BATCH_001" em "WH_ORIGIN" é 50
  And existe TransferInTransit com quantity 50
  And ledger contém TRANSFER_OUT com quantity 50
  And ledger contém TRANSFER_IN_TRANSIT com quantity 50

  # Validação
  When "dest_user" inicia validação
  Then transfer está em status "VALIDATION_IN_PROGRESS"

  When "dest_user" escaneia barcode de "PROD_A" com quantidade 50
  Then item tem receivedQuantity 50

  When "dest_user" completa validação
  Then transfer está em status "COMPLETED"
  And existe batch "BATCH_001-WHDEST" em "WH_DEST" com quantidade 50
  And TransferInTransit foi consumido (quantity = 0)
  And ledger contém TRANSFER_IN com quantity 50
  And ledger contém TRANSFER_TRANSIT_CONSUMED com quantity 50
```

#### Cenário 2: Fluxo com Divergência (Falta)

```gherkin
Scenario: Receber quantidade menor que esperada
  Given transfer despachada com 50 unidades de "PROD_A"

  When "dest_user" inicia validação
  And "dest_user" escaneia barcode de "PROD_A" com quantidade 40
  And "dest_user" completa validação

  Then transfer está em status "COMPLETED_WITH_DISCREPANCY"
  And existe batch em "WH_DEST" com quantidade 40
  And TransferInTransit tem quantity residual 10
  And existe TransferDiscrepancy com:
    | type     | expected | received | difference |
    | SHORTAGE | 50       | 40       | 10         |
  And discrepancy tem status "PENDING_RESOLUTION"
```

#### Cenário 3: Permissões por Papel

```gherkin
Scenario: Origem não pode validar, destino não pode despachar
  Given transfer em status "DRAFT"

  When "dest_user" tenta despachar a transfer
  Then recebe erro 403 "User cannot dispatch this transfer"

  Given transfer em status "IN_TRANSIT"

  When "origin_user" tenta iniciar validação
  Then recebe erro 403 "User cannot start validation for this transfer"
```

#### Cenário 4: Idempotência

```gherkin
Scenario: Múltiplas chamadas de dispatch não duplicam movimentos
  Given transfer em status "DRAFT"

  When "origin_user" despacha a transfer
  And "origin_user" despacha a transfer novamente (retry)

  Then transfer está em status "IN_TRANSIT"
  And ledger contém exatamente 1 TRANSFER_OUT
  And ledger contém exatamente 1 TRANSFER_IN_TRANSIT
```

#### Cenário 5: Concorrência

```gherkin
Scenario: Duas sessões tentando despachar simultaneamente
  Given transfer em status "DRAFT"

  When "user_1" e "user_2" tentam despachar simultaneamente
  Then um recebe sucesso
  And outro recebe erro 409 "CONCURRENT_MODIFICATION"
  And ledger contém exatamente 1 TRANSFER_OUT
```

#### Cenário 6: Cancelamento

```gherkin
Scenario: Cancelar transfer em DRAFT
  Given transfer em status "DRAFT"

  When "origin_user" cancela a transfer
  Then transfer está em status "CANCELLED"
  And saldo de batch não foi alterado
  And não existe entrada no ledger

Scenario: Não pode cancelar transfer despachada
  Given transfer em status "IN_TRANSIT"

  When "origin_user" tenta cancelar a transfer
  Then recebe erro 400 "Cannot cancel transfer in status IN_TRANSIT"
```

#### Cenário 7: Estoque Insuficiente

```gherkin
Scenario: Não pode despachar sem estoque suficiente
  Given produto "PROD_A" com batch "BATCH_001" com quantidade 30
  And transfer com expectedQuantity 50

  When "origin_user" tenta despachar
  Then recebe erro 400 "Insufficient stock in batch BATCH_001"
  And saldo permanece 30
  And transfer permanece em "DRAFT"
```

#### Cenário 8: Resolução de Discrepância

```gherkin
Scenario: Resolver discrepância como perda
  Given transfer completada com discrepancy SHORTAGE de 10 unidades

  When "manager" resolve discrepancy como "WRITE_OFF" com justificativa "Dano no transporte"

  Then discrepancy tem status "RESOLVED"
  And discrepancy tem resolution "WRITE_OFF"
  And TransferInTransit foi zerado
  And ledger contém TRANSFER_LOSS com quantity 10
```

---

### 5. Testes de Integração

#### Estrutura de Testes

```kotlin
@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
class TransferIntegrationTest {

    @Container
    companion object {
        val postgres = PostgreSQLContainer("postgres:15")
            .withDatabaseName("stockshift_test")
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var transferRepository: TransferRepository

    @Autowired
    lateinit var ledgerRepository: InventoryLedgerRepository

    @Test
    fun `should create and dispatch transfer successfully`() {
        // Arrange
        val warehouse1 = createWarehouse("WH1")
        val warehouse2 = createWarehouse("WH2")
        val product = createProduct("PROD_A")
        val batch = createBatch(product, warehouse1, quantity = 100)
        val user = createUserWithPermissions(warehouse1, TRANSFER_EXECUTE)

        // Act - Create
        val createResponse = mockMvc.perform(
            post("/stockshift/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "sourceWarehouseId": "${warehouse1.id}",
                        "destinationWarehouseId": "${warehouse2.id}",
                        "items": [
                            {
                                "productId": "${product.id}",
                                "batchId": "${batch.id}",
                                "quantity": 50
                            }
                        ]
                    }
                """)
                .with(user(user))
        )
            .andExpect(status().isCreated)
            .andReturn()

        val transfer = parseResponse<TransferResponse>(createResponse)
        assertThat(transfer.status).isEqualTo(TransferStatus.DRAFT)

        // Act - Dispatch
        mockMvc.perform(
            post("/stockshift/transfers/${transfer.id}/dispatch")
                .with(user(user))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("IN_TRANSIT"))

        // Assert
        val updatedBatch = batchRepository.findById(batch.id).get()
        assertThat(updatedBatch.quantity).isEqualTo(BigDecimal(50))

        val ledgerEntries = ledgerRepository.findByReferenceId(transfer.id)
        assertThat(ledgerEntries).hasSize(2)
        assertThat(ledgerEntries.map { it.entryType })
            .containsExactlyInAnyOrder(
                LedgerEntryType.TRANSFER_OUT,
                LedgerEntryType.TRANSFER_IN_TRANSIT
            )
    }

    @Test
    fun `should be idempotent on dispatch`() {
        // Arrange
        val transfer = createTransferInDraft()

        // Act - Dispatch twice
        repeat(2) {
            mockMvc.perform(
                post("/stockshift/transfers/${transfer.id}/dispatch")
                    .with(user(originUser))
            )
                .andExpect(status().isOk)
        }

        // Assert - Only one ledger entry
        val ledgerEntries = ledgerRepository.findByReferenceId(transfer.id)
        assertThat(ledgerEntries.count { it.entryType == LedgerEntryType.TRANSFER_OUT })
            .isEqualTo(1)
    }

    @Test
    fun `should reject dispatch from destination user`() {
        // Arrange
        val transfer = createTransferInDraft()

        // Act
        mockMvc.perform(
            post("/stockshift/transfers/${transfer.id}/dispatch")
                .with(user(destinationUser))
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
    }
}
```

---

### 6. Checklist de Validação

#### Pré-Deploy

- [ ] Migrations executam sem erro em banco limpo
- [ ] Migrations executam sem erro em banco com dados existentes
- [ ] Índices criados corretamente
- [ ] Constraints funcionando (ex: source != destination)
- [ ] Permissões inseridas

#### Funcional

- [ ] Criar transfer em DRAFT
- [ ] Editar transfer em DRAFT
- [ ] Cancelar transfer em DRAFT
- [ ] Despachar transfer (DRAFT → IN_TRANSIT)
- [ ] Iniciar validação (IN_TRANSIT → VALIDATION_IN_PROGRESS)
- [ ] Escanear itens
- [ ] Completar validação sem divergência (→ COMPLETED)
- [ ] Completar validação com divergência (→ COMPLETED_WITH_DISCREPANCY)
- [ ] Resolver discrepância

#### Segurança

- [ ] Origem não pode validar
- [ ] Destino não pode despachar
- [ ] Usuário sem permissão recebe 403
- [ ] Usuário de outro tenant recebe 404

#### Robustez

- [ ] Dispatch idempotente
- [ ] Complete validation idempotente
- [ ] Scan com idempotency key funciona
- [ ] Concorrência tratada (409 Conflict)
- [ ] Transação com rollback em caso de erro

#### Auditoria

- [ ] Transfer tem campos de auditoria preenchidos
- [ ] TransferEvent registrado para cada transição
- [ ] Ledger entries têm createdBy/createdAt
- [ ] Histórico acessível via endpoint

#### Performance

- [ ] Listagem paginada funciona
- [ ] Índices utilizados nas queries principais
- [ ] Nenhum N+1 query

---

### 7. Rollback Plan

Caso seja necessário reverter:

```sql
-- 1. Parar aplicação

-- 2. Verificar se há transfers no novo modelo em andamento
SELECT status, COUNT(*) FROM transfer
WHERE status NOT IN ('COMPLETED', 'COMPLETED_WITH_DISCREPANCY', 'CANCELLED')
GROUP BY status;

-- 3. Se houver transfers IN_TRANSIT, resolver manualmente antes de rollback

-- 4. Manter tabelas novas, apenas desativar endpoints
-- (não deletar dados, podem ser necessários para auditoria)

-- 5. Reativar endpoints antigos de stock_movement
```

---

### Critérios de Conclusão

| Critério | Status |
|----------|--------|
| Estrutura de banco definida | ✅ 7 tabelas novas + alterações |
| Migrations documentadas | ✅ Ordem e scripts SQL |
| Plano de migração de dados | ✅ Dual-write + script de migração |
| Cenários E2E definidos | ✅ 8 cenários principais |
| Testes de integração | ✅ Estrutura e exemplos |
| Checklist de validação | ✅ 20+ itens |
| Rollback plan | ✅ Documentado |
