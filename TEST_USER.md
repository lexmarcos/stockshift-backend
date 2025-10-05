# Usuário de Teste para Desenvolvimento

## 📋 Visão Geral

Este sistema inclui um usuário de teste automaticamente criado durante o desenvolvimento para facilitar testes de API e E2E. O usuário é criado apenas em ambiente de desenvolvimento e possui tokens fixos para facilitar a automação de testes.

## 🔧 Configuração

### Habilitando o Usuário de Teste

Para habilitar o usuário de teste, configure as seguintes propriedades no arquivo `application-dev.properties`:

```properties
# Test User Configuration (Development Only)
app.test-user.enabled=true
app.test-user.username=testuser
app.test-user.email=test@stockshift.com
app.test-user.password=testpass123
app.test-user.role=ADMIN
app.test-user.access-token=dev-access-token-12345678901234567890123456789012345678901234567890
app.test-user.refresh-token=dev-refresh-token-12345678901234567890123456789012345678901234567890
```

### Executando com Profile de Desenvolvimento

```bash
# Opção 1: Usando o perfil dev
./gradlew bootRun --args='--spring.profiles.active=dev'

# Opção 2: Usando variável de ambiente
export SPRING_PROFILES_ACTIVE=dev
./gradlew bootRun

# Opção 3: Se nenhum perfil estiver configurado, assume desenvolvimento por padrão
./gradlew bootRun
```

## 👤 Credenciais do Usuário de Teste

### Informações de Login
- **Username:** `testuser`
- **Password:** `testpass123`
- **Email:** `test@stockshift.com`
- **Role:** `ADMIN`

### Tokens Fixos (Válidos por 1 ano em desenvolvimento)
- **Access Token:** `dev-access-token-12345678901234567890123456789012345678901234567890`
- **Refresh Token:** `dev-refresh-token-12345678901234567890123456789012345678901234567890`

## 🚀 Como Usar

### 1. Autenticação via Login
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "testpass123"
  }'
```

### 2. Usando Tokens Fixos Diretamente
```bash
# Fazer requisições autenticadas usando o token fixo
curl -X GET http://localhost:8080/api/v1/users \
  -H "Authorization: Bearer dev-access-token-12345678901234567890123456789012345678901234567890"
```

### 3. Endpoint para Obter Credenciais (Desenvolvimento)
```bash
# Obter todas as credenciais de teste
curl -X GET http://localhost:8080/api/v1/dev/test-user
```

Resposta:
```json
{
  "username": "testuser",
  "password": "testpass123",
  "accessToken": "dev-access-token-12345678901234567890123456789012345678901234567890",
  "refreshToken": "dev-refresh-token-12345678901234567890123456789012345678901234567890"
}
```

## 🧪 Testes E2E

### Exemplo em JavaScript/Node.js
```javascript
const testUserCredentials = {
  username: 'testuser',
  password: 'testpass123',
  accessToken: 'dev-access-token-12345678901234567890123456789012345678901234567890',
  refreshToken: 'dev-refresh-token-12345678901234567890123456789012345678901234567890'
};

// Usar token fixo diretamente
const response = await fetch('http://localhost:8080/api/v1/users', {
  headers: {
    'Authorization': `Bearer ${testUserCredentials.accessToken}`,
    'Content-Type': 'application/json'
  }
});
```

### Exemplo em Postman
1. Crie uma variável de ambiente `access_token` com o valor do token fixo
2. Configure o header `Authorization` como `Bearer {{access_token}}`
3. Importe o arquivo `postman.enviroment.json` e adicione as variáveis de teste

## 🔒 Segurança

### ⚠️ IMPORTANTE - Apenas para Desenvolvimento

- O usuário de teste é criado **APENAS** em ambiente de desenvolvimento
- Os tokens fixos são **APENAS** válidos em desenvolvimento
- Este recurso é **AUTOMATICAMENTE DESABILITADO** em produção
- **NUNCA** use estas credenciais em ambiente de produção

### Verificações de Segurança

O sistema implementa as seguintes verificações:

1. **Profile de Ambiente:** Verifica se o perfil `dev` está ativo ou se nenhum perfil está configurado
2. **Configuração Explícita:** Requer `app.test-user.enabled=true` para funcionar
3. **Tokens Específicos:** Os tokens fixos só são válidos para o usuário de teste
4. **Endpoint Restrito:** O endpoint `/api/v1/dev/test-user` só funciona em desenvolvimento

## 🛠️ Troubleshooting

### Usuário de Teste Não Foi Criado

1. Verifique se `app.test-user.enabled=true` está configurado
2. Confirme que está rodando em perfil de desenvolvimento
3. Verifique os logs da aplicação para mensagens de erro
4. Certifique-se que o banco de dados está acessível

### Tokens Não Funcionam

1. Verifique se está usando os tokens exatos da configuração
2. Confirme que o usuário de teste foi criado
3. Verifique se o perfil de desenvolvimento está ativo
4. Certifique-se que os tokens não foram modificados

### Logs Úteis

```
INFO  - Checking if test user exists...
INFO  - Creating test user for development environment...
INFO  - Test user created successfully: testuser
INFO  - Use these credentials for testing:
INFO  -   Username: testuser
INFO  -   Password: testpass123
INFO  -   Access Token: dev-access-token-...
INFO  -   Refresh Token: dev-refresh-token-...
```

## 📚 Arquivos Relacionados

- `TestUserProperties.java` - Configuração das propriedades
- `TestUserService.java` - Lógica de criação e gerenciamento
- `TestUserInitializer.java` - Inicialização automática na startup
- `DevController.java` - Endpoint para obter credenciais
- `JwtTokenProvider.java` - Validação de tokens fixos
- `application-dev.properties` - Configurações de desenvolvimento
