#!/usr/bin/env python3
"""
Test RabbitMQ integration between User-Service and Kraken-Service
"""

import requests
import json
import time

USER_SERVICE_URL = "http://205.172.56.220:8081"
KRAKEN_SERVICE_URL = "http://205.172.56.220:8086"

API_KEY = "/tjRZL5FBJ/IPhYv4CBrox7mPfPgiQ8v9ZT+z5EY9e28Hck4y9sYjOvP"
API_SECRET = "T1qIe4efEWBjletsVTldzux9f4sH/yDpTp3vvL3XAaZhZVdTVBGPPn14MZebBxkPP1V5RNcmIdK2DYGk+N+MPPh9"

def test_rabbitmq_integration():
    print("""
    ╔══════════════════════════════════════════════════╗
    ║        TESTE INTEGRAÇÃO RABBITMQ                 ║
    ║        User-Service → Kraken-Service             ║
    ╚══════════════════════════════════════════════════╝
    """)
    
    # Use a different client ID to avoid conflicts
    client_id = f"rabbitmq_test_client_{int(time.time())}"
    
    print(f"=== Registrando usuário: {client_id} ===")
    
    registration_payload = {
        "clientId": client_id,
        "apiKey": API_KEY,
        "apiSecret": API_SECRET,
        "exchange": "KRAKEN",
        "initialBalance": 50000.0,
        "dailyRisk": {
            "type": "percentage",
            "value": 3.0
        },
        "maxRisk": {
            "type": "percentage",
            "value": 7.0
        }
    }
    
    print(f"Payload: {json.dumps(registration_payload, indent=2)}")
    
    # Register user via User-Service
    try:
        response = requests.post(
            f"{USER_SERVICE_URL}/api/users/register",
            headers={"Content-Type": "application/json"},
            json=registration_payload,
            timeout=10
        )
        
        print(f"Registration Status: {response.status_code}")
        print(f"Registration Response: {json.dumps(response.json(), indent=2)}")
        
        if response.status_code != 201:
            print("❌ Registration failed")
            return False
            
    except Exception as e:
        print(f"❌ Error during registration: {e}")
        return False
    
    # Wait for RabbitMQ event processing
    print("\n⏳ Aguardando processamento do evento RabbitMQ...")
    for i in range(10):
        print(f"   Aguardando... {i+1}/10 segundos")
        time.sleep(1)
    
    # Test if Kraken-Service received the event
    print(f"\n=== Testando se Kraken-Service recebeu o evento ===")
    
    try:
        # Try to place an order to see if monitoring account exists
        webhook_payload = {
            "clientId": client_id,
            "symbol": "ETH/USD",
            "strategy": "rabbitmq-test",
            "side": "BUY",
            "orderQty": 0.01,
            "inverse": False,
            "pyramid": False,
            "stopLoss": 0.5,
            "exchange": "KRAKEN"
        }
        
        headers = {
            "X-API-Key": API_KEY,
            "X-API-Secret": API_SECRET,
            "Content-Type": "application/json"
        }
        
        response = requests.post(
            f"{KRAKEN_SERVICE_URL}/api/kraken/webhook",
            headers=headers,
            json=webhook_payload,
            timeout=10
        )
        
        print(f"Webhook Test Status: {response.status_code}")
        print(f"Webhook Test Response: {json.dumps(response.json(), indent=2)}")
        
        if response.status_code == 400 and "No monitoring account found" in response.text:
            print("❌ RabbitMQ integration FAILED - Kraken-Service não recebeu o evento")
            return False
        else:
            print("✅ RabbitMQ integration SUCCESS - Kraken-Service recebeu o evento")
            return True
            
    except Exception as e:
        print(f"❌ Error during webhook test: {e}")
        return False

def check_kraken_service_health():
    """Check if Kraken service is healthy"""
    try:
        response = requests.get(f"{KRAKEN_SERVICE_URL}/api/kraken/health", timeout=5)
        print(f"Kraken Service Health: {response.status_code}")
        return response.status_code == 200
    except Exception as e:
        print(f"❌ Kraken Service not reachable: {e}")
        return False

def check_user_service_health():
    """Check if User service is healthy"""
    try:
        response = requests.get(f"{USER_SERVICE_URL}/api/users/health", timeout=5)
        print(f"User Service Health: {response.status_code}")
        return response.status_code == 200
    except Exception as e:
        print(f"❌ User Service not reachable: {e}")
        return False

if __name__ == "__main__":
    print("=== Verificando saúde dos serviços ===")
    user_healthy = check_user_service_health()
    kraken_healthy = check_kraken_service_health()
    
    if not user_healthy or not kraken_healthy:
        print("❌ Um ou mais serviços não estão funcionando")
        exit(1)
    
    print("\n✅ Ambos os serviços estão funcionando")
    
    # Run the integration test
    success = test_rabbitmq_integration()
    
    if success:
        print("\n🎉 TESTE DE INTEGRAÇÃO RABBITMQ PASSOU!")
    else:
        print("\n💥 TESTE DE INTEGRAÇÃO RABBITMQ FALHOU!")
        print("\nPossíveis causas:")
        print("1. RabbitMQ não está rodando")
        print("2. Configuração de exchange/queue incorreta")
        print("3. Kraken-Service não está escutando a fila correta")
        print("4. User-Service não está publicando no exchange correto")
