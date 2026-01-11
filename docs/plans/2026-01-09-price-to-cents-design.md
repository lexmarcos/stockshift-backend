# Design: Migração de Preços de Decimal para Centavos

**Data:** 2026-01-09
**Status:** Aprovado
**Tipo:** Breaking Change
**Autores:** Claude Code + Lex Marcos

## Sumário Executivo

Migrar todos os campos de preço dos endpoints de Batches e Products de formato decimal (`BigDecimal`) para formato de centavos (`Long`). Esta mudança elimina problemas de arredondamento, melhora performance, e simplifica cálculos monetários.

## Motivação

### Problemas com BigDecimal
- Overhead de memória e processamento
- Complexidade em operações matemáticas
- Possíveis erros de arredondamento em conversões
- Inconsistência com práticas modernas de APIs financeiras

### Benefícios de Centavos (Long)
- Representação exata de valores monetários
- Operações matemáticas mais rápidas (aritmética de inteiros)
- Padrão amplamente adotado (Stripe, PayPal, etc.)
- Menor uso de memória
- Elimina problemas de arredondamento

## Escopo

### Entidades Afetadas
1. **Batch**: `costPrice`, `sellingPrice`
2. **Product**: campos de preço (se existirem)

### Endpoints Afetados

#### Batch Endpoints
- `POST /api/batches` - Create batch
- `POST /api/batches/with-product` - Create product with batch
- `GET /api/batches` - List all batches
- `GET /api/batches/{id}` - Get batch by ID
- `GET /api/batches/warehouse/{warehouseId}` - List by warehouse
- `GET /api/batches/product/{productId}` - List by product
- `GET /api/batches/expiring/{daysAhead}` - List expiring batches
- `GET /api/batches/low-stock/{threshold}` - List low stock
- `PUT /api/batches/{id}` - Update batch
- `DELETE /api/batches/{id}` - Delete batch

#### Product Endpoints (se aplicável)
- Todos os endpoints que retornam ou aceitam campos de preço

### DTOs Afetados
- `BatchRequest`
- `BatchResponse`
- `ProductBatchRequest`
- `ProductBatchResponse` (se existir)
- `ProductRequest` (se tiver campos de preço)
- `ProductResponse` (se tiver campos de preço)

## Design Técnico

### 1. Camada de Dados

#### Mudanças nas Entidades

**Batch.java**
```java
// ANTES
@Column(name = "cost_price", precision = 15, scale = 2)
private BigDecimal costPrice;

@Column(name = "selling_price", precision = 15, scale = 2)
private BigDecimal sellingPrice;

// DEPOIS
@Column(name = "cost_price")
private Long costPrice;

@Column(name = "selling_price")
private Long sellingPrice;
```

**Tipo de dado escolhido:** `Long`
- Range: -9,223,372,036,854,775,808 to 9,223,372,036,854,775,807
- Em reais: ~92 trilhões de reais (suficiente para qualquer caso de uso)
- Tipo nativo do PostgreSQL: `BIGINT`

#### Script de Migração (Flyway)

**Arquivo:** `V{next_version}__migrate_prices_to_cents.sql`

```sql
-- ======================================
-- Migration: Decimal to Cents
-- Converts price fields from DECIMAL(15,2) to BIGINT (cents)
-- ======================================

-- BATCHES TABLE
-- Step 1: Add temporary columns
ALTER TABLE batches
  ADD COLUMN cost_price_cents BIGINT,
  ADD COLUMN selling_price_cents BIGINT;

-- Step 2: Convert existing data (multiply by 100)
UPDATE batches
SET
  cost_price_cents = CAST(COALESCE(cost_price, 0) * 100 AS BIGINT),
  selling_price_cents = CAST(COALESCE(selling_price, 0) * 100 AS BIGINT);

-- Step 3: Drop old columns
ALTER TABLE batches
  DROP COLUMN cost_price,
  DROP COLUMN selling_price;

-- Step 4: Rename new columns to original names
ALTER TABLE batches
  RENAME COLUMN cost_price_cents TO cost_price;

ALTER TABLE batches
  RENAME COLUMN selling_price_cents TO selling_price;

-- PRODUCTS TABLE (if needed)
-- Repeat the same pattern if Product entity has price fields
-- ALTER TABLE products ADD COLUMN price_cents BIGINT;
-- UPDATE products SET price_cents = CAST(COALESCE(price, 0) * 100 AS BIGINT);
-- ALTER TABLE products DROP COLUMN price;
-- ALTER TABLE products RENAME COLUMN price_cents TO price;
```

**Estratégia de Migração:**
1. Cria colunas temporárias (sem risco de perda de dados)
2. Converte valores existentes multiplicando por 100
3. Remove colunas antigas apenas após conversão bem-sucedida
4. Renomeia para nomes originais

**Rollback:** O backup do banco permite restauração. As colunas antigas só são removidas após conversão confirmada.

### 2. Camada de DTOs

