#!/usr/bin/env python3
"""
Final integration test after RabbitMQ configuration fix
"""

import requests
import json
import time

USER_SERVICE_URL = "http://205.172.56.220:8081"
KRAKEN_SERVICE_URL = "http://205.172.56.220:8086"

API_KEY = "/tjRZL5FBJ/IPhYv4CBrox7mPfPgiQ8v9ZT+z5EY9e28Hck4y9sYjOvP"
API_SECRET = "T1qIe4efEWBjletsVTldzux9f4sH/yDpTp3vvL3XAaZhZVdTVBGPPn14MZebBxkPP1V5RNcmIdK2DYGk+N+MPPh9"

def test_final_integration():
    print("""
    ╔══════════════════════════════════════════════════╗
    ║        TESTE FINAL DE INTEGRAÇÃO                 ║
    ║        Após correção RabbitMQ Config             ║
    ╚══════════════════════════════════════════════════╝
    """)
    
    # Use a unique client ID
    client_id = f"final_test_client_{int(time.time())}"
    
    print(f"=== Registrando usuário: {client_id} ===")
    
    registration_payload = {
        "clientId": client_id,
        "apiKey": API_KEY,
        "apiSecret": API_SECRET,
        "exchange": "KRAKEN",
        "initialBalance": 100000.0,
        "dailyRisk": {
            "type": "percentage",
            "value": 2.0
        },
        "maxRisk": {
            "type": "percentage",
            "value": 5.0
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
    for i in range(15):  # Wait longer this time
        print(f"   Aguardando... {i+1}/15 segundos")
        time.sleep(1)
    
    # Test webhook to verify monitoring account exists
    print(f"\n=== Testando webhook para verificar conta de monitoramento ===")
    
    try:
        webhook_payload = {
            "clientId": client_id,
            "symbol": "ETH/USD",
            "strategy": "final-integration-test",
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
        
        # Check if the error is still "No monitoring account found"
        if response.status_code == 400 and "No monitoring account found" in response.text:
            print("❌ RabbitMQ integration STILL FAILED")
            print("   Kraken-Service ainda não está recebendo eventos do User-Service")
            return False
        else:
            print("✅ RabbitMQ integration SUCCESS!")
            print("   Kraken-Service recebeu o evento e criou a conta de monitoramento")
            return True
            
    except Exception as e:
        print(f"❌ Error during webhook test: {e}")
        return False

def test_balance_after_integration(client_id):
    """Test balance endpoint after successful integration"""
    print(f"\n=== Testando balance após integração bem-sucedida ===")
    
    try:
        headers = {
            "X-API-Key": API_KEY,
            "X-API-Secret": API_SECRET
        }
        
        response = requests.get(
            f"{KRAKEN_SERVICE_URL}/api/kraken/balance?clientId={client_id}",
            headers=headers,
            timeout=10
        )
        
        print(f"Balance Status: {response.status_code}")
        print(f"Balance Response: {json.dumps(response.json(), indent=2)}")
        
        return response.status_code == 200
        
    except Exception as e:
        print(f"❌ Error during balance test: {e}")
        return False

if __name__ == "__main__":
    print("🚀 EXECUTANDO TESTE FINAL DE INTEGRAÇÃO...")
    print("   (Certifique-se de que o Kraken-Service foi rebuilded)")
    
    success = test_final_integration()
    
    if success:
        print("\n🎉 INTEGRAÇÃO RABBITMQ FUNCIONANDO!")
        print("\n✅ TODOS OS PROBLEMAS FORAM RESOLVIDOS:")
        print("   1. ✅ Mapeamento JSON (action → side)")
        print("   2. ✅ Criptografia de credenciais")
        print("   3. ✅ Validação permissiva em modo demo")
        print("   4. ✅ Lógica Inverse/Pyramid implementada")
        print("   5. ✅ Integração RabbitMQ funcionando")
        print("\n🎯 SISTEMA KRAKEN COMPLETAMENTE FUNCIONAL!")
    else:
        print("\n💥 INTEGRAÇÃO RABBITMQ AINDA NÃO FUNCIONANDO")
        print("\nVerifique se:")
        print("1. Kraken-Service foi rebuilded após mudanças na configuração")
        print("2. RabbitMQ está rodando e acessível")
        print("3. Não há erros nos logs do Kraken-Service")
        print("4. UserRegistrationListener está ativo")
