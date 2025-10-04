# Stockshift Backend

Sistema de gerenciamento de estoque desenvolvido com Spring Boot seguindo princípios de Domain-Driven Design (DDD-lite) e arquitetura hexagonal.

## 🏗️ Stack Tecnológica

- **Java**: 21
- **Spring Boot**: 3.5.6
- **Spring Security**: JWT Authentication
- **Spring Data JPA**: Persistência com PostgreSQL
- **Lombok**: Redução de boilerplate
- **Gradle**: Build tool
- **PostgreSQL**: Database

## 📋 Pré-requisitos

- Java 21
- PostgreSQL 12+
- Gradle 8+ (ou use o wrapper incluído `./gradlew`)

## ⚙️ Configuração

### 1. Banco de Dados

Crie o banco de dados PostgreSQL:

```sql
CREATE DATABASE stockshift;
CREATE USER stockshift WITH PASSWORD 'stockshift';
GRANT ALL PRIVILEGES ON DATABASE stockshift TO stockshift;
```

### 2. Configuração da Aplicação

As configurações estão em `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/stockshift
spring.datasource.username=stockshift
spring.datasource.password=stockshift

jwt.secret=your-256-bit-secret-key-change-this-in-production
jwt.access-token-expiration=3600000  # 1 hour
jwt.refresh-token-expiration=86400000  # 24 hours
```

⚠️ **IMPORTANTE**: Altere o `jwt.secret` antes de colocar em produção!

## 🚀 Executando a Aplicação

### Via Gradle Wrapper (recomendado)
```bash
./gradlew bootRun
```

### Via Gradle instalado
```bash
gradle bootRun
```

### Build e Run do JAR
```bash
./gradlew build
java -jar build/libs/backend-0.0.1-SNAPSHOT.jar
```

A aplicação estará disponível em: `http://localhost:8080`

## 🔐 Autenticação

A API usa JWT (JSON Web Tokens) para autenticação. Consulte a documentação completa em:
- [AUTH_API.md](AUTH_API.md) - Guia de uso da API
- [IMPLEMENTACAO_AUTH.md](IMPLEMENTACAO_AUTH.md) - Detalhes da implementação

### Usuários Padrão

| Username | Password    | Role    |
|----------|-------------|---------|
| admin    | admin123    | ADMIN   |
| manager  | manager123  | MANAGER |
| seller   | seller123   | SELLER  |

### Quick Start

1. **Login**:
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

2. **Usar o token nas requisições**:
```bash
curl -X GET http://localhost:8080/api/v1/test/authenticated \
  -H "Authorization: Bearer {seu_access_token}"
```

## 🧪 Testes

### Executar testes automatizados
```bash
./test-auth.sh
```

### Usar coleção Postman/Insomnia
Importe o arquivo `postman-collection.json`

## 📁 Estrutura do Projeto

```
src/main/java/com/stockshift/backend/
├── api/                    # Controllers e DTOs
│   ├── controller/
│   ├── dto/
│   └── exception/
├── application/            # Use cases e serviços
│   └── service/
├── domain/                 # Entidades e lógica de negócio
│   └── user/
└── infrastructure/         # Configurações e adaptadores
    ├── config/
    ├── repository/
    └── security/
```

## 📚 Endpoints Disponíveis

### Autenticação
- `POST /api/v1/auth/login` - Login
- `POST /api/v1/auth/refresh` - Renovar access token
- `POST /api/v1/auth/logout` - Logout

### Teste (Debug)
- `GET /api/v1/test/public` - Endpoint público
- `GET /api/v1/test/authenticated` - Requer autenticação
- `GET /api/v1/test/admin` - Requer role ADMIN
- `GET /api/v1/test/manager` - Requer role ADMIN ou MANAGER

## 🏗️ Arquitetura

Este projeto segue os princípios de:

- **Domain-Driven Design (DDD)**: Separação clara entre domínio, aplicação e infraestrutura
- **Arquitetura Hexagonal**: Isolamento da lógica de negócio
- **SOLID**: Princípios de design orientado a objetos
- **Clean Code**: Código limpo e manutenível

Consulte [.github/copilot-instructions.md](.github/copilot-instructions.md) para detalhes completos da arquitetura.

## 🔧 Build

```bash
# Compilar
./gradlew build

# Compilar sem testes
./gradlew build -x test

# Limpar e compilar
./gradlew clean build

# Executar testes
./gradlew test
```

## 📝 Logs

Os logs são configurados para:
- Mostrar SQL formatado (desenvolvimento)
- Timezone UTC
- Incluir mensagens de erro e binding errors

## 🤝 Contribuindo

1. Fork o projeto
2. Crie uma branch para sua feature (`git checkout -b feature/AmazingFeature`)
3. Commit suas mudanças (`git commit -m 'Add some AmazingFeature'`)
4. Push para a branch (`git push origin feature/AmazingFeature`)
5. Abra um Pull Request

## 📄 Licença

Este projeto é privado e proprietário.

## 👥 Autores

- Desenvolvimento inicial - Stockshift Team

## 🐛 Reportando Bugs

Encontrou um bug? Abra uma issue com:
- Descrição detalhada
- Steps to reproduce
- Comportamento esperado vs atual
- Screenshots (se aplicável)
- Versão do Java e OS

## 📞 Suporte

Para suporte, entre em contato através do repositório do projeto.
