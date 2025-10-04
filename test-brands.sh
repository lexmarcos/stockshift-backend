#!/bin/bash

# Test Brand Management API
# Make sure the application is running

BASE_URL="http://localhost:8080/api/v1"

echo "================================"
echo "Testing Brand Management API"
echo "================================"
echo ""

# Step 1: Login as admin
echo "1. Login as admin to get token..."
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123"
  }')

ADMIN_TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.accessToken')
echo "Admin token obtained: ${ADMIN_TOKEN:0:50}..."
echo ""

# Step 2: Create brands
echo "2. Creating brands..."
NIKE=$(curl -s -X POST "$BASE_URL/brands" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Nike",
    "description": "American multinational corporation that designs, develops, manufactures, and sells footwear, apparel, equipment, accessories, and services."
  }')

echo "$NIKE" | jq .
NIKE_ID=$(echo "$NIKE" | jq -r '.id')
echo ""

ADIDAS=$(curl -s -X POST "$BASE_URL/brands" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Adidas",
    "description": "German multinational corporation that designs and manufactures shoes, clothing and accessories."
  }')

echo "$ADIDAS" | jq .
ADIDAS_ID=$(echo "$ADIDAS" | jq -r '.id')
echo ""

PUMA=$(curl -s -X POST "$BASE_URL/brands" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Puma",
    "description": "German multinational corporation that designs and manufactures athletic and casual footwear, apparel and accessories."
  }')

echo "$PUMA" | jq .
echo ""

# Step 3: Get all brands
echo "3. Listing all brands..."
curl -s -X GET "$BASE_URL/brands?page=0&size=10" | jq '.content[] | {id, name, active}'
echo ""

# Step 4: Get brand by ID
echo "4. Getting Nike brand by ID..."
curl -s -X GET "$BASE_URL/brands/$NIKE_ID" | jq .
echo ""

# Step 5: Get brand by name
echo "5. Getting Adidas brand by name..."
curl -s -X GET "$BASE_URL/brands/name/Adidas" | jq .
echo ""

# Step 6: Update brand
echo "6. Updating Nike brand description..."
curl -s -X PUT "$BASE_URL/brands/$NIKE_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "description": "Just Do It - Leading sports brand worldwide"
  }' | jq .
echo ""

# Step 7: Try to create duplicate brand (should fail)
echo "7. Testing duplicate brand name (should fail with 409)..."
curl -s -X POST "$BASE_URL/brands" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Nike",
    "description": "This should fail"
  }' | jq .
echo ""

# Step 8: Test manager can create brand
echo "8. Login as manager and create brand..."
MANAGER_LOGIN=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "manager",
    "password": "manager123"
  }')

MANAGER_TOKEN=$(echo "$MANAGER_LOGIN" | jq -r '.accessToken')

REEBOK=$(curl -s -X POST "$BASE_URL/brands" \
  -H "Authorization: Bearer $MANAGER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Reebok",
    "description": "American fitness footwear and clothing brand"
  }')

echo "$REEBOK" | jq .
REEBOK_ID=$(echo "$REEBOK" | jq -r '.id')
echo ""

# Step 9: Test seller cannot create brand (should fail)
echo "9. Login as seller and try to create brand (should fail with 403)..."
SELLER_LOGIN=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "seller",
    "password": "seller123"
  }')

SELLER_TOKEN=$(echo "$SELLER_LOGIN" | jq -r '.accessToken')

curl -s -X POST "$BASE_URL/brands" \
  -H "Authorization: Bearer $SELLER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Should Fail",
    "description": "This should not work"
  }' | jq .
echo ""

# Step 10: Test public access (no auth) - seller can view
echo "10. Testing public access to list brands (should work)..."
curl -s -X GET "$BASE_URL/brands?onlyActive=true" | jq '.content[] | {name, active}'
echo ""

# Step 11: Deactivate brand
echo "11. Deactivating Puma brand (admin only)..."
curl -s -X DELETE "$BASE_URL/brands/$ADIDAS_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -w "\nHTTP Status: %{http_code}\n"
echo ""

# Step 12: Verify brand is inactive
echo "12. Verifying Adidas brand is inactive..."
curl -s -X GET "$BASE_URL/brands/$ADIDAS_ID" | jq '{name, active}'
echo ""

# Step 13: List only active brands
echo "13. Listing only active brands..."
curl -s -X GET "$BASE_URL/brands?onlyActive=true" | jq '.content[] | {name, active}'
echo ""

# Step 14: Reactivate brand
echo "14. Reactivating Adidas brand..."
curl -s -X PATCH "$BASE_URL/brands/$ADIDAS_ID/activate" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '{name, active}'
echo ""

# Step 15: Test validation errors
echo "15. Testing validation (short name)..."
curl -s -X POST "$BASE_URL/brands" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "A"
  }' | jq .
echo ""

# Step 16: Clean up - delete test brands
echo "16. Cleaning up - deactivating test brands..."
curl -s -X DELETE "$BASE_URL/brands/$NIKE_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -w "\nNike: HTTP %{http_code}\n"

curl -s -X DELETE "$BASE_URL/brands/$ADIDAS_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -w "Adidas: HTTP %{http_code}\n"

curl -s -X DELETE "$BASE_URL/brands/$REEBOK_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -w "Reebok: HTTP %{http_code}\n"
echo ""

echo "================================"
echo "All brand tests completed!"
echo "================================"
