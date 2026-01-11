# Price to Cents Migration Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Migrate all price fields from BigDecimal (decimal format) to Long (cents format) for Batch and Product entities

**Architecture:** Change price representation from `BigDecimal` (e.g., 10.50) to `Long` (e.g., 1050 cents) across all layers: database, entities, DTOs, and services. Database migration converts existing data by multiplying by 100.

**Tech Stack:** Java 17, Spring Boot, PostgreSQL, Flyway, JUnit, Mockito

---

## Task 1: Create Database Migration Script

**Files:**
- Create: `src/main/resources/db/migration/V10__migrate_prices_to_cents.sql`

**Step 1: Create Flyway migration script**

Create the file with the following content:

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
```

**Step 2: Verify migration file is valid**

Run: `cat src/main/resources/db/migration/V10__migrate_prices_to_cents.sql`
Expected: File content displays correctly

**Step 3: Commit**

```bash
git add src/main/resources/db/migration/V10__migrate_prices_to_cents.sql
git commit -m "feat: add database migration to convert prices to cents

Add Flyway migration V10 to convert batch price fields from DECIMAL(15,2)
to BIGINT (cents). Uses temporary columns to preserve data during migration.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 2: Update Batch Entity

**Files:**
- Modify: `src/main/java/br/com/stockshift/model/entity/Batch.java:46-50`
- Test: `src/test/java/br/com/stockshift/service/BatchServiceTest.java:74-75`

**Step 1: Update test to use Long values**

In `BatchServiceTest.java`, update the request builder:

```java
// Line 74-75: Change from BigDecimal to Long
.costPrice(1000L)  // R$10.00 in cents
.sellingPrice(2000L)  // R$20.00 in cents
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "BatchServiceTest"`
Expected: FAIL with compilation error "incompatible types: long cannot be converted to BigDecimal"

**Step 3: Update Batch entity**

In `Batch.java`, change:

```java
// Lines 46-47: Remove precision/scale, change type
@Column(name = "cost_price")
private Long costPrice;

// Lines 49-50: Remove precision/scale, change type
@Column(name = "selling_price")
private Long sellingPrice;
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "BatchServiceTest"`
Expected: PASS (6 tests)

**Step 5: Commit**

```bash
git add src/main/java/br/com/stockshift/model/entity/Batch.java src/test/java/br/com/stockshift/service/BatchServiceTest.java
git commit -m "feat: update Batch entity to use Long for prices

Change costPrice and sellingPrice from BigDecimal to Long to represent
values in cents. Update test fixtures to use cent values.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 3: Update BatchRequest DTO

**Files:**
- Modify: `src/main/java/br/com/stockshift/dto/warehouse/BatchRequest.java:36-39`

**Step 1: Update BatchRequest DTO**

In `BatchRequest.java`, change:

```java
// Lines 35-39: Change type and add Schema annotation
@Schema(description = "Cost price in cents", example = "1050")
@PositiveOrZero(message = "Cost price must be zero or positive")
private Long costPrice;

@Schema(description = "Selling price in cents", example = "1575")
@PositiveOrZero(message = "Selling price must be zero or positive")
private Long sellingPrice;
```

Add import:
```java
import io.swagger.v3.oas.annotations.media.Schema;
```

**Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/dto/warehouse/BatchRequest.java
git commit -m "feat: update BatchRequest to use Long for prices

Change price fields to Long (cents) and add OpenAPI schema documentation
with examples showing values are in cents.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 4: Update BatchResponse DTO

**Files:**
- Modify: `src/main/java/br/com/stockshift/dto/warehouse/BatchResponse.java:27-28`

**Step 1: Update BatchResponse DTO**

In `BatchResponse.java`, change:

```java
// Lines 27-28: Change type and add Schema annotation
@Schema(description = "Cost price in cents", example = "1050")
private Long costPrice;

@Schema(description = "Selling price in cents", example = "1575")
private Long sellingPrice;
```

Add import:
```java
import io.swagger.v3.oas.annotations.media.Schema;
```

**Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/dto/warehouse/BatchResponse.java
git commit -m "feat: update BatchResponse to use Long for prices

Change price fields to Long (cents) and add OpenAPI schema documentation.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 5: Update ProductBatchRequest DTO

**Files:**
- Modify: `src/main/java/br/com/stockshift/dto/warehouse/ProductBatchRequest.java:46-49`

**Step 1: Update ProductBatchRequest DTO**

In `ProductBatchRequest.java`, change:

```java
// Lines 46-49: Change type and add Schema annotation
@Schema(description = "Cost price in cents", example = "1050")
@PositiveOrZero(message = "Cost price must be zero or positive")
private Long costPrice;

@Schema(description = "Selling price in cents", example = "1575")
@PositiveOrZero(message = "Selling price must be zero or positive")
private Long sellingPrice;
```

Add import:
```java
import io.swagger.v3.oas.annotations.media.Schema;
```

**Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/br/com/stockshift/dto/warehouse/ProductBatchRequest.java
git commit -m "feat: update ProductBatchRequest to use Long for prices

Change price fields to Long (cents) and add OpenAPI schema documentation.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 6: Update Integration Tests

**Files:**
- Modify: `src/test/java/br/com/stockshift/controller/BatchControllerIntegrationTest.java`

**Step 1: Find and update all BigDecimal price values**

Search for all occurrences of `BigDecimal` price values and replace with Long cents:

```java
// Example changes:
// OLD: .costPrice(new BigDecimal("10.50"))
// NEW: .costPrice(1050L)

