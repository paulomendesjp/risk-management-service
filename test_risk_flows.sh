#!/bin/bash
# Script para testar os fluxos de risk monitoring em tempo real

API_KEY="j4uFYvXXFvX0DOwSfAvn4iK4"
API_SECRET="D9gWYW6hykFkf95JwnQfj7ohuw2fy4nUhRopsB9xyHkP"
CLIENT_ID="testuser_realtime"

echo "========================================="
echo "   TESTE DE RISK MONITORING REAL-TIME   "
echo "========================================="

# Registrar usuário com limite de $50
echo -e "\n📝 Registrando usuário com limite diário de \$50..."
curl -X POST http://localhost:8081/api/users/register \
  -H "Content-Type: application/json" \
  -d "{
    \"clientId\": \"${CLIENT_ID}\",
    \"apiKey\": \"${API_KEY}\",
    \"apiSecret\": \"${API_SECRET}\",
    \"initialBalance\": 1000,
    \"dailyRisk\": {\"value\": 50, \"isAbsolute\": true},
    \"maxRisk\": {\"value\": 100, \"isAbsolute\": true}
  }"

# Aguardar WebSockets conectarem
echo -e "\n⏳ Aguardando WebSocket connections (5s)..."
sleep 5

# Verificar status inicial
echo -e "\n✅ Status inicial:"
curl -s http://localhost:8083/api/risk/test/status/${CLIENT_ID} | jq '.' 2>/dev/null || curl http://localhost:8083/api/risk/test/status/${CLIENT_ID}

# Simular perdas progressivas
echo -e "\n📉 Iniciando simulação de perdas progressivas..."

# 80% do limite - WARNING
echo -e "\n⚠️  Teste 1: Perda de \$40 (80% do limite - deve gerar WARNING)"
curl -X POST http://localhost:8083/api/risk/test/simulate-balance \
  -H "Content-Type: application/json" \
  -d "{
    \"clientId\": \"${CLIENT_ID}\",
    \"previousBalance\": 1000,
    \"newBalance\": 960
  }"
sleep 3

# 90% do limite - CRITICAL
echo -e "\n🚨 Teste 2: Perda de \$45 (90% do limite - deve gerar CRITICAL)"
curl -X POST http://localhost:8083/api/risk/test/simulate-balance \
  -H "Content-Type: application/json" \
  -d "{
    \"clientId\": \"${CLIENT_ID}\",
    \"previousBalance\": 960,
    \"newBalance\": 955
  }"
sleep 3

# 100% do limite - BREACH e BLOCK
echo -e "\n💥 Teste 3: Perda de \$50 (100% do limite - deve FECHAR POSIÇÕES e BLOQUEAR)"
curl -X POST http://localhost:8083/api/risk/test/simulate-balance \
  -H "Content-Type: application/json" \
  -d "{
    \"clientId\": \"${CLIENT_ID}\",
    \"previousBalance\": 955,
    \"newBalance\": 950
  }"
sleep 3

# Verificar status após breach
echo -e "\n🔒 Status após breach (deve estar bloqueado):"
curl -s http://localhost:8083/api/risk/test/status/${CLIENT_ID} | jq '{
  clientId: .clientId,
  dailyLoss: .dailyLoss,
  dailyBlocked: .dailyBlocked,
  canTrade: .canTrade,
  riskStatus: .riskStatus
}' 2>/dev/null || curl http://localhost:8083/api/risk/test/status/${CLIENT_ID}

echo -e "\n✅ Teste concluído!"
echo "Para ver os logs do sistema, execute:"
echo "docker logs risk-monitoring-service --tail 50"