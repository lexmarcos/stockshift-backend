# Guia de Autenticação para Frontend

## Visão Geral

A API StockShift utiliza **cookies HTTP-only** para armazenar tokens de autenticação. Isso aumenta a segurança protegendo contra ataques XSS, já que o JavaScript não pode acessar os tokens.

## Como Funciona

### 1. Login

**Endpoint:** `POST /api/auth/login`

**Request:**
```json
{
  "email": "usuario@example.com",
  "password": "senha123"
}
```

**Response:** 200 OK
```json
{
  "success": true,
  "data": {
    "userId": "uuid",
    "email": "usuario@example.com",
    "fullName": "João Silva",
    "tokenType": "Bearer",
    "expiresIn": 900000
  }
}
```

**Cookies Recebidos:**
- `accessToken` - Token JWT válido por 15 minutos
- `refreshToken` - Token de renovação válido por 7 dias

⚠️ **Importante:** Os tokens NÃO aparecem no JSON da resposta. Eles são enviados automaticamente via cookies HTTP-only.

### 2. Requisições Autenticadas

Para todas as requisições autenticadas, o browser **automaticamente** envia os cookies. Você NÃO precisa fazer nada manualmente.

**Configuração Necessária:**

```javascript
// Fetch API
fetch('http://localhost:8080/api/products', {
  method: 'GET',
  credentials: 'include',  // ← OBRIGATÓRIO para enviar cookies
  headers: {
    'Content-Type': 'application/json'
  }
});

// Axios
import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8080/api',
  withCredentials: true  // ← OBRIGATÓRIO para enviar cookies
});

// Exemplo de uso
const response = await api.get('/products');
```

### 3. Refresh de Tokens

Quando o `accessToken` expira (15 minutos), você precisa renovar os tokens.

**Endpoint:** `POST /api/auth/refresh`

**Request:** Não precisa de corpo! O refresh token é lido automaticamente do cookie.

```javascript
// Fetch
const response = await fetch('http://localhost:8080/api/auth/refresh', {
  method: 'POST',
  credentials: 'include'  // ← OBRIGATÓRIO
});

// Axios
const response = await api.post('/auth/refresh');
```

**Response:** 200 OK
```json
{
  "success": true,
  "data": "Tokens refreshed successfully"
}
```

**Cookies Recebidos:**
- Novo `accessToken` (válido por mais 15 min)
- Novo `refreshToken` (válido por mais 7 dias a partir de agora)

⚠️ **Token Deslizante:** A cada refresh, o refresh token é renovado. Enquanto você usar a aplicação, o refresh token continua válido.

### 4. Logout

**Endpoint:** `POST /api/auth/logout`

**Request:** Não precisa de corpo!

```javascript
const response = await fetch('http://localhost:8080/api/auth/logout', {
  method: 'POST',
  credentials: 'include'
});
```

**Response:** 200 OK
```json
{
  "success": true,
  "data": "Logged out successfully"
}
```

**Cookies Removidos:**
- `accessToken` (Max-Age=0)
- `refreshToken` (Max-Age=0)

---

## Tratamento de Erros

### Token Expirado (401)

Quando o `accessToken` expira, você receberá:

```json
{
  "success": false,
  "error": "UNAUTHORIZED",
  "message": "Token expired"
}
```

**O que fazer:**
1. Chamar `/api/auth/refresh` automaticamente
2. Se o refresh falhar (401), redirecionar para login

### Refresh Token Expirado (401)

Se o refresh token expirou (7 dias sem uso):

```json
{
  "success": false,
  "error": "REFRESH_TOKEN_EXPIRED",
  "message": "Refresh token has expired"
}
```

**O que fazer:**
- Redirecionar usuário para tela de login
- Limpar estado da aplicação

---

## Exemplo de Implementação Completa

### React com Axios

```javascript
import axios from 'axios';
import { useNavigate } from 'react-router-dom';

// Criar instância do axios
const api = axios.create({
  baseURL: 'http://localhost:8080/api',
  withCredentials: true  // Habilita cookies
});

// Estado para controlar refresh em andamento
let isRefreshing = false;
let failedQueue = [];

const processQueue = (error, token = null) => {
  failedQueue.forEach(prom => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });
  
  failedQueue = [];
};

// Interceptor para lidar com token expirado
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    // Se for 401 e ainda não tentou refresh
    if (error.response?.status === 401 && !originalRequest._retry) {
      
      if (isRefreshing) {
        // Se já está refreshing, adiciona à fila
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        }).then(() => {
          return api(originalRequest);
        }).catch(err => {
          return Promise.reject(err);
        });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        // Tenta refresh
        await api.post('/auth/refresh');
        processQueue(null);
        isRefreshing = false;
        
        // Retry requisição original
        return api(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError);
        isRefreshing = false;
        
        // Refresh falhou - redirecionar para login
        window.location.href = '/login';
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  }
);

export default api;
```

### Uso no Componente

