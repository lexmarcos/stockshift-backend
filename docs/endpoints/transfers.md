# Transfer Endpoints

## Overview

The Transfer module manages inter-warehouse inventory movements with a complete workflow including dispatch, validation, and discrepancy resolution. Transfers are tracked through an inventory ledger and support barcode scanning for receiving validation.

**Base URL**: `/stockshift/transfers`
**Authentication**: Required (Bearer token)

---

## Transfer Status Flow

```
DRAFT → IN_TRANSIT → VALIDATION_IN_PROGRESS → COMPLETED
   ↓                                              ↓
CANCELLED                          COMPLETED_WITH_DISCREPANCY
```

### Status Definitions

| Status | Description |
|--------|-------------|
| `DRAFT` | Transfer created, items can be edited. Stock not yet affected. |
| `IN_TRANSIT` | Dispatched from source. Stock deducted and moved to virtual transit. |
| `VALIDATION_IN_PROGRESS` | Destination warehouse started receiving validation. |
| `COMPLETED` | All items received matching expected quantities. |
| `COMPLETED_WITH_DISCREPANCY` | Validation complete but with missing or excess items. |
| `CANCELLED` | Transfer aborted. If dispatched, stock is returned to source. |

---

## Role-Based Access

Transfers enforce strict role separation between source and destination warehouses:

| Role | Warehouse | Allowed Actions |
|------|-----------|-----------------|
| `OUTBOUND` | Source | `UPDATE`, `DISPATCH`, `CANCEL` |
| `INBOUND` | Destination | `START_VALIDATION`, `SCAN_ITEM`, `COMPLETE` |

The `direction` field in responses indicates the user's role for that transfer.

---

## Endpoints

### POST /stockshift/transfers
**Summary**: Create a new transfer in DRAFT status

**Authorization**: `TRANSFER:CREATE`

#### Request Body
```json
{
  "sourceWarehouseId": "550e8400-e29b-41d4-a716-446655440001",
  "destinationWarehouseId": "550e8400-e29b-41d4-a716-446655440002",
  "items": [
    {
      "productId": "660e8400-e29b-41d4-a716-446655440001",
      "batchId": "770e8400-e29b-41d4-a716-446655440002",
      "quantity": 10.5
    }
  ],
  "notes": "Weekly restock for Store B"
}
```

**Field Details**:
- `sourceWarehouseId`: Required, UUID of origin warehouse
- `destinationWarehouseId`: Required, UUID of destination warehouse (must differ from source)
- `items`: Required, array with at least one item
  - `productId`: Required, UUID of product
  - `batchId`: Required, UUID of specific batch to transfer
  - `quantity`: Required, positive decimal (supports 3 decimal places)
- `notes`: Optional, transfer description

#### Response
**Status Code**: `201 CREATED`

```json
{
  "id": "880e8400-e29b-41d4-a716-446655440003",
  "transferCode": "TRF-2026-00001",
  "status": "DRAFT",
  "sourceWarehouse": {
    "id": "550e8400-e29b-41d4-a716-446655440001",
    "name": "Main Warehouse",
    "code": "WH-001"
  },
  "destinationWarehouse": {
    "id": "550e8400-e29b-41d4-a716-446655440002",
    "name": "Store B",
    "code": "WH-002"
  },
  "items": [
    {
      "id": "aa0e8400-e29b-41d4-a716-446655440005",
      "productId": "660e8400-e29b-41d4-a716-446655440001",
      "productName": "Product Name",
      "productSku": "PROD-001",
      "barcode": "7891234567890",
      "batchId": "770e8400-e29b-41d4-a716-446655440002",
      "batchNumber": "BATCH-2025-001",
      "expectedQuantity": 10.5,
      "receivedQuantity": null
    }
  ],
  "direction": "OUTBOUND",
  "allowedActions": ["UPDATE", "DISPATCH", "CANCEL"],
  "totalItems": 1,
  "totalExpectedQuantity": 10.5,
  "totalReceivedQuantity": null,
  "itemsValidated": 0,
  "hasDiscrepancy": false,
  "createdBy": {
    "id": "990e8400-e29b-41d4-a716-446655440004",
    "name": "John Doe"
  },
  "createdAt": "2026-01-30T10:00:00Z",
  "dispatchedBy": null,
  "dispatchedAt": null,
  "validationStartedBy": null,
  "validationStartedAt": null,
  "completedBy": null,
  "completedAt": null,
  "cancelledBy": null,
  "cancelledAt": null,
  "cancellationReason": null,
  "notes": "Weekly restock for Store B"
}
```

---

### GET /stockshift/transfers/{id}
**Summary**: Get transfer details

**Authorization**: `TRANSFER:READ`

**URL Parameters**: `id` (UUID) - Transfer identifier

**Response**: Same structure as POST response

