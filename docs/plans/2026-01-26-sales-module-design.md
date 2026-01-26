# Design do Módulo de Vendas (Sales)

**Data:** 2026-01-26  
**Status:** Aprovado

## Visão Geral

Implementar um módulo de vendas separado que permita registrar vendas de produtos com diferentes métodos de pagamento (débito, crédito, dinheiro, fiado, etc.) e que reduza automaticamente o estoque ao finalizar a venda.

## Requisitos

- Venda pode incluir múltiplos produtos
- Registrar método de pagamento (apenas controle simples, sem parcelas)
- Controlar preços unitários e valores totais
- Descontar do estoque imediatamente ao criar venda
- Permitir cancelamento de vendas com devolução ao estoque
- Integração com sistema de movimentações de estoque existente

## Arquitetura

### Entidades

#### Sale
Representa a venda completa:
- `id` (Long)
- `tenantId` (Long)
- `warehouseId` (Long) - Warehouse de origem
- `userId` (Long) - Usuário que realizou a venda
- `customerId` (Long, opcional) - Cliente, se houver cadastro
- `customerName` (String, opcional) - Nome para vendas fiado
- `paymentMethod` (Enum) - Método de pagamento
- `status` (Enum) - COMPLETED, CANCELLED
- `subtotal` (BigDecimal) - Soma dos itens
- `discount` (BigDecimal) - Desconto aplicado
- `total` (BigDecimal) - Valor final
- `notes` (String) - Observações
- `stockMovementId` (Long) - Movimentação de estoque vinculada
- `createdAt` (LocalDateTime)
- `completedAt` (LocalDateTime)
- `cancelledAt` (LocalDateTime, opcional)
- `cancelledBy` (Long, opcional)
- `cancellationReason` (String, opcional)
- `items` (List<SaleItem>) - OneToMany

#### SaleItem
Representa cada produto vendido:
- `id` (Long)
- `saleId` (Long) - ManyToOne para Sale
- `productId` (Long)
- `batchId` (Long, opcional) - Lote específico
- `quantity` (Integer)
- `unitPrice` (BigDecimal)
- `subtotal` (BigDecimal) - quantity * unitPrice

#### PaymentMethod (Enum)
- `CASH` - Dinheiro
- `DEBIT_CARD` - Cartão de débito
- `CREDIT_CARD` - Cartão de crédito
- `INSTALLMENT` - Fiado/Crediário
- `PIX` - PIX
- `BANK_TRANSFER` - Transferência bancária
- `OTHER` - Outros

#### SaleStatus (Enum)
- `COMPLETED` - Venda finalizada
- `CANCELLED` - Venda cancelada

### API Endpoints

```
POST   /api/sales              - Criar nova venda
GET    /api/sales              - Listar vendas (filtros: data, status, método pagamento)
GET    /api/sales/{id}         - Detalhes de uma venda
PUT    /api/sales/{id}/cancel  - Cancelar venda
GET    /api/sales/report       - Relatório de vendas
```

### DTOs

#### CreateSaleRequest
```json
{
  "warehouseId": 1,
  "paymentMethod": "CASH",
  "customerId": 10,
  "customerName": "João Silva",
  "discount": 10.50,
  "notes": "Venda balcão",
  "items": [
    {
      "productId": 5,
      "batchId": 20,
      "quantity": 2,
      "unitPrice": 50.00
    }
  ]
}
```

#### SaleResponse
```json
{
  "id": 100,
  "warehouseId": 1,
  "warehouseName": "Loja Principal",
  "userId": 3,
  "userName": "Maria Santos",
  "customerId": 10,
  "customerName": "João Silva",
  "paymentMethod": "CASH",
  "status": "COMPLETED",
  "subtotal": 100.00,
  "discount": 10.50,
  "total": 89.50,
  "notes": "Venda balcão",
  "stockMovementId": 250,
  "createdAt": "2026-01-26T10:30:00",
  "completedAt": "2026-01-26T10:30:00",
  "items": [
    {
      "id": 150,
      "productId": 5,
      "productName": "Produto X",
      "batchId": 20,
      "quantity": 2,
      "unitPrice": 50.00,
      "subtotal": 100.00
    }
  ]
}
```

