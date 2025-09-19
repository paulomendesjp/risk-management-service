# 📊 Guia de Teste do Webhook - Sistema de Trading

## 🎯 Entendendo o Fluxo

### O que é TradingView?
- **TradingView.com** = Plataforma de análise técnica (EXTERNA)
- Permite criar estratégias que enviam sinais via webhook
- **NÃO é parte do seu sistema** - é quem ENVIA sinais

### Como Funciona?

```
TradingView/Script Python
         ↓
    HTTP POST
         ↓
Seu Webhook (/webhook/tradingview)
         ↓
    Processa e Roteia
         ↓
Kraken API ou Architect API
```

## 🚀 Como Testar SEM TradingView

### 1. Iniciar os Serviços

```bash
# Terminal 1 - MongoDB e RabbitMQ
docker-compose up -d mongodb rabbitmq

# Terminal 2 - API Gateway (porta 8080)
cd api-gateway
mvn spring-boot:run

# Terminal 3 - Kraken Service (porta 8086)
cd kraken-service
mvn spring-boot:run

# Terminal 4 - Risk Monitoring (opcional)
cd risk-monitoring-service
mvn spring-boot:run
```

### 2. Testar com Scripts Prontos

#### Opção A - Teste Rápido
```bash
./test_webhook.sh
# Escolha opção 1 para teste simples
```

#### Opção B - Teste Completo Python
```bash
python3 test_kraken_webhook.py
```

#### Opção C - Simulação Contínua
```bash
python3 simulate_trading.py
# Simula sinais de trading a cada 30 segundos
```

### 3. Teste Manual com CURL

```bash
# Ordem de compra para Kraken
curl -X POST http://localhost:8080/webhook/tradingview \
  -H "Content-Type: application/json" \
  -d '{
    "clientId": "1211123",
    "symbol": "ETH/USD",
    "action": "buy",
    "orderQty": 0.01,
    "exchange": "KRAKEN",
    "strategy": "manual-test"
  }'

# Ordem para Architect (compatibilidade)
curl -X POST http://localhost:8080/webhook/tradingview \
  -H "Content-Type: application/json" \
  -d '{
    "clientId": "test-client",
    "symbol": "AAPL",
    "action": "buy",
    "orderQty": 100,
    "exchange": "ARCHITECT"
  }'
```

## 🌐 Como Configurar com TradingView Real

### Pré-requisitos:
1. Conta no TradingView.com (Pro, Pro+ ou Premium para webhooks)
2. Seu servidor acessível na internet (não localhost)

### Passos:

1. **Expor seu servidor na internet:**
   ```bash
   # Opção 1 - Usar ngrok (para testes)
   ngrok http 8080
   # Copie a URL gerada (ex: https://abc123.ngrok.io)
   ```

   ```bash
   # Opção 2 - Deploy em servidor cloud
   # Configure nginx/apache para proxy na porta 80
   ```

2. **No TradingView:**
   - Crie/abra um gráfico
   - Adicione indicador ou estratégia
   - Clique em "Alertas" (ícone de relógio)
   - Configure:
     - Condição: Sua estratégia
     - Ações: Webhook URL
     - URL: `https://seu-servidor.com/webhook/tradingview`
     - Mensagem:
     ```json
     {
       "clientId": "{{strategy.order.id}}",
       "symbol": "{{ticker}}",
       "action": "{{strategy.order.action}}",
       "orderQty": {{strategy.order.contracts}},
       "exchange": "KRAKEN",
       "strategy": "{{strategy.name}}"
     }
     ```

## 📝 Formato do Webhook

### Campos Obrigatórios:
- `clientId`: Identificador do cliente
- `symbol`: Par de trading (ETH/USD, BTC/USD)
- `action`: "buy" ou "sell"
- `orderQty`: Quantidade

### Campos Opcionais:
- `exchange`: "KRAKEN" ou "ARCHITECT" (default: ARCHITECT)
- `strategy`: Nome da estratégia
- `maxriskperday%`: Limite de risco diário
- `stopLoss%`: Stop loss percentual
- `pyramid`: true/false (permite múltiplas ordens)
- `inverse`: true/false (fecha e inverte posição)

## 🔍 Verificar se Está Funcionando

### 1. Verificar Logs

```bash
# Ver logs do API Gateway
tail -f api-gateway/logs/api-gateway.log

# Ver logs do Kraken Service
tail -f kraken-service/logs/kraken-service.log
```

### 2. Verificar Endpoints de Saúde

```bash
# API Gateway
curl http://localhost:8080/actuator/health

# Kraken Service
curl http://localhost:8086/api/kraken/health
```

### 3. Respostas Esperadas

**Sucesso:**
```json
{
  "success": true,
  "orderId": "KRK-123456",
  "symbol": "ETH/USD",
  "side": "buy",
  "quantity": 0.01
}
```

**Erro:**
```json
{
  "success": false,
  "error": "INVALID_REQUEST",
  "message": "Missing required field: symbol"
}
```

## ⚠️ Troubleshooting

### Problema: "Connection refused"
**Solução:** Verifique se os serviços estão rodando

### Problema: "404 Not Found"
**Solução:** URL incorreta, use `/webhook/tradingview`

### Problema: "Invalid webhook request"
**Solução:** Verifique campos obrigatórios no JSON

### Problema: "Client credentials not found"
**Solução:** Cliente precisa estar cadastrado no User Service

## 📊 Fluxo Completo do Sistema

```
1. Sinal de Trading (TradingView/Script)
   ↓
2. Webhook POST → API Gateway (porta 80/8080)
   ↓
3. Gateway valida e roteia baseado em "exchange"
   ↓
4. Kraken Service ou Architect Service
   ↓
5. Executa ordem na exchange via API
   ↓
6. Risk Monitoring verifica limites
   ↓
7. Retorna confirmação ao webhook
```

## 🎯 Resumo

- **Você NÃO precisa do TradingView** para testar
- Use os scripts Python fornecidos
- TradingView é apenas UMA das formas de enviar sinais
- Qualquer sistema pode enviar POST para seu webhook
- Kraken NÃO envia webhooks - você consulta a API deles