---

### PUT /stockshift/transfers/{id}
**Summary**: Update a DRAFT transfer

**Authorization**: `TRANSFER:UPDATE`

**Note**: Only transfers in DRAFT status can be updated. Only users with OUTBOUND role can update.

#### Request Body
```json
{
  "items": [
    {
      "productId": "660e8400-e29b-41d4-a716-446655440001",
      "batchId": "770e8400-e29b-41d4-a716-446655440002",
      "quantity": 15.0
    }
  ],
  "notes": "Updated quantity"
}
```

**Response**: Updated TransferResponse

---

### DELETE /stockshift/transfers/{id}
**Summary**: Cancel a transfer

**Authorization**: `TRANSFER:DELETE`

**Query Parameters**:
- `reason` (optional): Cancellation reason

**Notes**:
- DRAFT transfers: Simply cancelled, no stock impact
- IN_TRANSIT transfers: Stock returned to source warehouse

**Response**: `204 No Content`

---

### POST /stockshift/transfers/{id}/dispatch
**Summary**: Dispatch transfer from source warehouse

**Authorization**: `TRANSFER:EXECUTE`

**Notes**:
- Transitions status from DRAFT to IN_TRANSIT
- Deducts stock from source warehouse batches
- Creates inventory ledger entries (TRANSFER_OUT)
- Moves stock to virtual transit ledger (TRANSFER_IN_TRANSIT)
- Idempotent: calling on already-dispatched transfer returns current state

**Response**: TransferResponse with status `IN_TRANSIT`

---

### POST /stockshift/transfers/{id}/validation/start
**Summary**: Start validation at destination warehouse

**Authorization**: `TRANSFER:VALIDATE`

**Notes**:
- Transitions status from IN_TRANSIT to VALIDATION_IN_PROGRESS
- Only users with INBOUND role (destination warehouse access) can start
- Idempotent: calling on already-started validation returns current state

#### Response
**Status Code**: `200 OK`

```json
{
  "transferId": "880e8400-e29b-41d4-a716-446655440003",
  "status": "VALIDATION_IN_PROGRESS",
  "validationStartedAt": "2026-01-30T14:00:00Z",
  "validationStartedBy": {
    "id": "990e8400-e29b-41d4-a716-446655440005",
    "name": "Jane Smith"
  },
  "items": [
    {
      "id": "aa0e8400-e29b-41d4-a716-446655440005",
      "productId": "660e8400-e29b-41d4-a716-446655440001",
      "productName": "Product Name",
      "barcode": "7891234567890",
      "expectedQuantity": 10.5,
      "receivedQuantity": 0
    }
  ],
  "totalItems": 1,
  "itemsScanned": 0,
  "itemsPending": 1,
  "hasDiscrepancy": false,
  "canComplete": false,
  "allowedActions": ["SCAN_ITEM", "COMPLETE"]
}
```

---

### POST /stockshift/transfers/{id}/validation/scan
**Summary**: Scan a product barcode during validation

**Authorization**: `TRANSFER:VALIDATE`

#### Request Body
```json
{
  "barcode": "7891234567890",
  "quantity": 5.0,
  "idempotencyKey": "scan-001-uuid"
}
```

**Field Details**:
- `barcode`: Required, product barcode to scan
- `quantity`: Required, positive decimal quantity being received
- `idempotencyKey`: Optional UUID, prevents duplicate scans with same key

**Notes**:
- Increments `receivedQuantity` for the matching item
- Returns error if barcode not found in transfer items
- Idempotency key prevents accidental double-counting

#### Response
**Status Code**: `200 OK`

Returns TransferValidationResponse with updated quantities.

---

### POST /stockshift/transfers/{id}/validation/complete
**Summary**: Complete validation and finalize transfer

**Authorization**: `TRANSFER:VALIDATE`

**Notes**:
- Compares received vs expected quantities for each item
- Creates inventory ledger entries (TRANSFER_IN) for received quantities
- Sets status to COMPLETED or COMPLETED_WITH_DISCREPANCY
- Creates discrepancy records for any differences
- Creates mirror batches in destination warehouse if needed

#### Response
**Status Code**: `200 OK`

```json
{
  "id": "880e8400-e29b-41d4-a716-446655440003",
  "transferCode": "TRF-2026-00001",
  "status": "COMPLETED_WITH_DISCREPANCY",
  "totalExpectedQuantity": 10.5,
  "totalReceivedQuantity": 8.0,
  "hasDiscrepancy": true,
  "completedBy": {
    "id": "990e8400-e29b-41d4-a716-446655440005",
    "name": "Jane Smith"
  },
  "completedAt": "2026-01-30T15:30:00Z"
}
```

---

### GET /stockshift/transfers
**Summary**: List transfers with filtering and pagination