```javascript
import React, { useEffect, useState } from 'react';
import api from './api';

function ProductList() {
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchProducts();
  }, []);

  const fetchProducts = async () => {
    try {
      const response = await api.get('/products');
      setProducts(response.data.data);
    } catch (error) {
      console.error('Error fetching products:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleLogin = async (email, password) => {
    try {
      const response = await api.post('/auth/login', { email, password });
      // Cookies são salvos automaticamente!
      console.log('Login successful:', response.data.data);
      // Redirecionar para dashboard
    } catch (error) {
      console.error('Login failed:', error);
    }
  };

  const handleLogout = async () => {
    try {
      await api.post('/auth/logout');
      // Cookies são removidos automaticamente!
      // Redirecionar para login
      window.location.href = '/login';
    } catch (error) {
      console.error('Logout failed:', error);
    }
  };

  return (
    <div>
      {/* Seu componente aqui */}
    </div>
  );
}
```

---

## Detalhes dos Cookies

### Atributos dos Cookies (Desenvolvimento)

| Cookie | Valor | HttpOnly | Secure | SameSite | Path | Max-Age |
|--------|-------|----------|--------|----------|------|---------|
| `accessToken` | JWT string | ✅ true | ❌ false | Lax | /api | 900s (15min) |
| `refreshToken` | UUID string | ✅ true | ❌ false | Lax | /api | 604800s (7d) |

### Atributos dos Cookies (Produção)

| Cookie | Valor | HttpOnly | Secure | SameSite | Path | Max-Age |
|--------|-------|----------|--------|----------|------|---------|
| `accessToken` | JWT string | ✅ true | ✅ true | None | /api | 900s (15min) |
| `refreshToken` | UUID string | ✅ true | ✅ true | None | /api | 604800s (7d) |

**Explicação:**
- **HttpOnly:** JavaScript não pode acessar (proteção XSS)
- **Secure:** Apenas HTTPS em produção
- **SameSite:** 
  - `Lax` em dev (permite localhost)
  - `None` em prod (permite CORS cross-origin)
- **Path:** `/api` - cookies só enviados para endpoints da API
- **Max-Age:** Tempo de vida em segundos

---

## Configuração CORS

O backend está configurado para aceitar credenciais (cookies) via CORS.

**Origens permitidas (configurável):**
- Desenvolvimento: `http://localhost:3000`
- Produção: Configure via variável de ambiente `ALLOWED_ORIGINS`

⚠️ **Importante:** Seu frontend DEVE estar em uma das origens permitidas, caso contrário os cookies não funcionarão.

---

## Verificação de Autenticação

Como o frontend não tem acesso aos tokens, você pode verificar se o usuário está autenticado de duas formas:

### 1. Endpoint /me (Recomendado)

Se você criar um endpoint `GET /api/auth/me`, pode verificar:

```javascript
const checkAuth = async () => {
  try {
    const response = await api.get('/auth/me');
    return response.data.data; // Dados do usuário
  } catch (error) {
    return null; // Não autenticado
  }
};
```

### 2. Verificar Resposta 401

Sempre que receber 401, considera usuário não autenticado:

```javascript
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // Usuário não autenticado
      store.dispatch(setUser(null));
    }
    return Promise.reject(error);
  }
);
```

---

## Checklist de Implementação

- [ ] Configurar `credentials: 'include'` em todas as requisições (Fetch)
- [ ] Configurar `withCredentials: true` no axios
- [ ] Implementar interceptor para refresh automático
- [ ] Tratar erro 401 redirecionando para login
- [ ] Implementar função de logout
- [ ] Testar em diferentes browsers
- [ ] Verificar CORS está configurado corretamente
- [ ] Em produção, garantir HTTPS

---

## Troubleshooting

### Cookies não estão sendo salvos

**Problema:** Após login, os cookies não aparecem no browser.

**Soluções:**
1. Verificar se `credentials: 'include'` / `withCredentials: true` está configurado
2. Verificar se o backend aceita a origem do frontend (CORS)
3. Verificar se está usando HTTPS em produção (obrigatório com SameSite=None)
4. Verificar no DevTools → Application → Cookies se os cookies existem

### Cookies não estão sendo enviados nas requisições

**Problema:** Requisições retornam 401, mas os cookies existem.

**Soluções:**
1. Verificar se `credentials: 'include'` / `withCredentials: true` está em TODAS as requisições
2. Verificar se o Path do cookie está correto (`/api`)
3. Verificar se o domínio está correto
4. Verificar no DevTools → Network → Request Headers se os cookies estão sendo enviados

### CORS Error

**Problema:** `Access-Control-Allow-Origin error`

**Soluções:**
1. Verificar variável de ambiente `ALLOWED_ORIGINS` no backend
2. Adicionar origem do frontend (ex: `http://localhost:3000`)
3. Reiniciar backend após mudança
4. Em produção, usar domínios corretos (não usar wildcards com credentials)

---

## Resumo Rápido

```javascript
// 1. Configurar API com credentials
const api = axios.create({
  baseURL: 'http://localhost:8080/api',
  withCredentials: true
});

// 2. Login
await api.post('/auth/login', { email, password });
// Cookies salvos automaticamente ✅

// 3. Fazer requisições
await api.get('/products');
// Cookies enviados automaticamente ✅

// 4. Refresh quando token expirar
await api.post('/auth/refresh');
// Novos cookies salvos automaticamente ✅

// 5. Logout
await api.post('/auth/logout');
// Cookies removidos automaticamente ✅
```

**É isso! O browser cuida de tudo relacionado aos cookies. Você só precisa configurar `credentials: 'include'` / `withCredentials: true`. 🎉**
