# Fix Migration History Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix the Flyway migration sequence V1-V38 so all migrations execute cleanly on a fresh database, enabling Testcontainers-based integration tests to pass.

**Architecture:** The root cause is that V33-V36 ALTER columns from INTEGER to NUMERIC(15,3), but inline CHECK constraints created in the original CREATE TABLE statements prevent type changes. We must drop the constraints, change the type, and recreate the constraints with the new type. Since this is a dev/refactoring phase and no production data exists, we'll fix the migrations in-place.

**Tech Stack:** PostgreSQL, Flyway, Spring Boot

---

## Problem Analysis

| Migration | Table | Column | Issue |
|-----------|-------|--------|-------|
| V5 | batches | quantity | Inline `CHECK (quantity >= 0)` prevents ALTER |
| V26 | transfer_items | expected_quantity | Inline `CHECK (expected_quantity > 0)` prevents ALTER |
| V26 | transfer_items | received_quantity | Inline `CHECK (received_quantity >= 0)` prevents ALTER |
| V27 | inventory_ledger | quantity | Inline `CHECK (quantity > 0)` prevents ALTER |
| V28 | transfer_in_transit | quantity | Inline `CHECK (quantity >= 0)` prevents ALTER |

---

## Task 1: Fix V33 - Transfer Items Quantities

**Files:**
- Modify: `src/main/resources/db/migration/V33__change_transfer_items_quantities_to_decimal.sql`

**Step 1: Update the migration to handle constraints**

Replace the entire file content with:

```sql
-- V33: Change transfer_items quantities from INTEGER to NUMERIC(15,3)

-- Step 1: Drop inline CHECK constraints
ALTER TABLE transfer_items DROP CONSTRAINT IF EXISTS chk_expected_positive;
ALTER TABLE transfer_items DROP CONSTRAINT IF EXISTS chk_received_non_negative;

-- Step 2: Change column types
ALTER TABLE transfer_items ALTER COLUMN expected_quantity TYPE NUMERIC(15,3) USING expected_quantity::NUMERIC(15,3);
ALTER TABLE transfer_items ALTER COLUMN received_quantity TYPE NUMERIC(15,3) USING received_quantity::NUMERIC(15,3);

-- Step 3: Recreate constraints with new type
ALTER TABLE transfer_items ADD CONSTRAINT chk_expected_positive CHECK (expected_quantity > 0);
ALTER TABLE transfer_items ADD CONSTRAINT chk_received_non_negative CHECK (received_quantity IS NULL OR received_quantity >= 0);
```

**Step 2: Verify syntax is valid**

Run: `cat src/main/resources/db/migration/V33__change_transfer_items_quantities_to_decimal.sql`
Expected: File shows updated content

**Step 3: Commit**

```bash
git add src/main/resources/db/migration/V33__change_transfer_items_quantities_to_decimal.sql
git commit -m "$(cat <<'EOF'
fix(migration): V33 drop/recreate constraints for type change

PostgreSQL requires dropping CHECK constraints before
altering column types, then recreating them.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Fix V34 - Batch Quantity

**Files:**
- Modify: `src/main/resources/db/migration/V34__change_batch_quantity_to_decimal.sql`

**Step 1: Update the migration to handle constraints**

Replace the entire file content with:

```sql
-- V34: Change batches quantity from INTEGER to NUMERIC(15,3)

-- Step 1: Drop inline CHECK constraint from V5 (unnamed, referenced by column)
-- PostgreSQL names inline constraints automatically, we need to find and drop it
DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    -- Find the unnamed inline constraint from V5
    SELECT con.conname INTO constraint_name
    FROM pg_constraint con
    JOIN pg_attribute att ON att.attnum = ANY(con.conkey) AND att.attrelid = con.conrelid
    WHERE con.conrelid = 'batches'::regclass
      AND con.contype = 'c'
      AND att.attname = 'quantity'
      AND con.conname != 'chk_batch_quantity_non_negative';

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE batches DROP CONSTRAINT %I', constraint_name);
    END IF;
END $$;

-- Step 2: Drop named constraint from V18
ALTER TABLE batches DROP CONSTRAINT IF EXISTS chk_batch_quantity_non_negative;

-- Step 3: Change column type
ALTER TABLE batches ALTER COLUMN quantity TYPE NUMERIC(15,3) USING quantity::NUMERIC(15,3);