**Authorization**: `TRANSFER:READ`

**Query Parameters**:
- `warehouseId` (optional): Filter by warehouse (source or destination)
- `status` (optional): Filter by status
- `direction` (optional): Filter by role (`OUTBOUND` or `INBOUND`)
- `page` (default: 0): Page number
- `size` (default: 20): Page size
- `sort` (default: `createdAt,desc`): Sort field and direction

#### Response
**Status Code**: `200 OK`

```json
{
  "content": [
    {
      "id": "880e8400-e29b-41d4-a716-446655440003",
      "transferCode": "TRF-2026-00001",
      "status": "IN_TRANSIT",
      "sourceWarehouse": { "id": "...", "name": "Main Warehouse", "code": "WH-001" },
      "destinationWarehouse": { "id": "...", "name": "Store B", "code": "WH-002" },
      "direction": "OUTBOUND",
      "allowedActions": ["CANCEL"],
      "totalItems": 3,
      "totalExpectedQuantity": 50.0,
      "createdAt": "2026-01-30T10:00:00Z",
      "dispatchedAt": "2026-01-30T11:00:00Z"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 20,
  "number": 0
}
```

---

### GET /stockshift/transfers/{id}/discrepancies
**Summary**: List discrepancies for a transfer

**Authorization**: `TRANSFER:READ`

#### Response
**Status Code**: `200 OK`

```json
[
  {
    "id": "dd0e8400-e29b-41d4-a716-446655440010",
    "transferId": "880e8400-e29b-41d4-a716-446655440003",
    "transferItemId": "aa0e8400-e29b-41d4-a716-446655440005",
    "discrepancyType": "SHORTAGE",
    "expectedQuantity": 10.5,
    "receivedQuantity": 8.0,
    "difference": 2.5,
    "status": "PENDING",
    "resolution": null,
    "resolutionNotes": null,
    "resolvedBy": null,
    "resolvedAt": null,
    "createdAt": "2026-01-30T15:30:00Z"
  }
]
```

**Discrepancy Types**:
- `SHORTAGE`: Received less than expected
- `EXCESS`: Received more than expected

---

### POST /stockshift/discrepancies/{discrepancyId}/resolve
**Summary**: Resolve a discrepancy

**Authorization**: `TRANSFER:VALIDATE`

**Query Parameters**:
- `resolution`: Required, one of `ACCEPT`, `WRITE_OFF`, `RETURN_TO_SENDER`
- `notes` (optional): Resolution notes

**Resolution Types**:
- `ACCEPT`: Accept the discrepancy as-is (for excess)
- `WRITE_OFF`: Write off missing quantity as loss (creates ledger entry)
- `RETURN_TO_SENDER`: Mark for return to source warehouse

#### Response
**Status Code**: `200 OK`

Returns updated DiscrepancyResponse with resolution details.

---

### GET /stockshift/transfers/{id}/ledger
**Summary**: Get inventory ledger entries for a transfer

**Authorization**: `TRANSFER:READ`

#### Response
**Status Code**: `200 OK`

```json
[
  {
    "id": "ll0e8400-e29b-41d4-a716-446655440020",
    "warehouseId": "550e8400-e29b-41d4-a716-446655440001",
    "productId": "660e8400-e29b-41d4-a716-446655440001",
    "batchId": "770e8400-e29b-41d4-a716-446655440002",
    "entryType": "TRANSFER_OUT",
    "isDebit": true,
    "quantity": 10.5,
    "balanceAfter": 89.5,
    "referenceType": "TRANSFER",
    "referenceId": "880e8400-e29b-41d4-a716-446655440003",
    "transferItemId": "aa0e8400-e29b-41d4-a716-446655440005",
    "notes": null,
    "createdBy": "990e8400-e29b-41d4-a716-446655440004",
    "createdAt": "2026-01-30T11:00:00Z"
  }
]
```

**Entry Types**:
- `TRANSFER_OUT`: Stock deducted from source warehouse
- `TRANSFER_IN_TRANSIT`: Stock in virtual transit
- `TRANSFER_IN`: Stock received at destination warehouse
- `TRANSFER_LOSS`: Write-off for discrepancy resolution

---

### GET /stockshift/transfers/{id}/history
**Summary**: Get audit trail and timeline for a transfer

**Authorization**: `TRANSFER:READ`

#### Response
**Status Code**: `200 OK`

```json
{
  "success": true,
  "data": {
    "transferId": "880e8400-e29b-41d4-a716-446655440003",
    "transferCode": "TRF-2026-00001",
    "events": [
      {
        "id": "ee0e8400-e29b-41d4-a716-446655440030",
        "eventType": "CREATED",
        "timestamp": "2026-01-30T10:00:00Z",
        "userId": "990e8400-e29b-41d4-a716-446655440004",
        "userName": "John Doe",
        "metadata": null
      },
      {
        "id": "ee0e8400-e29b-41d4-a716-446655440031",
        "eventType": "DISPATCHED",
        "timestamp": "2026-01-30T11:00:00Z",
        "userId": "990e8400-e29b-41d4-a716-446655440004",
        "userName": "John Doe",
        "metadata": null
      }
    ]
  }
}
```