#### CancelSaleRequest
```json
{
  "reason": "Cliente desistiu da compra"
}
```

## Fluxo de Negócio

### Criação de Venda

1. **Validação inicial** (SaleService):
   - Verifica se produtos existem e estão ativos
   - Verifica se warehouse existe e está ativo
   - Calcula se há estoque disponível

2. **Desconto de estoque** (BatchService):
   - Para cada item, busca batches disponíveis
   - Aplica estratégia FIFO (First In, First Out)
   - Se produto tem validade, prioriza lotes com vencimento próximo
   - Reduz quantidade do(s) batch(es) atomicamente

3. **Criação de movimentação**:
   - Cria StockMovement com tipo `SALE` e status `COMPLETED`
   - Cria StockMovementItem para cada produto
   - Vincula Sale → StockMovement

4. **Transação atômica**:
   - Todo processo em uma única transação `@Transactional`
   - Qualquer erro = rollback completo

### Cancelamento de Venda

1. **Validações**:
   - Venda existe e está com status `COMPLETED`
   - Usuário tem permissão `SALES:CANCEL`
   - Venda não está expirada para cancelamento (ex: até 30 dias)
   - Motivo é obrigatório

2. **Reversão de estoque**:
   - Cria StockMovement tipo `ADJUSTMENT` devolvendo ao warehouse
   - Incrementa quantidade nos batches originais

3. **Atualização da venda**:
   - Status → `CANCELLED`
   - Registra: `cancelledAt`, `cancelledBy`, `cancellationReason`

## Tratamento de Erros

- `InsufficientStockException` - Estoque insuficiente
- `SaleNotFoundException` - Venda não encontrada
- `SaleAlreadyCancelledException` - Venda já cancelada
- `InvalidSaleCancellationException` - Não pode cancelar
- `InvalidPriceException` - Preço inválido
- `EmptySaleException` - Venda sem itens
- `WarehouseNotFoundException` - Warehouse não encontrado

## Segurança

Permissões necessárias (usar sistema existente):
- `SALES:CREATE` - Criar vendas
- `SALES:READ` - Visualizar vendas
- `SALES:CANCEL` - Cancelar vendas (gerentes/admin)
- `SALES:VIEW_REPORTS` - Acessar relatórios

## Testes

### Testes Unitários
- Validações de request
- Cálculos de totais e descontos
- Lógica de seleção de batches (FIFO)

### Testes de Integração
- Fluxo completo de venda com desconto de estoque
- Venda com estoque insuficiente (deve falhar)
- Cancelamento com devolução ao estoque
- Venda com múltiplos produtos de diferentes lotes
- Concorrência (duas vendas simultâneas)

## Considerações Técnicas

### Banco de Dados

**Tabelas:**
- `sales` - Dados da venda
- `sale_items` - Itens vendidos

**Índices importantes:**
- `(tenant_id, created_at)` em sales
- `(tenant_id, status)` em sales
- `(sale_id)` em sale_items

**Controle de concorrência:**
- Usar `@Version` nas entidades Batch
- Locks otimistas para prevenir overselling

### Logs e Auditoria
- Log detalhado de todas operações de venda
- Registro de quem criou/cancelou cada venda
- Histórico completo mantido (soft delete)

### Performance
- Eager/Lazy loading apropriado
- Paginação em listagens
- Cache de produtos frequentemente vendidos (opcional)

## Próximos Passos (Implementação)

1. Criar enums: PaymentMethod, SaleStatus
2. Criar entidades: Sale, SaleItem
3. Criar repositories: SaleRepository, SaleItemRepository
4. Criar DTOs: Request e Response
5. Implementar SaleService com lógica de negócio
6. Criar SaleController com endpoints
7. Implementar tratamento de erros customizados
8. Adicionar permissões no sistema
9. Criar testes unitários
10. Criar testes de integração
11. Documentar endpoints (Swagger/OpenAPI)

## Melhorias Futuras (Opcional)

- Cadastro de clientes completo
- Controle de contas a receber (para vendas fiado)
- Integração com sistemas de pagamento
- Emissão de nota fiscal
- Relatórios avançados de vendas
- Dashboard com métricas de vendas
