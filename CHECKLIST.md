# ✅ Checklist de Verificação - Sistema de Autenticação

Use este checklist para verificar se tudo está funcionando corretamente.

## 📋 Pré-requisitos

- [ ] Java 21 instalado (`java -version`)
- [ ] PostgreSQL instalado e rodando
- [ ] Banco de dados `stockshift` criado
- [ ] Usuário `stockshift` criado com permissões

## 🔧 Configuração

- [ ] `application.properties` configurado
- [ ] `jwt.secret` configurado (não usar o padrão em produção!)
- [ ] Porta 8080 disponível (ou alterada no config)
- [ ] Permissões de execução nos scripts (`chmod +x gradlew test-auth.sh`)

## 🏗️ Build

- [ ] Projeto compila sem erros (`./gradlew build -x test`)
- [ ] Sem warnings críticos
- [ ] Dependências baixadas corretamente

## 🚀 Execução

- [ ] Aplicação inicia sem erros (`./gradlew bootRun`)
- [ ] Tabelas criadas no banco (users, refresh_tokens)
- [ ] Usuários padrão criados (verificar logs)
- [ ] Aplicação responde em `http://localhost:8080`

## 🔐 Testes de Autenticação

### Login
- [ ] Login com admin funciona
  ```bash
  curl -X POST http://localhost:8080/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}'
  ```
- [ ] Login com manager funciona
- [ ] Login com seller funciona
- [ ] Login com senha errada retorna 401
- [ ] Login com usuário inexistente retorna 401

### Tokens
- [ ] Recebe accessToken no login
- [ ] Recebe refreshToken no login
- [ ] Access token tem formato JWT válido
- [ ] Refresh token é UUID válido

### Refresh Token
- [ ] Refresh token funciona
  ```bash
  curl -X POST http://localhost:8080/api/v1/auth/refresh \
    -H "Content-Type: application/json" \
    -d '{"refreshToken":"SEU_REFRESH_TOKEN"}'
  ```
- [ ] Recebe novo accessToken
- [ ] Refresh token expirado retorna erro
- [ ] Refresh token revogado retorna erro

### Logout
- [ ] Logout funciona (204 No Content)
- [ ] Refresh token fica revogado após logout
- [ ] Não consegue usar refresh token após logout

## 🛡️ Testes de Autorização

### Endpoints Públicos
- [ ] `/api/v1/test/public` acessível sem token
- [ ] `/api/v1/auth/login` acessível sem token

### Endpoints Autenticados
- [ ] `/api/v1/test/authenticated` requer token
- [ ] `/api/v1/test/authenticated` retorna 403 sem token
- [ ] `/api/v1/test/authenticated` funciona com token válido

### Controle de Roles
- [ ] Admin acessa `/api/v1/test/admin` ✅
- [ ] Manager NÃO acessa `/api/v1/test/admin` ❌ (403)
- [ ] Seller NÃO acessa `/api/v1/test/admin` ❌ (403)
- [ ] Admin acessa `/api/v1/test/manager` ✅
- [ ] Manager acessa `/api/v1/test/manager` ✅
- [ ] Seller NÃO acessa `/api/v1/test/manager` ❌ (403)

## 🧪 Testes Automatizados

- [ ] Script `./test-auth.sh` executa sem erros
- [ ] Todos os testes passam
- [ ] Respostas JSON válidas

## 📝 Validação de DTOs

- [ ] Login sem username retorna 400
- [ ] Login sem password retorna 400
- [ ] Refresh sem token retorna 400
- [ ] Mensagens de erro claras

## 🗄️ Banco de Dados

- [ ] Tabela `users` existe
- [ ] Tabela `refresh_tokens` existe
- [ ] 3 usuários criados (admin, manager, seller)
- [ ] Senhas estão hasheadas (bcrypt)
- [ ] Timestamps em UTC

Verificar no PostgreSQL:
```sql
SELECT username, email, role, active FROM users;
SELECT COUNT(*) FROM refresh_tokens;
```

## 🔍 Logs

- [ ] Logs aparecem no console
- [ ] SQL queries aparecem (em dev)
- [ ] Sem stack traces de erros inesperados
- [ ] Mensagem de inicialização dos usuários aparece

## 📊 Respostas da API

### Login Response
```json
{
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "uuid-format",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "username": "admin",
  "role": "ADMIN"
}
```
- [ ] Todos os campos presentes
- [ ] Tipos corretos
- [ ] Valores válidos

### Error Response
```json
{
  "type": "about:blank",
  "title": "Bad Credentials",
  "status": 401,
  "detail": "Invalid username or password",
  "instance": "/api/v1/auth/login",
  "timestamp": "2025-10-04T..."
}
```
- [ ] Formato RFC 7807
- [ ] Campos completos
- [ ] Status code correto

## 🔄 Ciclo Completo

Teste o fluxo completo:
1. [ ] Login → Recebe tokens
2. [ ] Usa access token → Acessa recurso protegido
3. [ ] Access token expira (ou simula) → Erro 401/403
4. [ ] Usa refresh token → Recebe novo access token
5. [ ] Usa novo access token → Acessa recurso protegido
6. [ ] Logout → Revoga refresh token
7. [ ] Tenta refresh com token revogado → Erro

## 🎯 Testes com Postman/Insomnia

- [ ] Coleção importada com sucesso
- [ ] Todas as requisições configuradas
- [ ] Consegue executar sequência de testes
- [ ] Variáveis de ambiente funcionam (opcional)

## 🚨 Testes de Segurança Básicos

- [ ] Token JWT não pode ser decodificado/alterado
- [ ] Token inválido retorna 401
- [ ] Token expirado retorna 401
- [ ] Senha não aparece nos logs
- [ ] Senha não aparece nas respostas
- [ ] CORS configurado (se necessário)

## 📱 Performance

- [ ] Login responde em < 1s
- [ ] Refresh responde em < 500ms
- [ ] Endpoints autenticados respondem em < 500ms
- [ ] Sem memory leaks visíveis

## 📚 Documentação

- [ ] README.md está atualizado
- [ ] AUTH_API.md tem exemplos funcionais
- [ ] QUICKSTART.md é seguível
- [ ] Comentários no código quando necessário

## 🎉 Checklist Final

- [ ] Todos os endpoints funcionam
- [ ] Todos os testes passam
- [ ] Sem erros no console
- [ ] Banco de dados consistente
- [ ] Documentação completa
- [ ] Pronto para desenvolvimento de features

---

## ✅ Resultado

**Data do Teste**: ___/___/_____
**Testado por**: __________________
**Status**: [ ] Aprovado [ ] Pendente [ ] Reprovado

**Observações**:
_______________________________________________
_______________________________________________
_______________________________________________

---

## 🐛 Problemas Encontrados

Se encontrar algum problema, consulte:
1. [QUICKSTART.md](QUICKSTART.md) - Seção "Problemas Comuns"
2. [AUTH_API.md](AUTH_API.md) - Documentação da API
3. Logs da aplicação
4. Status do PostgreSQL

---

**Dica**: Execute este checklist sempre que:
- Configurar o projeto pela primeira vez
- Após mudanças significativas
- Antes de um deploy
- Ao onboard novos desenvolvedores
