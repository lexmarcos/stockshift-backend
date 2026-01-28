# Design: Cadastro de Usuários em Tenant Existente

## Resumo

Endpoint para admins cadastrarem novos usuários em um tenant existente, com senha temporária gerada automaticamente.

## Decisões

- **Autorização:** Apenas usuários com `ROLE_ADMIN`
- **Senha:** Gerada automaticamente pelo sistema (temporária)
- **Roles:** Admin seleciona quais roles atribuir dentre as existentes no tenant

## Endpoint Principal

### `POST /api/users`

**Request:**
```json
{
  "email": "usuario@exemplo.com",
  "fullName": "Nome Completo",
  "roleIds": ["uuid-role-1", "uuid-role-2"]
}
```

**Response (201 Created):**
```json
{
  "userId": "uuid",
  "email": "usuario@exemplo.com",
  "fullName": "Nome Completo",
  "temporaryPassword": "Ab3xK9mP",
  "mustChangePassword": true,
  "roles": ["VENDEDOR", "ESTOQUISTA"]
}
```

## Validações

1. Email único no tenant
2. Todas as roleIds devem existir e pertencer ao tenant do admin
3. Pelo menos uma role obrigatória

## Endpoint de Troca de Senha

### `POST /api/auth/change-password`

**Request:**
```json
{
  "currentPassword": "senhaAtual",
  "newPassword": "novaSenha123"
}
```

**Response (200 OK):**
```json
{
  "message": "Password changed successfully"
}
```

## Alterações na Entidade User

Novo campo:
```java
private boolean mustChangePassword = false;
```

## Migration V20

```sql
ALTER TABLE users ADD COLUMN must_change_password BOOLEAN DEFAULT FALSE;
```

## Arquivos a Criar

- `dto/user/CreateUserRequest.java`
- `dto/user/CreateUserResponse.java`
- `dto/auth/ChangePasswordRequest.java`
- `controller/UserController.java`
- `service/UserService.java`
- `util/PasswordGeneratorUtil.java`

## Arquivos a Modificar

- `model/entity/User.java` - Adicionar campo `mustChangePassword`
- `dto/auth/LoginResponse.java` - Adicionar campo `mustChangePassword`
- `service/AuthService.java` - Retornar `mustChangePassword` no login
- `controller/AuthController.java` - Adicionar endpoint change-password
