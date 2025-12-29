# Brand Management Design

**Data:** 2025-12-28
**Autor:** Sistema StockShift
**Status:** Aprovado

## Visão Geral

Adicionar funcionalidade de gerenciamento de marcas ao sistema. Um produto pode ter uma marca, e uma marca pode ter vários produtos. Exemplo: um perfume pode ser da marca Natura, e a Natura tem vários perfumes e maquiagens.

## Requisitos Funcionais

### Marca (Brand)
- **Nome:** obrigatório e único por tenant
- **Logo:** opcional (armazenado como URL)
- **Soft Delete:** marcas deletadas são mantidas no banco com `deletedAt`
- **Multi-tenant:** cada marca pertence a um tenant

### Relacionamento com Produto
- **Cardinalidade:** Many-to-One (muitos produtos para uma marca)
- **Obrigatoriedade:** marca é opcional no produto
- **Regra de Negócio:** não é possível deletar uma marca que possui produtos vinculados

## Arquitetura

### 1. Entidade Brand

```java
package br.com.stockshift.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "brands", uniqueConstraints = {
    @UniqueConstraint(columnNames = { "tenant_id", "name" })
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class Brand extends TenantAwareEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "deleted_at")
    private java.time.LocalDateTime deletedAt;
}
```

**Características:**
- Estende `TenantAwareEntity` (herda tenant_id, created_at, updated_at)
- Nome obrigatório e único por tenant via constraint
- Logo opcional armazenado como URL (máximo 500 caracteres)
- Soft delete com campo `deletedAt`

### 2. Alteração na Entidade Product

```java
// Adicionar em Product.java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "brand_id")
private Brand brand;
```

**Características:**
- Relacionamento Many-to-One
- Marca opcional (nullable)
- Lazy loading para performance
- Foreign key `brand_id` referencia `brands(id)`

### 3. Repository Layer

```java
package br.com.stockshift.repository;

import br.com.stockshift.model.entity.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BrandRepository extends JpaRepository<Brand, Long> {

    Optional<Brand> findByIdAndTenantIdAndDeletedAtIsNull(Long id, Long tenantId);

    List<Brand> findByTenantIdAndDeletedAtIsNull(Long tenantId);

    boolean existsByNameAndTenantIdAndDeletedAtIsNull(String name, Long tenantId);
}
```

**Adicionar em ProductRepository:**
```java
boolean existsByBrandIdAndDeletedAtIsNull(Long brandId);
```

### 4. Service Layer

**BrandService - Operações principais:**

#### Criar Marca
1. Validar nome obrigatório
2. Verificar se nome já existe no tenant (excluindo deletadas)
3. Criar e salvar marca

#### Atualizar Marca
1. Verificar se marca existe e não está deletada
2. Verificar se novo nome já existe no tenant (exceto a própria marca)
3. Atualizar e salvar

#### Deletar Marca (Soft Delete)
1. Verificar se marca existe e não está deletada
2. **VALIDAÇÃO CRÍTICA:** Verificar se existem produtos vinculados
3. Se houver produtos, lançar `BusinessException` bloqueando exclusão
4. Se não houver produtos, marcar `deletedAt = LocalDateTime.now()`

```java
public void delete(Long id) {
    Brand brand = findById(id);

    if (productRepository.existsByBrandIdAndDeletedAtIsNull(brand.getId())) {
        throw new BusinessException(
            "Não é possível deletar marca com produtos vinculados"
        );
    }

    brand.setDeletedAt(LocalDateTime.now());
    brandRepository.save(brand);
}
```

#### Listar Marcas
- Retornar apenas marcas não deletadas do tenant

#### Buscar por ID
- Retornar marca se existir, não estiver deletada e pertencer ao tenant

### 5. DTOs

**BrandRequest:**
```java
package br.com.stockshift.dto.brand;

import jakarta.validation.constraints.NotBlank;

public record BrandRequest(
    @NotBlank(message = "Nome é obrigatório")
    String name,

    String logoUrl  // opcional
) {}
```

**BrandResponse:**
```java
package br.com.stockshift.dto.brand;

import java.time.LocalDateTime;

public record BrandResponse(
    Long id,
    String name,
    String logoUrl,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
```

**Atualização em ProductRequest:**
```java
// Adicionar campo
private Long brandId;  // opcional
```

**Atualização em ProductResponse:**
```java
// Adicionar campo
private BrandResponse brand;  // pode ser null
```

### 6. Controller Layer

**BrandController - Endpoints REST:**

```java
package br.com.stockshift.controller;

@RestController
@RequestMapping("/api/brands")
public class BrandController {

    // POST /api/brands - Criar marca
    @PostMapping
    ResponseEntity<BrandResponse> create(@Valid @RequestBody BrandRequest request);

    // GET /api/brands - Listar todas as marcas (não deletadas)
    @GetMapping
    ResponseEntity<List<BrandResponse>> findAll();

    // GET /api/brands/{id} - Buscar marca por ID
    @GetMapping("/{id}")
    ResponseEntity<BrandResponse> findById(@PathVariable Long id);

    // PUT /api/brands/{id} - Atualizar marca
    @PutMapping("/{id}")
    ResponseEntity<BrandResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody BrandRequest request
    );

    // DELETE /api/brands/{id} - Deletar marca (soft delete)
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id);
}
```

### 7. Validações no ProductService

**Ao criar/atualizar produto:**
```java
if (request.brandId() != null) {
    Brand brand = brandRepository
        .findByIdAndTenantIdAndDeletedAtIsNull(request.brandId(), tenantId)
        .orElseThrow(() -> new ResourceNotFoundException("Marca não encontrada"));
    product.setBrand(brand);
} else {
    product.setBrand(null);
}
```

