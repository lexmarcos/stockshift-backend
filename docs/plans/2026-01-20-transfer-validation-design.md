# Design: Validação de Transferências entre Warehouses

## Resumo

Feature para permitir que o warehouse destino valide os produtos recebidos em uma transferência via escaneamento de código de barras, gerando relatórios de discrepância para produtos faltantes.

## Fluxo de Transferência

### Novos Status

```
PENDING → IN_TRANSIT → COMPLETED (ou COMPLETED_WITH_DISCREPANCY)
```

| Status | Descrição |
|--------|-----------|
| `PENDING` | Rascunho, ainda não executado |
| `IN_TRANSIT` | Warehouse A executou, produtos saíram. Aguardando validação do Warehouse B |
| `COMPLETED` | Validação feita, todos os produtos conferem |
| `COMPLETED_WITH_DISCREPANCY` | Validação feita, mas com produtos faltantes |

### Fluxo Completo

1. Warehouse A cria transferência → `PENDING`
2. Warehouse A executa → estoque sai de A, status vira `IN_TRANSIT`
3. Warehouse B inicia validação e escaneia produtos um a um
4. Warehouse B finaliza validação → estoque entra em B, status final definido

### Regras de Negócio

- **Validação parcial automática**: Produtos escaneados entram no estoque de B; faltantes geram discrepância
- **Produtos não listados**: Rejeitar com erro imediato, não adiciona ao estoque
- **Escaneamento**: Um por um, cada scan incrementa +1 na quantidade recebida
- **Permissões**: Qualquer usuário associado ao warehouse destino pode validar
- **Prazo**: Sem prazo para MVP, sem alertas automáticos

---

## Modelo de Dados

### Nova tabela: `transfer_validations`

| Coluna | Tipo | Descrição |
|--------|------|-----------|
| `id` | UUID | PK |
| `stock_movement_id` | UUID | FK para stock_movements |
| `validated_by` | UUID | FK para users (quem validou) |
| `started_at` | timestamp | Quando começou a escanear |
| `completed_at` | timestamp | Quando finalizou (nullable) |
| `status` | enum | `IN_PROGRESS`, `COMPLETED` |
| `notes` | text | Observações opcionais |

### Nova tabela: `transfer_validation_items`

| Coluna | Tipo | Descrição |
|--------|------|-----------|
| `id` | UUID | PK |
| `transfer_validation_id` | UUID | FK para transfer_validations |
| `stock_movement_item_id` | UUID | FK para stock_movement_items |
| `expected_quantity` | integer | Quantidade esperada |
| `received_quantity` | integer | Quantidade escaneada |
| `scanned_at` | timestamp | Último scan deste item |

### Nova tabela: `transfer_discrepancies`

| Coluna | Tipo | Descrição |
|--------|------|-----------|
| `id` | UUID | PK |
| `transfer_validation_id` | UUID | FK para transfer_validations |
| `stock_movement_item_id` | UUID | FK para stock_movement_items |
| `expected_quantity` | integer | Quantidade esperada |
| `received_quantity` | integer | Quantidade recebida |
| `missing_quantity` | integer | Quantidade faltante (expected - received) |
| `created_at` | timestamp | Data de criação |

### Alteração em `stock_movements`

Adicionar ao enum `MovementStatus`:
- `IN_TRANSIT`
- `COMPLETED_WITH_DISCREPANCY`

---

## Endpoints da API

### Iniciar validação

```
POST /api/stock-movements/{id}/validations
```

**Regras:**
- Só permite se status = `IN_TRANSIT`
- Só permite se usuário pertence ao warehouse destino
- Não permite se já existe validação `IN_PROGRESS` ou `COMPLETED`

**Response:**
```json
{
  "validationId": "uuid",
  "items": [
    {
      "itemId": "uuid",
      "productName": "Produto X",
      "barcode": "7891234567890",
      "expectedQuantity": 10,
      "scannedQuantity": 0
    }
  ],
  "startedAt": "2026-01-20T10:00:00Z"
}
```

### Escanear produto

```
POST /api/stock-movements/{id}/validations/{validationId}/scan
```

**Request:**
```json
{
  "barcode": "7891234567890"
}
```

**Response (sucesso):**
```json
{
  "success": true,
  "item": {
    "productName": "Produto X",
    "expectedQuantity": 10,
    "scannedQuantity": 5
  },
  "message": "Produto escaneado com sucesso"
}
```

**Response (produto não listado):**
```json
{
  "success": false,
  "message": "Produto não faz parte desta transferência",
  "barcode": "7891234567890"
}
```

### Consultar progresso

```
GET /api/stock-movements/{id}/validations/{validationId}
```

**Response:**
```json
{
  "validationId": "uuid",
  "status": "IN_PROGRESS",
  "startedAt": "2026-01-20T10:00:00Z",
  "items": [
    {
      "productName": "Produto X",
      "barcode": "7891234567890",
      "expectedQuantity": 10,
      "scannedQuantity": 10,
      "status": "COMPLETE"
    },
    {
      "productName": "Produto Y",
      "barcode": "7891234567891",
      "expectedQuantity": 5,
      "scannedQuantity": 2,
      "status": "PARTIAL"
    }
  ],
  "progress": {
    "totalItems": 4,
    "completeItems": 1,
    "partialItems": 1,
    "pendingItems": 2
  }
}
```

### Finalizar validação

```
POST /api/stock-movements/{id}/validations/{validationId}/complete
```

**Response:**
```json
{
  "status": "COMPLETED_WITH_DISCREPANCY",
  "completedAt": "2026-01-20T11:30:00Z",
  "summary": {
    "totalExpected": 20,
    "totalReceived": 17,
    "totalMissing": 3
  },
  "discrepancies": [
    {
      "productName": "Produto Y",
      "expected": 5,
      "received": 2,
      "missing": 3
    }
  ],
  "reportUrl": "/api/stock-movements/{id}/validations/{validationId}/discrepancy-report"
}
```

### Exportar relatório de discrepância

```
GET /api/stock-movements/{id}/validations/{validationId}/discrepancy-report
Accept: application/pdf | application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
```

---

## Implementação

### Serviço Principal: `TransferValidationService`

```java
public interface TransferValidationService {
    TransferValidation startValidation(UUID movementId, UUID userId);
    ScanResult scanBarcode(UUID validationId, String barcode);
    ValidationProgress getProgress(UUID validationId);
    ValidationResult complete(UUID validationId);
    byte[] generateDiscrepancyReport(UUID validationId, ReportFormat format);
}
```

### Transações

O método `complete()` deve ser transacional:
1. Atualiza estoque do Warehouse B (apenas itens recebidos)
2. Cria registros de discrepância para itens faltantes
3. Atualiza status da transferência
4. Marca validação como `COMPLETED`

### Validações de Segurança

- Verificar se usuário pertence ao warehouse destino
- Não permitir validação dupla
- Não permitir scan após validação finalizada
- Log de todas as tentativas de scan (sucesso e falha)

### Geração de Relatório

- PDF: Usar biblioteca existente ou adicionar iText/OpenPDF
- Excel: Usar Apache POI
- Template: Cabeçalho com dados da transferência + tabela de discrepâncias

---

## Fora do Escopo (Futuro)

- Alertas para transferências paradas em trânsito por muito tempo
- Notificações por email para discrepâncias
- Validação de lotes/batches específicos (por ora, valida só produto)
- Modo de entrada manual de quantidade
- Cancelamento de validação em progresso
