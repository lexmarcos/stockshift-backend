# Warehouse Endpoints

## Overview

Gerencia warehouses do tenant atual.

Base path de aplicacao: `/stockshift`

Base path do recurso: `/api/warehouses`

Base URL efetiva: `/stockshift/api/warehouses`

Autenticacao: obrigatoria.

## Authorization Matrix

- `POST /api/warehouses`: `WAREHOUSE_CREATE` ou `ROLE_ADMIN`
- `GET /api/warehouses`: `WAREHOUSE_READ` ou `ROLE_ADMIN`
- `GET /api/warehouses/{id}`: `WAREHOUSE_READ` ou `ROLE_ADMIN`
- `GET /api/warehouses/active/{isActive}`: `WAREHOUSE_READ` ou `ROLE_ADMIN`
- `PUT /api/warehouses/{id}`: `WAREHOUSE_UPDATE` ou `ROLE_ADMIN`
- `DELETE /api/warehouses/{id}`: `WAREHOUSE_DELETE` ou `ROLE_ADMIN`
- `GET /api/warehouses/{id}/products`: `WAREHOUSE_READ` ou `ROLE_ADMIN`

## WarehouseRequest

Campos:

- `name` (obrigatorio, max 255)
- `city` (obrigatorio, max 100)
- `state` (obrigatorio, 2 letras maiusculas)
- `code` (opcional, max 20, `A-Z0-9-`)
- `address` (opcional, max 500)
- `isActive` (opcional, default `true`)

Exemplo:

```json
{
  "name": "Main Warehouse",
  "city": "New York",
  "state": "NY",
  "code": "MAIN-NY",
  "address": "123 Storage St",
  "isActive": true
}
```

Se `code` nao for enviado, o backend gera automaticamente com base em nome/cidade.

## Endpoints

### POST /api/warehouses
Cria warehouse para o tenant atual.

### GET /api/warehouses
Lista warehouses visiveis para o usuario atual.

- Admin (`ROLE_ADMIN`) ve todas do tenant.
- Usuario sem full-access ve apenas warehouses permitidas.

### GET /api/warehouses/{id}
Retorna warehouse por ID com validacao de acesso.

### GET /api/warehouses/active/{isActive}
Filtra por status ativo/inativo.

### PUT /api/warehouses/{id}
Atualiza dados da warehouse.

### DELETE /api/warehouses/{id}
Remove warehouse.

### GET /api/warehouses/{id}/products
Retorna produtos com estoque agregado no warehouse.

Query params de paginacao/sort:

- `page`
- `size`
- `sort`

Sort permitido apenas em:

- `name`
- `sku`
- `barcode`
- `active`
- `createdAt`
- `updatedAt`

Exemplo:

```bash
curl -H "Authorization: Bearer <token>" \
  "http://localhost:8080/stockshift/api/warehouses/{id}/products?page=0&size=20&sort=name,asc"
```

## Error Handling (comum)

- `400 Bad Request`: payload invalido.
- `403 Forbidden`: usuario sem acesso ao warehouse.
- `404 Not Found`: warehouse inexistente.
- `409 Conflict`: conflitos de integridade/regras de negocio.
