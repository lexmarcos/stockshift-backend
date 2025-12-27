# StockShift - Sistema de Gerenciamento de Estoque

API REST para gerenciamento de estoque multi-tenant com Spring Boot, PostgreSQL e JWT.

## 🚀 Tecnologias

- **Java 17**
- **Spring Boot 4.0.1**
- **PostgreSQL 16**
- **Flyway** (Migrações de banco de dados)
- **JWT** (Autenticação)
- **Lombok** (Redução de boilerplate)
- **OpenAPI/Swagger** (Documentação da API)

## 📋 Pré-requisitos

- Java 17 ou superior
- Docker e Docker Compose
- Gradle (incluído via wrapper)

## 🐳 Iniciando o Banco de Dados

### 1. Subir apenas o PostgreSQL

```bash
docker-compose up -d postgres
```

### 2. Subir PostgreSQL + pgAdmin (interface web)

```bash
docker-compose --profile tools up -d
```

**Acessar pgAdmin:**
- URL: http://localhost:5050
- Email: admin@stockshift.com
- Senha: admin

**Conectar ao PostgreSQL via pgAdmin:**
- Host: postgres
- Port: 5432
- Database: stockshift
- Username: postgres
- Password: postgres

### 3. Parar os serviços

```bash
docker-compose down
```

### 4. Parar e remover volumes (apaga dados)

```bash
docker-compose down -v
```

## 🗄️ Migrações do Banco de Dados

O projeto usa Flyway para gerenciar as migrações. Execute:

```bash
./gradlew flywayMigrate
```

**Migrações disponíveis:**
- V1: Tenants e Users (multi-tenancy base)
- V2: Roles e Permissions (RBAC)
- V3: Refresh Tokens (autenticação)
- V4: Categories e Products (produtos flexíveis com JSONB)
- V5: Warehouses e Batches (estoques e lotes)
- V6: Stock Movements (movimentações com estados)
- V7: Views (agregações)
- V8: Default Permissions (permissões iniciais)

## ▶️ Executando a Aplicação

### Modo desenvolvimento

```bash
./gradlew bootRun
```

### Build e execução

```bash
./gradlew clean build
java -jar build/libs/stockshift-0.0.1-SNAPSHOT.jar
```

## 📚 Documentação da API

Após iniciar a aplicação, acesse:

**Swagger UI:** http://localhost:8080/swagger-ui.html

## 🔧 Configuração

### Variáveis de Ambiente (Desenvolvimento)

```bash
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=stockshift
DB_USER=postgres
DB_PASSWORD=postgres

# JWT
JWT_SECRET=your-secret-key-change-in-production
JWT_ACCESS_EXPIRATION=900000     # 15 minutos
JWT_REFRESH_EXPIRATION=604800000 # 7 dias

# CORS
ALLOWED_ORIGINS=http://localhost:3000
```

## 🏗️ Estrutura do Projeto

```
src/main/java/br/com/stockshift/
├── config/          # Configurações (JWT, Security, etc)
├── controller/      # REST Controllers
├── dto/             # Data Transfer Objects
│   ├── request/     # Request DTOs
│   └── response/    # Response DTOs
├── exception/       # Exception Handlers
├── model/
│   ├── entity/      # Entidades JPA
│   └── enums/       # Enumerações
├── repository/      # Repositórios Spring Data
├── security/        # JWT, UserDetails, Filters
├── service/         # Lógica de negócio
└── util/            # Utilitários

src/main/resources/
├── application.yml           # Configuração principal
├── application-dev.yml       # Perfil desenvolvimento
├── application-test.yml      # Perfil testes
└── db/migration/             # Migrações Flyway
```

## 🎯 Funcionalidades Principais

### Multi-Tenancy
- Isolamento completo de dados por empresário (tenant)
- Filtro automático de queries por tenant_id

### Autenticação e Autorização
- JWT + Refresh Token
- RBAC granular (Resource/Action/Scope)
- Roles customizáveis por tenant

### Produtos
- Atributos flexíveis (JSONB) para diversos tipos de produtos
- Suporte a kits (produtos compostos)
- Categorias hierárquicas

### Gestão de Estoque
- Múltiplos warehouses por tenant
- Controle de lotes com validade (FEFO)
- Rastreamento de movimentações com estados
- Soft deletes para auditoria

### Movimentações
- PURCHASE (Compra)
- SALE (Venda)
- TRANSFER (Transferência entre estoques)
- ADJUSTMENT (Ajuste de inventário)
- RETURN (Devolução)

## 🧪 Testes

```bash
# Executar testes
./gradlew test

# Executar com cobertura
./gradlew test jacocoTestReport
```

## 📝 Endpoints Principais (Planejados)

```
POST   /api/auth/login          # Login
POST   /api/auth/refresh        # Renovar token
POST   /api/auth/logout         # Logout

GET    /api/products            # Listar produtos
POST   /api/products            # Criar produto
GET    /api/products/:id        # Detalhes do produto

POST   /api/movements/purchase  # Registrar compra
POST   /api/movements/sale      # Registrar venda
POST   /api/movements/transfer  # Criar transferência

GET    /api/reports/stock-summary        # Resumo de estoque
GET    /api/reports/expiring-products    # Produtos vencendo
```

## 🤝 Contribuindo

1. Fork o projeto
2. Crie uma branch para sua feature (`git checkout -b feature/AmazingFeature`)
3. Commit suas mudanças (`git commit -m 'feat: Add some AmazingFeature'`)
4. Push para a branch (`git push origin feature/AmazingFeature`)
5. Abra um Pull Request

## 📄 Licença

Este projeto está sob a licença MIT.

## ✨ Autor

Desenvolvido para pequenos empresários gerenciarem estoques em múltiplas lojas.

---

**Status do Projeto:** 🚧 Em desenvolvimento - Fundação completa (Database, Entities, Security)