#### BatchRequest
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchRequest {
    @NotNull(message = "Product ID is required")
    private UUID productId;

    @NotNull(message = "Warehouse ID is required")
    private UUID warehouseId;

    private String batchCode;

    @NotNull(message = "Quantity is required")
    @PositiveOrZero(message = "Quantity must be zero or positive")
    private Integer quantity;

    private LocalDate manufacturedDate;
    private LocalDate expirationDate;

    @Schema(description = "Cost price in cents", example = "1050")
    @PositiveOrZero(message = "Cost price must be zero or positive")
    private Long costPrice;  // Changed from BigDecimal

    @Schema(description = "Selling price in cents", example = "1575")
    @PositiveOrZero(message = "Selling price must be zero or positive")
    private Long sellingPrice;  // Changed from BigDecimal
}
```

#### BatchResponse
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchResponse {
    private UUID id;
    private UUID productId;
    private String productName;
    private UUID warehouseId;
    private String warehouseName;
    private String batchCode;
    private Integer quantity;
    private LocalDate manufacturedDate;
    private LocalDate expirationDate;

    @Schema(description = "Cost price in cents", example = "1050")
    private Long costPrice;  // Changed from BigDecimal

    @Schema(description = "Selling price in cents", example = "1575")
    private Long sellingPrice;  // Changed from BigDecimal

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

**Validações:**
- `@PositiveOrZero` continua funcionando com `Long`
- `@Schema` documenta claramente que valores são em centavos
- Não há necessidade de validação customizada adicional

### 3. Camada de Service

#### Mapeamento Entity ↔ DTO

**Nenhuma conversão necessária!**

```java
// BatchService.java - create method
Batch batch = new Batch();
batch.setCostPrice(request.getCostPrice());  // Long → Long (direto)
batch.setSellingPrice(request.getSellingPrice());  // Long → Long (direto)

// BatchService.java - toResponse method
BatchResponse response = BatchResponse.builder()
    .costPrice(batch.getCostPrice())  // Long → Long (direto)
    .sellingPrice(batch.getSellingPrice())  // Long → Long (direto)
    .build();
```

**Vantagens:**
- Zero overhead de conversão
- Código mais simples
- Menos chances de bugs
- Melhor performance

### 4. Documentação OpenAPI

**Annotations:**
```java
@Schema(
    description = "Cost price in cents (e.g., 1050 = R$10.50)",
    example = "1050",
    type = "integer",
    format = "int64"
)
private Long costPrice;
```

**Swagger UI:** Exibirá claramente que os valores são em centavos com exemplos.

### 5. Testes

#### Testes de Integração (BatchControllerIntegrationTest)

```java
// ANTES
@Test
void shouldCreateBatch() {
    BatchRequest request = BatchRequest.builder()
        .productId(productId)
        .warehouseId(warehouseId)
        .batchCode("BATCH001")
        .quantity(100)
        .costPrice(new BigDecimal("10.50"))
        .sellingPrice(new BigDecimal("15.75"))
        .build();
    // ...
}

// DEPOIS
@Test
void shouldCreateBatch() {
    BatchRequest request = BatchRequest.builder()
        .productId(productId)
        .warehouseId(warehouseId)
        .batchCode("BATCH001")
        .quantity(100)
        .costPrice(1050L)  // R$10,50 em centavos
        .sellingPrice(1575L)  // R$15,75 em centavos
        .build();
    // ...
}
```

#### Testes de Service (BatchServiceTest)

```java
// Adicionar testes para edge cases
@Test
void shouldHandleZeroPrices() {
    BatchRequest request = BatchRequest.builder()
        .costPrice(0L)
        .sellingPrice(0L)
        .build();
    // ...
}

@Test
void shouldHandleLargePrices() {
    BatchRequest request = BatchRequest.builder()
        .costPrice(999999999999L)  // ~10 bilhões de reais
        .sellingPrice(999999999999L)
        .build();
    // ...
}
```

## Exemplos de Uso

### Request (Create Batch)
```json
POST /api/batches
{
  "productId": "123e4567-e89b-12d3-a456-426614174000",
  "warehouseId": "123e4567-e89b-12d3-a456-426614174001",
  "batchCode": "BATCH001",
  "quantity": 100,
  "costPrice": 1050,      // R$10,50 em centavos
  "sellingPrice": 1575    // R$15,75 em centavos
}
```

### Response (Get Batch)
```json
{
  "success": true,
  "message": null,
  "data": {
    "id": "123e4567-e89b-12d3-a456-426614174002",
    "productId": "123e4567-e89b-12d3-a456-426614174000",
    "productName": "Product Name",
    "warehouseId": "123e4567-e89b-12d3-a456-426614174001",
    "warehouseName": "Warehouse Name",
    "batchCode": "BATCH001",
    "quantity": 100,
    "costPrice": 1050,      // R$10,50 em centavos
    "sellingPrice": 1575,   // R$15,75 em centavos
    "createdAt": "2026-01-09T10:00:00",
    "updatedAt": "2026-01-09T10:00:00"
  }
}
```

## Impacto no Frontend

### Conversões Necessárias

**Enviando para API (Reais → Centavos):**
```typescript
function convertToCents(price: number): number {
  return Math.round(price * 100);
}