**Ao retornar produto:**
- Incluir dados completos da marca (se houver) no `ProductResponse`
- Usar mapper para converter `Brand` em `BrandResponse`

## Database Schema

### Migration 1: Criar tabela brands

```sql
-- File: V{next_version}__create_brands_table.sql

CREATE TABLE brands (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    logo_url VARCHAR(500),
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_brands_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id),

    CONSTRAINT uk_brands_tenant_name
        UNIQUE (tenant_id, name)
);

-- Índices para performance
CREATE INDEX idx_brands_tenant_id ON brands(tenant_id);
CREATE INDEX idx_brands_deleted_at ON brands(deleted_at);

-- Comentários
COMMENT ON TABLE brands IS 'Marcas de produtos';
COMMENT ON COLUMN brands.name IS 'Nome da marca (único por tenant)';
COMMENT ON COLUMN brands.logo_url IS 'URL do logo da marca (opcional)';
COMMENT ON COLUMN brands.deleted_at IS 'Data de exclusão lógica';
```

### Migration 2: Adicionar brand_id em products

```sql
-- File: V{next_version}__add_brand_to_products.sql

ALTER TABLE products
    ADD COLUMN brand_id BIGINT;

ALTER TABLE products
    ADD CONSTRAINT fk_products_brand
        FOREIGN KEY (brand_id) REFERENCES brands(id);

CREATE INDEX idx_products_brand_id ON products(brand_id);

COMMENT ON COLUMN products.brand_id IS 'Marca do produto (opcional)';
```

**Observações importantes:**
- Foreign key **sem CASCADE DELETE** (proteção adicional contra deleção acidental)
- Índices para otimizar queries filtradas por tenant e brand
- Campo `brand_id` é nullable (marca opcional)

## Regras de Negócio

### RN001: Nome único por tenant
- Não é permitido criar duas marcas com o mesmo nome no mesmo tenant
- Validação feita via constraint de banco de dados + validação em service

### RN002: Proteção contra deleção
- Não é possível deletar uma marca que possui produtos vinculados
- Sistema deve lançar exceção com mensagem clara ao usuário
- Validação obrigatória antes do soft delete

### RN003: Marca opcional no produto
- Produtos podem existir sem marca (útil para produtos genéricos)
- Se `brandId` for informado, deve validar existência da marca

### RN004: Soft delete
- Marcas deletadas permanecem no banco com `deletedAt` preenchido
- Queries devem sempre filtrar `deletedAt IS NULL`
- Mantém integridade referencial e histórico

### RN005: Multi-tenancy
- Todas as operações devem respeitar o tenant do usuário logado
- Marcas de um tenant não são visíveis para outros tenants

## Testes

### Testes Unitários (Service)
- ✓ Criar marca com sucesso
- ✓ Criar marca com nome duplicado (deve falhar)
- ✓ Atualizar marca com sucesso
- ✓ Atualizar marca com nome duplicado (deve falhar)
- ✓ Deletar marca sem produtos vinculados
- ✓ Deletar marca com produtos vinculados (deve falhar)
- ✓ Listar apenas marcas não deletadas
- ✓ Buscar marca deletada (deve falhar)

### Testes de Integração (Controller)
- ✓ POST /api/brands - criar marca válida
- ✓ POST /api/brands - criar marca com nome duplicado (409)
- ✓ GET /api/brands - listar marcas
- ✓ GET /api/brands/{id} - buscar marca existente
- ✓ GET /api/brands/{id} - buscar marca não existente (404)
- ✓ PUT /api/brands/{id} - atualizar marca
- ✓ DELETE /api/brands/{id} - deletar marca sem produtos
- ✓ DELETE /api/brands/{id} - deletar marca com produtos (400)

### Testes de Integração (Product + Brand)
- ✓ Criar produto com marca válida
- ✓ Criar produto com marca inválida (404)
- ✓ Criar produto sem marca (null)
- ✓ Atualizar produto alterando a marca
- ✓ Retornar produto com dados da marca incluídos

## Considerações de Implementação

### Performance
- Uso de `FetchType.LAZY` no relacionamento Product → Brand
- Índices em colunas frequentemente consultadas
- DTOs para evitar over-fetching

### Segurança
- Validação de tenant em todas as operações
- Constraints de banco de dados como camada adicional
- Validação de entrada com Bean Validation

### Manutenibilidade
- Seguir padrão existente no projeto (Category como referência)
- Código reutilizável e bem documentado
- Mensagens de erro claras e específicas

### Extensibilidade Futura
- Campo `logoUrl` permite adicionar logos sem alterar schema
- Possibilidade de adicionar mais campos se necessário (descrição, país, etc.)
- Estrutura permite adicionar relacionamentos adicionais

## Ordem de Implementação

1. **Migration:** Criar tabela `brands`
2. **Migration:** Adicionar coluna `brand_id` em `products`
3. **Entity:** Criar classe `Brand`
4. **Entity:** Adicionar campo `brand` em `Product`
5. **Repository:** Criar `BrandRepository`
6. **Repository:** Adicionar método em `ProductRepository`
7. **DTO:** Criar `BrandRequest` e `BrandResponse`
8. **DTO:** Adicionar campos em `ProductRequest` e `ProductResponse`
9. **Service:** Criar `BrandService`
10. **Service:** Atualizar `ProductService` para validar brand
11. **Controller:** Criar `BrandController`
12. **Tests:** Criar testes de integração para `BrandController`
13. **Tests:** Atualizar testes de `ProductController`

## Aprovação

✅ Design aprovado e validado em 2025-12-28