-- Step 4: Recreate constraint
ALTER TABLE batches ADD CONSTRAINT chk_batch_quantity_non_negative CHECK (quantity >= 0);
```

**Step 2: Verify syntax is valid**

Run: `cat src/main/resources/db/migration/V34__change_batch_quantity_to_decimal.sql`
Expected: File shows updated content

**Step 3: Commit**

```bash
git add src/main/resources/db/migration/V34__change_batch_quantity_to_decimal.sql
git commit -m "$(cat <<'EOF'
fix(migration): V34 drop/recreate constraints for batch quantity

Handles both the inline constraint from V5 and the named
constraint from V18 before changing type.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Fix V35 - Transfer In Transit Quantity

**Files:**
- Modify: `src/main/resources/db/migration/V35__change_transfer_in_transit_quantity_to_decimal.sql`

**Step 1: Update the migration to handle constraints**

Replace the entire file content with:

```sql
-- V35: Change transfer_in_transit quantity from INTEGER to NUMERIC(15,3)

-- Step 1: Drop CHECK constraint
ALTER TABLE transfer_in_transit DROP CONSTRAINT IF EXISTS chk_transit_quantity_non_negative;

-- Step 2: Change column type
ALTER TABLE transfer_in_transit ALTER COLUMN quantity TYPE NUMERIC(15,3) USING quantity::NUMERIC(15,3);

-- Step 3: Recreate constraint
ALTER TABLE transfer_in_transit ADD CONSTRAINT chk_transit_quantity_non_negative CHECK (quantity >= 0);
```

**Step 2: Verify syntax is valid**

Run: `cat src/main/resources/db/migration/V35__change_transfer_in_transit_quantity_to_decimal.sql`
Expected: File shows updated content

**Step 3: Commit**

```bash
git add src/main/resources/db/migration/V35__change_transfer_in_transit_quantity_to_decimal.sql
git commit -m "$(cat <<'EOF'
fix(migration): V35 drop/recreate constraint for transit quantity

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Fix V36 - Inventory Ledger Quantities

**Files:**
- Modify: `src/main/resources/db/migration/V36__change_inventory_ledger_quantities_to_decimal.sql`

**Step 1: Update the migration to handle constraints**

Replace the entire file content with:

```sql
-- V36: Change inventory_ledger quantity and balance_after from INTEGER to NUMERIC(15,3)

-- Step 1: Drop CHECK constraint on quantity
ALTER TABLE inventory_ledger DROP CONSTRAINT IF EXISTS chk_quantity_positive;

-- Step 2: Change column types
ALTER TABLE inventory_ledger ALTER COLUMN quantity TYPE NUMERIC(15,3) USING quantity::NUMERIC(15,3);
ALTER TABLE inventory_ledger ALTER COLUMN balance_after TYPE NUMERIC(15,3) USING balance_after::NUMERIC(15,3);

-- Step 3: Recreate constraint
ALTER TABLE inventory_ledger ADD CONSTRAINT chk_quantity_positive CHECK (quantity > 0);
```

**Step 2: Verify syntax is valid**

Run: `cat src/main/resources/db/migration/V36__change_inventory_ledger_quantities_to_decimal.sql`
Expected: File shows updated content

**Step 3: Commit**

```bash
git add src/main/resources/db/migration/V36__change_inventory_ledger_quantities_to_decimal.sql
git commit -m "$(cat <<'EOF'
fix(migration): V36 drop/recreate constraint for ledger quantity

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Clean Up Backup Files

**Files:**
- Delete: `src/main/resources/db/migration/*.bak` (if any exist)

**Step 1: Remove any backup files from migrations folder**

```bash
rm -f src/main/resources/db/migration/*.bak
```

**Step 2: Verify no backup files remain**

Run: `ls src/main/resources/db/migration/*.bak 2>/dev/null || echo "No backup files"`
Expected: "No backup files"

**Step 3: Commit (if files were removed)**

```bash
git status --porcelain src/main/resources/db/migration/ | grep -q '\.bak' && \
git add -A src/main/resources/db/migration/ && \
git commit -m "chore: remove migration backup files" || \
echo "No backup files to remove"
```

---

## Task 6: Verify Migration Sequence

**Files:** None (verification only)

**Step 1: List all migrations in order**

Run: `ls -1 src/main/resources/db/migration/V*.sql | sort -V`