// OLD: .sellingPrice(new BigDecimal("15.75"))
// NEW: .sellingPrice(1575L)
```

Use find/replace:
- Find: `new BigDecimal\("(\d+)\.(\d+)"\)` (regex)
- Replace with Long cents by multiplying decimal by 100

**Step 2: Update imports**

Remove:
```java
import java.math.BigDecimal;
```

**Step 3: Run integration tests**

Run: `./gradlew test --tests "BatchControllerIntegrationTest"`
Expected: All tests pass (or fail due to database not running, which is acceptable)

**Step 4: Commit**

```bash
git add src/test/java/br/com/stockshift/controller/BatchControllerIntegrationTest.java
git commit -m "test: update BatchControllerIntegrationTest to use cents

Update all price assertions and test data to use Long cents instead of
BigDecimal decimals.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 7: Update ProductControllerIntegrationTest (if applicable)

**Files:**
- Modify: `src/test/java/br/com/stockshift/controller/ProductControllerIntegrationTest.java`

**Step 1: Check if Product has price fields**

Read the file and check if there are any price-related fields that need updating.

**Step 2: Update if needed**

If Product entity has price fields, update them following the same pattern as Task 6.

**Step 3: Commit (if changes made)**

```bash
git add src/test/java/br/com/stockshift/controller/ProductControllerIntegrationTest.java
git commit -m "test: update ProductControllerIntegrationTest to use cents

Update all price assertions and test data to use Long cents instead of
BigDecimal decimals.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 8: Verify BatchService (No Changes Needed)

**Files:**
- Read: `src/main/java/br/com/stockshift/service/BatchService.java`

**Step 1: Verify no conversion logic exists**

Search for any price conversion logic in BatchService. Since we're using Long everywhere, there should be no conversions needed.

Expected: Direct assignment like `batch.setCostPrice(request.getCostPrice())`

**Step 2: Run unit tests**

Run: `./gradlew test --tests "BatchServiceTest"`
Expected: PASS (6 tests)

**Step 3: No commit needed**

If no changes were required, skip the commit step.

---

## Task 9: Build and Verify

**Step 1: Clean build**

Run: `./gradlew clean build -x test`
Expected: BUILD SUCCESSFUL

**Step 2: Run all unit tests**

Run: `./gradlew test --tests "*Test"`
Expected: All unit tests pass

**Step 3: Verify migration is included**

Run: `ls -la src/main/resources/db/migration/ | grep V10`
Expected: Shows V10__migrate_prices_to_cents.sql

---

## Task 10: Update CHANGELOG (Optional)

**Files:**
- Modify: `CHANGELOG.md` (if exists)

**Step 1: Add entry to CHANGELOG**

```markdown
## [Unreleased]

### Changed
- **BREAKING:** Migrated all price fields from decimal to cents format
  - Batch: `costPrice` and `sellingPrice` now use Long (cents)
  - API requests/responses now expect integer cents instead of decimal values
  - Example: `1050` instead of `10.50` for R$10.50
  - See `docs/endpoints/price-format-change.md` for frontend migration guide
```

**Step 2: Commit**

```bash
git add CHANGELOG.md
git commit -m "docs: update CHANGELOG for price-to-cents migration

Document breaking change for price format migration.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Verification Checklist

After completing all tasks, verify:

- [ ] Migration script V10 exists and follows Flyway naming
- [ ] Batch entity uses Long for costPrice and sellingPrice
- [ ] All DTOs (BatchRequest, BatchResponse, ProductBatchRequest) use Long
- [ ] All DTOs have @Schema annotations documenting cents format
- [ ] BatchServiceTest uses Long values (1000L, 2000L, etc.)
- [ ] Integration tests updated with Long cents values
- [ ] No BigDecimal imports remain in price-related files
- [ ] Unit tests pass: `./gradlew test --tests "*Test"`
- [ ] Build succeeds: `./gradlew build -x test`

---

## Testing in Development Environment

**Note:** Integration tests require Testcontainers (Docker) to run PostgreSQL. If Docker is not available, integration tests will fail with connection errors, which is expected.

**To test with real database:**

1. Ensure Docker is running
2. Run: `./gradlew test`
3. Testcontainers will start PostgreSQL automatically
4. Flyway will run migrations including V10
5. All tests should pass

**Manual API testing:**

1. Start the application
2. Check Swagger UI at `/swagger-ui.html`
3. Verify price fields show as `integer` type with examples in cents
4. Test creating a batch with cent values

---

## Rollback Plan

If issues are discovered after deployment:

1. Restore database from backup (taken before migration)
2. Revert Git commits
3. Redeploy previous version

The migration script is designed to be safe:
- Creates temporary columns first
- Converts data
- Only drops old columns after successful conversion

---

## Notes

- **DRY:** No conversion logic needed - Long used throughout
- **YAGNI:** No additional features, just type migration
- **TDD:** Tests updated before/with implementation changes
- **Frequent commits:** One commit per task for easy rollback

**Estimated Time:** 30-45 minutes for all tasks
