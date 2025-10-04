#!/bin/bash

# Test Authentication API
# Make sure the application is running before executing this script

BASE_URL="http://localhost:8080/api/v1"

echo "================================"
echo "Testing Authentication API"
echo "================================"
echo ""

# Test 1: Public endpoint (no authentication required)
echo "1. Testing public endpoint..."
curl -s -X GET "$BASE_URL/test/public" | jq .
echo ""

# Test 2: Login with admin user
echo "2. Login with admin user..."
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123"
  }')

echo "$LOGIN_RESPONSE" | jq .
echo ""

# Extract tokens
ACCESS_TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.accessToken')
REFRESH_TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.refreshToken')

echo "Access Token: $ACCESS_TOKEN"
echo "Refresh Token: $REFRESH_TOKEN"
echo ""

# Test 3: Authenticated endpoint
echo "3. Testing authenticated endpoint with access token..."
curl -s -X GET "$BASE_URL/test/authenticated" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
echo ""

# Test 4: Admin endpoint
echo "4. Testing admin-only endpoint..."
curl -s -X GET "$BASE_URL/test/admin" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
echo ""

# Test 5: Manager endpoint
echo "5. Testing manager endpoint..."
curl -s -X GET "$BASE_URL/test/manager" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
echo ""

# Test 6: Refresh token
echo "6. Testing refresh token..."
REFRESH_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/refresh" \
  -H "Content-Type: application/json" \
  -d "{
    \"refreshToken\": \"$REFRESH_TOKEN\"
  }")

echo "$REFRESH_RESPONSE" | jq .
echo ""

NEW_ACCESS_TOKEN=$(echo "$REFRESH_RESPONSE" | jq -r '.accessToken')
echo "New Access Token: $NEW_ACCESS_TOKEN"
echo ""

# Test 7: Try to access without token (should fail)
echo "7. Testing authenticated endpoint without token (should fail)..."
curl -s -X GET "$BASE_URL/test/authenticated" | jq .
echo ""

# Test 8: Logout
echo "8. Testing logout..."
curl -s -X POST "$BASE_URL/auth/logout" \
  -H "Content-Type: application/json" \
  -d "{
    \"refreshToken\": \"$REFRESH_TOKEN\"
  }"
echo "Logout completed (204 No Content expected)"
echo ""

# Test 9: Try to use revoked refresh token (should fail)
echo "9. Testing refresh with revoked token (should fail)..."
curl -s -X POST "$BASE_URL/auth/refresh" \
  -H "Content-Type: application/json" \
  -d "{
    \"refreshToken\": \"$REFRESH_TOKEN\"
  }" | jq .
echo ""

# Test 10: Login with manager user and try admin endpoint (should fail)
echo "10. Login with manager user and test admin endpoint (should fail)..."
MANAGER_LOGIN=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "manager",
    "password": "manager123"
  }')

MANAGER_TOKEN=$(echo "$MANAGER_LOGIN" | jq -r '.accessToken')

curl -s -X GET "$BASE_URL/test/admin" \
  -H "Authorization: Bearer $MANAGER_TOKEN" | jq .
echo ""

echo "================================"
echo "All tests completed!"
echo "================================"
