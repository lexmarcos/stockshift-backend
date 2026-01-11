# Breaking Change: Price Format Migration (Decimal to Cents)

**Date:** 2026-01-09
**Status:** ⚠️ Breaking Change
**Affected Areas:** Batch endpoints and related DTOs

## Overview

All price fields in batch-related endpoints have been migrated from decimal format (e.g., `10.50`) to integer cents format (e.g., `1050`). This change affects both request and response payloads.

## What Changed

### Data Type
- **Before:** `BigDecimal` (e.g., `10.50` = R$10,50)
- **After:** `Long` (e.g., `1050` = R$10,50)

### Affected Fields
- `costPrice` - Cost price in cents
- `sellingPrice` - Selling price in cents

## Affected Endpoints

### 1. Create Batch
**Endpoint:** `POST /api/batches`

#### Request Body Changes
```json
// BEFORE (deprecated)
{
  "productId": "uuid",
  "warehouseId": "uuid",
  "batchCode": "BATCH001",
  "quantity": 100,
  "costPrice": 10.50,      // ❌ BigDecimal
  "sellingPrice": 15.75     // ❌ BigDecimal
}

// AFTER (current)
{
  "productId": "uuid",
  "warehouseId": "uuid",
  "batchCode": "BATCH001",
  "quantity": 100,
  "costPrice": 1050,        // ✅ Long (cents)
  "sellingPrice": 1575      // ✅ Long (cents)
}
```

#### Response Changes
```json
// BEFORE (deprecated)
{
  "id": "uuid",
  "productId": "uuid",
  "warehouseId": "uuid",
  "costPrice": 10.50,      // ❌ BigDecimal
  "sellingPrice": 15.75     // ❌ BigDecimal
}

// AFTER (current)
{
  "id": "uuid",
  "productId": "uuid",
  "warehouseId": "uuid",
  "costPrice": 1050,        // ✅ Long (cents)
  "sellingPrice": 1575      // ✅ Long (cents)
}
```

---

### 2. Create Product with Batch
**Endpoint:** `POST /api/batches/with-product`
**Content-Type:** `multipart/form-data`

#### Request Part: "product" JSON Changes
```json
// BEFORE (deprecated)
{
  "name": "Product Name",
  "warehouseId": "uuid",
  "quantity": 100,
  "costPrice": 10.50,      // ❌ BigDecimal
  "sellingPrice": 15.75     // ❌ BigDecimal
}

// AFTER (current)
{
  "name": "Product Name",
  "warehouseId": "uuid",
  "quantity": 100,
  "costPrice": 1050,        // ✅ Long (cents)
  "sellingPrice": 1575      // ✅ Long (cents)
}
```

---

### 3. Get All Batches
**Endpoint:** `GET /api/batches`

#### Response Changes
```json
// BEFORE (deprecated)
[
  {
    "id": "uuid",
    "costPrice": 10.50,      // ❌ BigDecimal
    "sellingPrice": 15.75     // ❌ BigDecimal
  }
]

// AFTER (current)
[
  {
    "id": "uuid",
    "costPrice": 1050,        // ✅ Long (cents)
    "sellingPrice": 1575      // ✅ Long (cents)
  }
]
```

---

### 4. Get Batch by ID
**Endpoint:** `GET /api/batches/{id}`

Response follows same pattern as "Get All Batches" above.

---

### 5. Get Batches by Warehouse
**Endpoint:** `GET /api/batches/warehouse/{warehouseId}`

Response follows same pattern as "Get All Batches" above.

---

### 6. Get Batches by Product
**Endpoint:** `GET /api/batches/product/{productId}`

Response follows same pattern as "Get All Batches" above.

---

### 7. Get Expiring Batches
**Endpoint:** `GET /api/batches/expiring/{daysAhead}`

Response follows same pattern as "Get All Batches" above.

---

### 8. Get Low Stock Batches
**Endpoint:** `GET /api/batches/low-stock/{threshold}`

