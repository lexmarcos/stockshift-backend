# Configuração do Postman para Testes E2E

## 📋 Visão Geral

Este guia descreve como configurar o Postman com as credenciais e tokens necessários para testes E2E do Stockshift Backend.

## 🚀 Configuração Rápida

### 1. Importar Collection e Environment

1. **Importe a Collection:**
   - Abra o Postman
   - Clique em "Import"
   - Selecione o arquivo `postman-collection.json`

2. **Importe o Environment:**
   - Clique em "Import"
   - Selecione o arquivo `postman.enviroment.json`
   - Selecione o environment "Stockshift - Local" no canto superior direito

### 2. Verificar Configuração Automática

O environment já vem pré-configurado com as credenciais do usuário de teste E2E:

```json
{
  "baseUrl": "http://localhost:8080",
  "access_token": "dev-access-token-12345678901234567890123456789012345678901234567890",
  "refresh_token": "dev-refresh-token-12345678901234567890123456789012345678901234567890",
  "test_username": "testuser",
  "test_password": "testpass123",
  "test_email": "test@stockshift.com"
}
```

## 🧪 Executando Testes E2E

### Opção 1: Usando Credenciais Pré-configuradas (Recomendado)

Para testes automatizados, use os tokens fixos que já estão configurados:

1. **Teste de Autenticação Básica:**
   - Execute "7. Test - Authenticated Endpoint"
   - O token já está configurado e deve funcionar

2. **Teste de Permissões Admin:**
   - Execute "8. Test - Admin Only"
   - Deve retornar sucesso pois o usuário de teste tem role ADMIN

### Opção 2: Login Dinâmico

Para simulação mais realista do fluxo de login:

1. **Obter Credenciais Atualizadas:**
   - Execute "0. Get Test User Credentials (Dev)"
   - Isso atualizará automaticamente as variáveis do environment

2. **Login com Usuário de Teste:**
   - Execute "1A. Login - Test User (E2E)"
   - Isso atualizará os tokens com versões novas

3. **Executar Testes:**
   - Execute qualquer endpoint autenticado
   - Os tokens foram atualizados automaticamente

## 🔧 Usuários Disponíveis

### Usuário de Teste E2E (Automático)
- **Username:** `testuser`
- **Password:** `testpass123`
- **Email:** `test@stockshift.com`
- **Role:** `ADMIN`
- **Tokens:** Fixos e válidos por 1 ano em desenvolvimento

### Usuários Manuais
- **Admin:** `admin` / `admin123`
- **Manager:** `manager` / `manager123` 
- **Seller:** `seller` / `seller123`

## 🤖 Automação de Testes

### Scripts Automáticos

Todas as requisições de login têm scripts automáticos que atualizam as variáveis:

```javascript
// Exemplo de script automático nos logins
if (pm.response.code === 200) {
    const response = pm.response.json();
    pm.environment.set('access_token', response.accessToken);
    pm.environment.set('refresh_token', response.refreshToken);
    pm.environment.set('user_id', response.user.id);
    console.log('Tokens updated successfully');
}
```

### Executando Collection Completa

Para executar todos os testes automaticamente:

1. **Via Postman Runner:**
   - Clique em "Runner" no canto superior esquerdo
   - Selecione a collection "Stockshift API"
   - Selecione o environment "Stockshift - Local"
   - Clique em "Run Stockshift API"

2. **Via Newman (CLI):**
   ```bash
   # Instalar Newman
   npm install -g newman
   
   # Executar collection
   newman run postman-collection.json \
     -e postman.enviroment.json \
     --reporters cli,json \
     --reporter-json-export results.json
   ```

## 🔍 Debugging e Troubleshooting

### Verificar Variáveis

No Postman, clique no ícone do "olho" (👁️) no canto superior direito para ver todas as variáveis de environment atuais.

### Logs de Console

Todos os scripts automáticos produzem logs no console do Postman. Para vê-los:
1. Abra o console do Postman (View > Show Postman Console)
2. Execute uma requisição
3. Veja os logs de sucesso/erro

### Problemas Comuns

1. **Token Inválido/Expirado:**
   - Execute "0. Get Test User Credentials (Dev)" 
   - Ou execute "1A. Login - Test User (E2E)"

2. **Aplicação não está rodando:**
   - Verifique se a aplicação está rodando em http://localhost:8080
   - Execute: `./gradlew bootRun --args='--spring.profiles.active=dev'`

3. **Usuário de teste não existe:**
   - Verifique se `app.test-user.enabled=true` está configurado
   - Verifique se está rodando em perfil de desenvolvimento

## 📊 Cobertura de Testes

A collection inclui testes para:

### Autenticação
- ✅ Login com diferentes tipos de usuário
- ✅ Refresh de tokens
- ✅ Logout
- ✅ Credenciais de teste dinâmicas

### Autorização
- ✅ Endpoints públicos
- ✅ Endpoints autenticados
- ✅ Endpoints apenas para Admin
- ✅ Endpoints apenas para Manager

### APIs de Negócio
- ✅ Gerenciamento de usuários
- ✅ Gerenciamento de marcas
- ✅ Gerenciamento de categorias
- ✅ Gerenciamento de atributos
- ✅ Gerenciamento de produtos
- ✅ Gerenciamento de warehouses

## 🚀 Integração com CI/CD

Para integrar com pipelines automatizados:

```yaml
# Exemplo para GitHub Actions
- name: Run API Tests
  run: |
    npm install -g newman
    newman run postman-collection.json \
      -e postman.enviroment.json \
      --bail \
      --reporters cli,junit \
      --reporter-junit-export results.xml
```

## 📚 Recursos Adicionais

- [TEST_USER.md](./TEST_USER.md) - Documentação detalhada do usuário de teste
- [test-api.sh](./test-api.sh) - Script de exemplo para testes via curl
- [AUTH_API.md](./AUTH_API.md) - Documentação da API de autenticação

---

💡 **Dica:** Para testes E2E mais eficientes, use sempre o usuário de teste automático (`testuser`) que possui tokens fixos e não expira em ambiente de desenvolvimento.
