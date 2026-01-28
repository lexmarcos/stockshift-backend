# Design: Associação Usuário-Warehouse

## Resumo

Usuários devem ser associados a um ou mais warehouses. Sem associação, o usuário não consegue ver nem manipular dados de warehouses não associados.

## Decisões

- **ADMIN:** Tem acesso total a todos os warehouses, ignora associações
- **Sem warehouse:** Usuário não-admin sem warehouses tem acesso bloqueado (403)
- **Criação:** Warehouse obrigatório na criação do usuário
- **Escopo:** Leitura e escrita filtrados por warehouse

## Modelo de Dados

### Nova tabela: `user_warehouses`

```sql
CREATE TABLE user_warehouses (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    warehouse_id UUID NOT NULL REFERENCES warehouses(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, warehouse_id)
);
```

## Endpoints Afetados

### Leitura (filtrados)
- `GET /api/warehouses`
- `GET /api/batches`
- `GET /api/stock-movements`
- `GET /api/sales`
- `GET /api/reports/*`

### Escrita (validados)
- `POST /api/batches`
- `POST /api/stock-movements`
- `POST /api/sales`

## Criação de Usuário

**Request atualizado:**
```json
{
  "email": "user@example.com",
  "fullName": "Nome",
  "roleIds": ["uuid"],
  "warehouseIds": ["uuid1", "uuid2"]
}
```

## Bloqueio de Acesso

Usuários sem warehouse recebem `403 Forbidden` em endpoints protegidos.

Endpoints permitidos sem warehouse:
- `POST /api/auth/change-password`
- `POST /api/auth/logout`

## Arquivos

### Novos
- `V22__create_user_warehouses_table.sql`
- `service/WarehouseAccessService.java`

### Modificados
- `model/entity/User.java`
- `dto/user/CreateUserRequest.java`
- `dto/user/CreateUserResponse.java`
- `dto/user/UserResponse.java`
- `service/UserService.java`
- `security/UserPrincipal.java`
- `security/CustomUserDetailsService.java`
- Services de domínio (filtros)
