#!/bin/bash

# Test User Management API
# Make sure the application is running and you're logged in as admin

BASE_URL="http://localhost:8080/api/v1"

echo "================================"
echo "Testing User Management API"
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

# Step 2: Create a new user
echo "2. Creating new user (SELLER)..."
NEW_USER=$(curl -s -X POST "$BASE_URL/users" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "testuser@example.com",
    "password": "senha123",
    "role": "SELLER"
  }')

echo "$NEW_USER" | jq .
USER_ID=$(echo "$NEW_USER" | jq -r '.id')
echo "Created user ID: $USER_ID"
echo ""

# Step 3: Get all users
echo "3. Listing all users (paginated)..."
curl -s -X GET "$BASE_URL/users?page=0&size=10" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.content[] | {id, username, email, role, active}'
echo ""

# Step 4: Get user by ID
echo "4. Getting user by ID..."
curl -s -X GET "$BASE_URL/users/$USER_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq .
echo ""

# Step 5: Get user by username
echo "5. Getting user by username..."
curl -s -X GET "$BASE_URL/users/username/testuser" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq .
echo ""

# Step 6: Update user
echo "6. Updating user email and role..."
curl -s -X PUT "$BASE_URL/users/$USER_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "updated@example.com",
    "role": "MANAGER"
  }' | jq .
echo ""

# Step 7: Try to create duplicate user (should fail)
echo "7. Testing duplicate username (should fail with 409)..."
curl -s -X POST "$BASE_URL/users" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "another@example.com",
    "password": "senha123",
    "role": "SELLER"
  }' | jq .
echo ""

# Step 8: Test manager access
echo "8. Login as manager and list users..."
MANAGER_LOGIN=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "manager",
    "password": "manager123"
  }')

MANAGER_TOKEN=$(echo "$MANAGER_LOGIN" | jq -r '.accessToken')

curl -s -X GET "$BASE_URL/users?page=0&size=5" \
  -H "Authorization: Bearer $MANAGER_TOKEN" | jq '.content[] | {username, role}'
echo ""

# Step 9: Test manager cannot create user (should fail)
echo "9. Testing manager trying to create user (should fail with 403)..."
curl -s -X POST "$BASE_URL/users" \
  -H "Authorization: Bearer $MANAGER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "shouldfail",
    "email": "fail@example.com",
    "password": "senha123",
    "role": "SELLER"
  }' | jq .
echo ""

# Step 10: Test seller cannot list users (should fail)
echo "10. Login as seller and try to list users (should fail with 403)..."
SELLER_LOGIN=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "seller",
    "password": "seller123"
  }')

SELLER_TOKEN=$(echo "$SELLER_LOGIN" | jq -r '.accessToken')

curl -s -X GET "$BASE_URL/users" \
  -H "Authorization: Bearer $SELLER_TOKEN" | jq .
echo ""

# Step 11: Deactivate user
echo "11. Deactivating user (soft delete)..."
curl -s -X DELETE "$BASE_URL/users/$USER_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -w "\nHTTP Status: %{http_code}\n"
echo ""

# Step 12: Verify user is inactive
echo "12. Verifying user is inactive..."
curl -s -X GET "$BASE_URL/users/$USER_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '{username, email, active}'
echo ""

# Step 13: Reactivate user
echo "13. Reactivating user..."
curl -s -X PATCH "$BASE_URL/users/$USER_ID/activate" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '{username, email, active}'
echo ""

# Step 14: Test validation errors
echo "14. Testing validation (missing required fields)..."
curl -s -X POST "$BASE_URL/users" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "ab",
    "email": "invalid-email",
    "password": "123"
  }' | jq .
echo ""

# Step 15: Clean up - delete test user
echo "15. Cleaning up - deleting test user..."
curl -s -X DELETE "$BASE_URL/users/$USER_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -w "\nHTTP Status: %{http_code}\n"
echo ""

echo "================================"
echo "All user management tests completed!"
echo "================================"
