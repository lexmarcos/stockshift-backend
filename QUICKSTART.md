# 🚀 Guia Rápido de Início

Este guia vai te ajudar a colocar a API rodando em menos de 5 minutos!

## Passo 1: Banco de Dados

```bash
# Conecte ao PostgreSQL
sudo -u postgres psql

# Crie o banco e usuário
CREATE DATABASE stockshift;
CREATE USER stockshift WITH PASSWORD 'stockshift';
GRANT ALL PRIVILEGES ON DATABASE stockshift TO stockshift;
\q
```

## Passo 2: Executar a Aplicação

```bash
# Na raiz do projeto
./gradlew bootRun
```

Aguarde a mensagem: `Started BackendApplication in X seconds`

## Passo 3: Testar

### Opção A: Script Automatizado (Recomendado)
```bash
./test-auth.sh
```

### Opção B: Teste Manual
```bash
# 1. Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# 2. Copie o "accessToken" da resposta

# 3. Teste um endpoint protegido
curl -X GET http://localhost:8080/api/v1/test/authenticated \
  -H "Authorization: Bearer SEU_ACCESS_TOKEN_AQUI"
```

## ✅ Pronto!

Se você recebeu uma resposta JSON com sucesso, está tudo funcionando!

### Próximos Passos:
- 📖 Leia [AUTH_API.md](AUTH_API.md) para conhecer todos os endpoints
- 🔧 Configure seu cliente REST favorito com [postman-collection.json](postman-collection.json)
- 🏗️ Consulte [.github/copilot-instructions.md](.github/copilot-instructions.md) para entender a arquitetura

## ⚠️ Problemas Comuns

### Erro: "Connection refused"
- Verifique se o PostgreSQL está rodando: `sudo systemctl status postgresql`

### Erro: "FATAL: database 'stockshift' does not exist"
- Execute o Passo 1 novamente

### Erro: "Permission denied" no gradlew
```bash
chmod +x gradlew
chmod +x test-auth.sh
```

### Porta 8080 já em uso
- Mate o processo: `sudo lsof -t -i:8080 | xargs kill -9`
- Ou altere a porta em `application.properties`: `server.port=8081`

## 🎯 Testando Diferentes Roles

```bash
# Admin (acesso total)
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# Manager (acesso gerencial)
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"manager","password":"manager123"}'

# Seller (acesso limitado)
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"seller","password":"seller123"}'
```

## 📱 Testando no Postman/Insomnia

1. Importe o arquivo `postman-collection.json`
2. Execute a requisição "1. Login - Admin"
3. Copie o `accessToken` da resposta
4. Cole no campo `Authorization` das outras requisições (substitua `PASTE_YOUR_ACCESS_TOKEN_HERE`)
5. Execute as outras requisições

## 🎉 Tudo Pronto!

Agora você pode começar a desenvolver novos recursos ou integrar com seu frontend!
