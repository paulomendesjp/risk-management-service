# Kraken Service

## Overview
Kraken Futures Trading Service integrated with the Risk Management System. This service provides integration with Kraken Futures API for trading ETH and BTC futures contracts with comprehensive risk management.

## Features
- **Kraken Futures API Integration**
  - Real-time balance monitoring
  - Order placement (market/limit)
  - Position management
  - Stop-loss orders

- **Risk Management**
  - Daily risk limits (percentage-based)
  - Max risk limits
  - Automatic position closure on breach
  - UTC-based daily reset

- **Trading Features**
  - Pyramid trading support
  - Inverse trading logic
  - Stop-loss percentage
  - Strategy tracking

## API Endpoints

### Webhook Processing
```
POST /api/kraken/webhook
Headers: X-API-Key, X-API-Secret
Body: {
  "clientId": "1211123",
  "symbol": "ETH/USD",
  "strategy": "test-model1",
  "maxriskperday%": "2",
  "action": "buy",
  "orderQty": 0.01,
  "inverse": false,
  "pyramid": false,
  "stopLoss%": 0.5,
  "exchange": "KRAKEN"
}
```

### Balance Check
```
GET /api/kraken/balance/{clientId}
Headers: X-API-Key, X-API-Secret
```

### Get Positions
```
GET /api/kraken/positions
Headers: X-API-Key, X-API-Secret
```

### Close All Positions
```
POST /api/kraken/positions/close-all/{clientId}
Headers: X-API-Key, X-API-Secret
```

## Configuration

### Application Properties
```yaml
kraken:
  api:
    base-url: https://futures.kraken.com
    demo-url: https://demo-futures.kraken.com
    use-demo: true  # Use demo environment for testing
```

### Test Credentials (Provided)
```
Public Key: /tjRZL5FBJ/IPhYv4CBrox7mPfPgiQ8v9ZT+z5EY9e28Hck4y9sYjOvP
Private Key: T1qIe4efEWBjletsVTldzux9f4sH/yDpTp3vvL3XAaZhZVdTVBGPPn14MZebBxkPP1V5RNcmIdK2DYGk+N+MPPh9
```

## Running the Service

### Local Development
```bash
cd kraken-service
mvn spring-boot:run
```

### Docker
```bash
docker build -t kraken-service .
docker run -p 8086:8086 -e KRAKEN_USE_DEMO=true kraken-service
```

### With Docker Compose
Service is included in the main docker-compose.yml

## Integration with Existing System

### Webhook Routing
The API Gateway automatically routes orders to Kraken when:
- `exchange` field contains "KRAKEN"
- Client has Kraken API credentials configured

### Risk Monitoring
The Risk Monitoring Service polls Kraken balances and enforces:
- Daily risk limits (percentage of initial balance)
- Max risk limits
- Automatic position closure on breach

## Trading Logic

### Pyramid Trading
- When `pyramid: false`: Rejects same-direction orders for a strategy
- When `pyramid: true`: Allows multiple same-direction orders

### Inverse Trading
- When `inverse: true`: Closes existing position before opening opposite

### Stop Loss
- Creates automatic stop-loss order at specified percentage
- Long positions: Stop below entry
- Short positions: Stop above entry

## Monitoring

- Health check: `GET /api/kraken/health`
- Metrics: `GET /actuator/metrics`
- Swagger UI: `http://localhost:8086/swagger-ui.html`

## Error Handling

All API errors return:
```json
{
  "success": false,
  "error": "ERROR_CODE",
  "message": "Detailed error message"
}
```

## Daily Reset

- Occurs at 00:01 UTC
- Resets daily trading blocks
- Clears daily loss tracking
- Maintains total PnL tracking