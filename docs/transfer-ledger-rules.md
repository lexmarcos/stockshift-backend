# Transfer Ledger Rules (Phase 4)

## Overview

The transfer system uses an append-only ledger to track all inventory movements. This ensures complete auditability and allows reconciliation of batch quantities.

## Ledger Entry Types for Transfers

| Type | When Created | Warehouse | Effect |
|------|--------------|-----------|--------|
| `TRANSFER_OUT` | Dispatch | Source | Debit (reduces stock) |
| `TRANSFER_IN_TRANSIT` | Dispatch | Virtual (null) | Credit (virtual holding) |
| `TRANSFER_IN` | Validation Complete | Destination | Credit (adds stock) |
| `TRANSFER_TRANSIT_CONSUMED` | Validation Complete | Virtual (null) | Debit (removes from transit) |
| `TRANSFER_LOSS` | Discrepancy Resolution | Virtual (null) | Debit (write-off shortage) |

## Discrepancy Handling

### Types
- **SHORTAGE**: Received less than expected
- **EXCESS**: Received more than expected

### Statuses
- **PENDING_RESOLUTION**: Awaiting action
- **RESOLVED**: Action taken (FOUND, ACCEPTED, RETURN_TRANSIT)
- **WRITTEN_OFF**: Written off as loss (WRITE_OFF)

### Resolution Options
- **WRITE_OFF**: Creates TRANSFER_LOSS ledger entry, zeros transit
- **FOUND**: Marks resolved, manual adjustment may follow
- **ACCEPTED**: For excess, marks resolved with audit flag
- **RETURN_TRANSIT**: Future: creates reverse transfer

## Reconciliation

Daily job at 2 AM compares:
- Batch materialized quantity (stored in `batches.quantity`)
- Calculated quantity (sum of ledger entries)

Discrepancies trigger critical alerts for investigation.

## API Endpoints

```
GET  /stockshift/transfers/{id}/discrepancies
POST /stockshift/transfers/discrepancies/{id}/resolve
GET  /stockshift/transfers/{id}/ledger
```