**Event Types**:
- `CREATED`: Transfer created
- `UPDATED`: Transfer items or notes updated
- `DISPATCHED`: Transfer dispatched from source
- `VALIDATION_STARTED`: Validation began at destination
- `ITEM_SCANNED`: Item scanned during validation
- `COMPLETED`: Transfer completed successfully
- `COMPLETED_WITH_DISCREPANCY`: Transfer completed with differences
- `CANCELLED`: Transfer cancelled

---

## Frontend Implementation Guide

### Transfer Creation Flow
1. **Warehouse Selection**: Select source and destination warehouses
2. **Product/Batch Selection**: Search products with available batches at source
3. **Quantity Entry**: Enter quantities (supports decimals)
4. **Review**: Show summary before creation
5. **Actions**: Save as draft or dispatch immediately

### Dispatch Flow (OUTBOUND Role)
1. **Review Items**: Show all items with quantities
2. **Stock Validation**: Verify sufficient stock in batches
3. **Confirmation**: Require explicit confirmation before dispatch
4. **Feedback**: Show success with updated status

### Validation Flow (INBOUND Role)
1. **Start Validation**: Prominent button for IN_TRANSIT transfers
2. **Scanner Interface**:
   - Large barcode input field (auto-focus)
   - Support for physical barcode scanners
   - Manual quantity entry option
3. **Progress Display**:
   - Items scanned vs pending
   - Real-time quantity updates
   - Discrepancy warnings
4. **Complete Validation**:
   - Summary before completion
   - Warn if discrepancies exist
   - Require confirmation

### Discrepancy Resolution
1. **List View**: Show all pending discrepancies
2. **Resolution Options**: Clear buttons for each resolution type
3. **Notes**: Optional notes field for context
4. **Audit Trail**: Show who resolved and when

### Mobile Considerations
1. **Large Touch Targets**: Big buttons for warehouse environment
2. **Scanner Support**: Native barcode scanner integration
3. **Offline Mode**: Queue scans for sync when online
4. **Audio Feedback**: Sound for successful/failed scans

---

## Error Responses

### 400 Bad Request - Same Warehouse
```json
{
  "success": false,
  "message": "Source and destination warehouses must be different"
}
```

### 400 Bad Request - Insufficient Stock
```json
{
  "success": false,
  "message": "Insufficient stock in batch",
  "data": {
    "batchId": "770e8400-e29b-41d4-a716-446655440002",
    "available": 5.0,
    "requested": 10.5
  }
}
```

### 400 Bad Request - Invalid Status Transition
```json
{
  "success": false,
  "message": "Cannot dispatch transfer in status CANCELLED"
}
```

### 403 Forbidden - Wrong Role
```json
{
  "success": false,
  "message": "User with role INBOUND cannot perform DISPATCH on this transfer"
}
```

### 404 Not Found - Barcode Not in Transfer
```json
{
  "success": false,
  "message": "Product with barcode 7891234567890 not found in this transfer"
}
```

### 409 Conflict - Idempotency Key Used
```json
{
  "success": false,
  "message": "Scan with idempotency key already processed",
  "data": {
    "idempotencyKey": "scan-001-uuid",
    "processedAt": "2026-01-30T14:05:00Z"
  }
}
```

---

## Integration Points

### Inventory Ledger
- All stock movements create ledger entries
- Supports reconciliation and audit trails
- Balance tracking per batch/warehouse

### Batch Management
- Mirror batches created at destination with origin reference
- Batch codes include warehouse suffix (e.g., `BATCH01-WH2`)
- Traceability back to original batch

### Reports
- Transfer history reports
- Discrepancy analysis
- Ledger reconciliation

---

## Migration from Stock Movements

The Transfer module replaces `TRANSFER` type stock movements. Key differences:

| Aspect | Old (Stock Movements) | New (Transfers) |
|--------|----------------------|-----------------|
| Workflow | Single execute action | Multi-step: dispatch, validate, complete |
| Validation | None | Barcode scanning with quantity tracking |
| Discrepancies | Not tracked | First-class entities with resolution |
| Accounting | Direct batch updates | Inventory ledger with audit trail |
| Roles | Single permission | Separate OUTBOUND/INBOUND roles |
| Transit | Instant transfer | Virtual transit tracking |

For `ENTRY`, `EXIT`, and `ADJUSTMENT` movements, continue using the [Stock Movements API](./stock-movements.md).
