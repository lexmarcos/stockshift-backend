---
active: true
iteration: 1
max_iterations: 0
completion_promise: null
started_at: "2026-01-21T15:57:50Z"
---

# Tarefa: Consertar Testes de Integração Quebrados

  ## Objetivo
  Consertar os testes de integração quebrados no projeto, um por um, de forma iterativa.

  ## Processo (Self-Correction Pattern)

  1. **Executar os testes** para identificar quais estão falhando:
     bash
     ./gradlew test --tests *IntegrationTest* 2>&1 | head -100

  2. Identificar o PRIMEIRO teste quebrado e analisar a causa raiz do erro
  3. Debugar e corrigir o problema identificado
  4. Verificar se o fix funciona executando APENAS o teste corrigido:
  ./gradlew test --tests NomeDaClasseTest.nomeDoMetodo
  5. Repetir para o próximo teste quebrado

  Regras

  - Corrigir UM teste por vez - não tente consertar todos de uma vez
  - Após cada correção, rodar o teste específico para confirmar que passou
  - Se um teste falhar após a correção, debugar e corrigir antes de seguir
  - Não alterar comportamento do código de produção a menos que o teste esteja errado
  - Todos os testes existentes devem continuar passando

  Critério de Conclusão

  - Executar ./gradlew test e todos os testes passarem
  - Output COMPLETE quando todos os testes estiverem passando

  Começar

  Execute os testes agora para identificar o primeiro teste quebrado.