Expected output (verify sequence is continuous):
```
V1__create_tenants_and_users.sql
V2__create_roles_and_permissions.sql
V3__create_refresh_tokens.sql
V4__create_categories_and_products.sql
V5__create_warehouses_and_batches.sql
...
V36__change_inventory_ledger_quantities_to_decimal.sql
V37__create_transfer_events.sql
V38__create_scan_logs.sql
```

**Step 2: Verify no gaps in sequence**

Run: `ls src/main/resources/db/migration/V*.sql | sed 's/.*V\([0-9]*\)__.*/\1/' | sort -n | awk 'NR>1 && $1!=prev+1 {print "GAP after V"prev} {prev=$1}'`

Expected: No output (no gaps)

---

## Task 7: Reset Local Database

**Files:** None (environment reset)

**Step 1: Stop any running application**

Run: `pkill -f 'stockshift' || true`

**Step 2: Reset Docker containers**

Run: `cd /home/lexmarcos/projects/stockshift-backend && docker compose down -v 2>/dev/null || docker-compose down -v 2>/dev/null || echo "Docker compose not available, skip"`

**Step 3: Start fresh containers**

Run: `cd /home/lexmarcos/projects/stockshift-backend && docker compose up -d 2>/dev/null || docker-compose up -d 2>/dev/null || echo "Docker compose not available, skip"`

**Step 4: Wait for PostgreSQL to be ready**

Run: `sleep 5 && docker ps | grep postgres || echo "Postgres container check"`

---

## Task 8: Run Tests to Validate Migrations

**Files:** None (verification only)

**Step 1: Clean build artifacts**

Run: `./gradlew clean`
Expected: BUILD SUCCESSFUL

**Step 2: Run a single simple test first**

Run: `./gradlew test --tests "TransferControllerIntegrationTest.shouldCreateTransfer" -i 2>&1 | tail -50`
Expected: Test passes (Flyway migrations succeed)

**Step 3: If Step 2 fails, check Flyway error**

If test fails with FlywayMigrateException, read the error carefully:
- Constraint already exists → need to add `IF EXISTS` to DROP
- Constraint not found → need to find actual constraint name
- Other error → investigate and fix

**Step 4: Run all transfer tests**

Run: `./gradlew test --tests "*Transfer*" -i 2>&1 | tail -100`
Expected: All tests pass

**Step 5: Run full test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, all tests pass

---

## Task 9: Final Commit

**Files:** None (final verification)

**Step 1: Verify all changes are committed**

Run: `git status`
Expected: Working tree clean or only untracked plan file

**Step 2: Final commit for migration fix**

```bash
git add -A
git commit -m "$(cat <<'EOF'
fix(migration): resolve V33-V36 constraint conflicts

Fixed migrations that convert INTEGER to NUMERIC(15,3):
- V33: transfer_items.expected_quantity, received_quantity
- V34: batches.quantity (handles both V5 inline and V18 named constraints)
- V35: transfer_in_transit.quantity
- V36: inventory_ledger.quantity, balance_after

Each migration now:
1. Drops CHECK constraints before type change
2. Alters column type with explicit USING clause
3. Recreates CHECK constraints with new type

Tested: All integration tests pass with Testcontainers.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Summary Checklist

| Task | Description | Verification |
|------|-------------|--------------|
| 1 | Fix V33 transfer_items | Constraints dropped/recreated |
| 2 | Fix V34 batches | Both inline and named constraints handled |
| 3 | Fix V35 transfer_in_transit | Constraint dropped/recreated |
| 4 | Fix V36 inventory_ledger | Constraint dropped/recreated |
| 5 | Clean backup files | No .bak files in migrations |
| 6 | Verify sequence | V1-V38 continuous, no gaps |
| 7 | Reset local DB | Fresh PostgreSQL container |
| 8 | Run tests | All pass with Testcontainers |
| 9 | Final commit | All changes committed |

## Troubleshooting

**If tests still fail after Task 8:**

1. Read the full Flyway error message
2. Check which specific migration fails
3. The constraint name might be different - use this query to find actual names:
   ```sql
   SELECT con.conname, con.conrelid::regclass, att.attname
   FROM pg_constraint con
   JOIN pg_attribute att ON att.attnum = ANY(con.conkey) AND att.attrelid = con.conrelid
   WHERE con.conrelid IN ('batches'::regclass, 'transfer_items'::regclass,
                          'transfer_in_transit'::regclass, 'inventory_ledger'::regclass)
   AND con.contype = 'c';
   ```
4. Update the migration to use the actual constraint name