Response follows same pattern as "Get All Batches" above.

---

### 9. Update Batch
**Endpoint:** `PUT /api/batches/{id}`

Request and response follow same pattern as "Create Batch" above.

---

## Frontend Migration Guide

### 1. Converting from Decimal to Cents (Request)

```typescript
// JavaScript/TypeScript example
function convertToCents(price: number): number {
  return Math.round(price * 100);
}

// Usage
const request = {
  productId: "uuid",
  warehouseId: "uuid",
  costPrice: convertToCents(10.50),      // 1050
  sellingPrice: convertToCents(15.75)     // 1575
};
```

### 2. Converting from Cents to Decimal (Response)

```typescript
// JavaScript/TypeScript example
function convertToDecimal(cents: number): number {
  return cents / 100;
}

// Usage
const response = await fetch('/api/batches/123');
const batch = await response.json();
const costPriceInReais = convertToDecimal(batch.data.costPrice);  // 10.50
const sellingPriceInReais = convertToDecimal(batch.data.sellingPrice);  // 15.75
```

### 3. Formatting for Display

```typescript
// JavaScript/TypeScript example
function formatPrice(cents: number, locale: string = 'pt-BR', currency: string = 'BRL'): string {
  const decimal = cents / 100;
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency: currency
  }).format(decimal);
}

// Usage
const batch = { costPrice: 1050, sellingPrice: 1575 };
console.log(formatPrice(batch.costPrice));      // "R$ 10,50"
console.log(formatPrice(batch.sellingPrice));    // "R$ 15,75"
```

### 4. Form Input Handling

```typescript
// React example for currency input
function PriceInput({ value, onChange }: { value: number, onChange: (cents: number) => void }) {
  const [displayValue, setDisplayValue] = React.useState((value / 100).toFixed(2));

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const input = e.target.value;
    setDisplayValue(input);

    // Convert to cents when sending to backend
    const decimal = parseFloat(input) || 0;
    onChange(Math.round(decimal * 100));
  };

  return (
    <input
      type="number"
      step="0.01"
      value={displayValue}
      onChange={handleChange}
      placeholder="0.00"
    />
  );
}
```

## Important Notes

### ⚠️ Breaking Changes
1. **Type Change:** All price fields changed from `BigDecimal` to `Long`
2. **Value Scale:** All values are now in cents (multiply by 100)
3. **No Decimal Places:** The backend no longer accepts or returns decimal values

### ✅ Validation
- The backend validates that prices are non-negative (`@PositiveOrZero`)
- Maximum value: `9,223,372,036,854,775,807` cents (~92 trillion reais)
- Minimum value: `0` cents

### 🔍 Testing Checklist
- [ ] Update all API client code to send/receive cents
- [ ] Update all display formatters to convert cents to decimal
- [ ] Update all form inputs to convert user input to cents
- [ ] Test with edge cases: 0, very large values, fractional cents (should round)
- [ ] Verify calculations (subtotals, totals) use cent values
- [ ] Check reports and exports for correct formatting

### 🚨 Common Pitfalls
1. **Sending decimal values:** `{"costPrice": 10.50}` → Backend receives `10` or `11` (truncated/rounded)
2. **Forgetting to convert on display:** Shows `1050` instead of `R$ 10,50`
3. **Double conversion:** Converting cents to decimal twice → shows `0.1050`
4. **Floating point math:** Use integer math on cents, convert only for display

## Database Migration

The backend includes a Flyway migration script that automatically converts existing decimal values to cents. No manual data intervention is required.

## API Documentation

The OpenAPI/Swagger documentation has been updated with:
- Schema descriptions indicating values are in cents
- Example values in cent format (e.g., `1050`)

## Support

If you encounter issues during migration, please:
1. Check the examples above
2. Verify your conversion functions
3. Test with the updated Swagger UI at `/swagger-ui.html`
4. Contact the backend team for support