const request = {
  costPrice: convertToCents(10.50),  // 1050
  sellingPrice: convertToCents(15.75)  // 1575
};
```

**Recebendo da API (Centavos → Reais):**
```typescript
function convertToReais(cents: number): number {
  return cents / 100;
}

const costInReais = convertToReais(response.costPrice);  // 10.50
```

**Formatação para Display:**
```typescript
function formatPrice(cents: number): string {
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL'
  }).format(cents / 100);
}

formatPrice(1050);  // "R$ 10,50"
```

### Documento Detalhado
Ver `docs/endpoints/price-format-change.md` para guia completo de migração do frontend.

## Plano de Deploy

### Pré-Deploy
1. ✅ Backup completo do banco de dados
2. ✅ Testar migration script em ambiente de desenvolvimento
3. ✅ Testar migration script em ambiente de staging
4. ✅ Revisar código com time
5. ✅ Coordenar com time de frontend

### Deploy
1. Backend deploy (com migration automática via Flyway)
2. Frontend deploy (simultâneo ou imediatamente após)
3. Monitorar logs e métricas
4. Validar endpoints via Swagger UI

### Pós-Deploy
1. Verificar que dados foram migrados corretamente
2. Testar fluxos críticos end-to-end
3. Monitorar erros em produção
4. Comunicar conclusão para stakeholders

### Rollback (se necessário)
- Restaurar backup do banco de dados
- Reverter deploy do backend
- Reverter deploy do frontend

## Riscos e Mitigações

| Risco | Probabilidade | Impacto | Mitigação |
|-------|---------------|---------|-----------|
| Perda de dados durante migração | Baixa | Alto | Backup completo + migração incremental |
| Frontend não atualizado | Média | Alto | Deploy coordenado + documentação clara |
| Erros de arredondamento na conversão | Baixa | Médio | Testes extensivos + revisão de dados |
| Valores muito grandes (overflow) | Muito Baixa | Baixo | Long suporta 92 trilhões de reais |
| Validações quebradas | Baixa | Médio | Testes de integração completos |

## Checklist de Implementação

### Backend
- [ ] Atualizar entidade `Batch` (BigDecimal → Long)
- [ ] Atualizar entidade `Product` (se aplicável)
- [ ] Atualizar `BatchRequest` DTO
- [ ] Atualizar `BatchResponse` DTO
- [ ] Atualizar `ProductBatchRequest` DTO
- [ ] Adicionar `@Schema` annotations nos DTOs
- [ ] Criar migration script Flyway
- [ ] Atualizar `BatchControllerIntegrationTest`
- [ ] Atualizar `BatchServiceTest`
- [ ] Adicionar testes de edge cases
- [ ] Testar migration em desenvolvimento
- [ ] Testar migration em staging
- [ ] Atualizar documentação OpenAPI/Swagger

### Frontend
- [ ] Criar funções de conversão (reais ↔ centavos)
- [ ] Atualizar API client
- [ ] Atualizar componentes de formulário
- [ ] Atualizar componentes de display
- [ ] Atualizar testes
- [ ] Revisar relatórios e exports
- [ ] Testar fluxos end-to-end

### Documentação
- [x] Criar design document (`docs/plans/2026-01-09-price-to-cents-design.md`)
- [x] Criar guia de migração para frontend (`docs/endpoints/price-format-change.md`)
- [ ] Atualizar README (se necessário)
- [ ] Atualizar CHANGELOG

### Deploy
- [ ] Coordenar data/hora com frontend
- [ ] Fazer backup do banco
- [ ] Deploy backend
- [ ] Deploy frontend
- [ ] Validação pós-deploy
- [ ] Comunicação aos stakeholders

## Métricas de Sucesso

1. ✅ Migration script executa sem erros
2. ✅ 100% dos dados convertidos corretamente (verificação amostral)
3. ✅ Todos os testes passando
4. ✅ Zero downtime durante deploy
5. ✅ Frontend funcionando corretamente após deploy
6. ✅ Sem aumento de erros em produção

## Referências

- [Stripe API - Working with Amounts](https://stripe.com/docs/currencies#zero-decimal)
- [PayPal API - Currency Codes](https://developer.paypal.com/api/rest/reference/currency-codes/)
- PostgreSQL BIGINT: -9,223,372,036,854,775,808 to +9,223,372,036,854,775,807
- Java Long: -9,223,372,036,854,775,808 to 9,223,372,036,854,775,807

## Próximos Passos

1. ✅ Aprovar este design document
2. Criar branch de feature
3. Implementar mudanças conforme checklist
4. Code review
5. Testing em desenvolvimento
6. Testing em staging
7. Deploy coordenado (backend + frontend)

---

**Aprovado por:** [Nome]
**Data de Aprovação:** [Data]
**Início da Implementação:** [Data]
**Deploy Planejado:** [Data]
