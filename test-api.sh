#!/bin/bash

# Script de exemplo para testar a API usando o usuário de teste
# Execute apenas em ambiente de desenvolvimento

BASE_URL="http://localhost:8080"

echo "🚀 Testando API com usuário de teste..."
echo

# 1. Obter credenciais do usuário de teste
echo "📋 1. Obtendo credenciais do usuário de teste:"
CREDENTIALS=$(curl -s "$BASE_URL/api/v1/dev/test-user")
echo "$CREDENTIALS" | jq .

# Extrair tokens das credenciais
ACCESS_TOKEN=$(echo "$CREDENTIALS" | jq -r '.accessToken')
REFRESH_TOKEN=$(echo "$CREDENTIALS" | jq -r '.refreshToken')
USERNAME=$(echo "$CREDENTIALS" | jq -r '.username')
PASSWORD=$(echo "$CREDENTIALS" | jq -r '.password')

echo
echo "🔑 Access Token: $ACCESS_TOKEN"
echo "🔄 Refresh Token: $REFRESH_TOKEN"
echo

# 2. Testar login tradicional
echo "🔐 2. Testando login tradicional:"
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")
echo "$LOGIN_RESPONSE" | jq .
echo

# 3. Testar endpoint público
echo "🌐 3. Testando endpoint público:"
curl -s "$BASE_URL/api/v1/test/public" | jq .
echo

# 4. Testar endpoint autenticado com token fixo
echo "🔒 4. Testando endpoint autenticado com token fixo:"
curl -s "$BASE_URL/api/v1/test/authenticated" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
echo

# 5. Testar endpoint admin com token fixo
echo "👨‍💼 5. Testando endpoint admin com token fixo:"
curl -s "$BASE_URL/api/v1/test/admin" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
echo

# 6. Listar usuários (requer permissão ADMIN)
echo "👥 6. Listando usuários (requer ADMIN):"
curl -s "$BASE_URL/api/v1/users" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
echo

echo "✅ Teste concluído!"
echo
echo "💡 Dicas:"
echo "- Use o token fixo '$ACCESS_TOKEN' em seus testes automatizados"
echo "- O refresh token fixo é: '$REFRESH_TOKEN'"
echo "- Credenciais: $USERNAME / $PASSWORD"
