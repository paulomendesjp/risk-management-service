#!/usr/bin/env python3
"""
Test RabbitMQ Integration After Fix
Tests the corrected RabbitMQ configuration between User-Service and Kraken-Service
"""

import requests
import json
import time

USER_SERVICE_URL = "http://205.172.56.220:8081"
KRAKEN_SERVICE_URL = "http://205.172.56.220:8086"

API_KEY = "/tjRZL5FBJ/IPhYv4CBrox7mPfPgiQ8v9ZT+z5EY9e28Hck4y9sYjOvP"
API_SECRET = "T1qIe4efEWBjletsVTldzux9f4sH/yDpTp3vvL3XAaZhZVdTVBGPPn14MZebBxkPP1V5RNcmIdK2DYGk+N+MPPh9"

def check_service_health(service_name, url):
    """Check if service is healthy"""
    try:
        response = requests.get(f"{url}/api/{service_name.lower()}/health", timeout=5)
        print(f"✅ {service_name} Service Health: {response.status_code}")
        return response.status_code == 200
    except requests.exceptions.RequestException as e:
        print(f"❌ {service_name} Service not reachable: {e}")
        return False

def register_user_via_user_service(client_id):
    """Register user via User-Service"""
    print(f"\n=== Registrando usuário: {client_id} ===")
    
    registration_payload = {
        "clientId": client_id,
        "apiKey": API_KEY,
        "apiSecret": API_SECRET,
        "exchange": "KRAKEN",
        "initialBalance": 100000.0,
        "dailyRisk": {"type": "percentage", "value": 2.0},
        "maxRisk": {"type": "percentage", "value": 5.0}
    }
    
    print(f"📤 Payload: {json.dumps(registration_payload, indent=2)}")
    
    try:
        response = requests.post(
            f"{USER_SERVICE_URL}/api/users/register", 
            json=registration_payload,
            timeout=10
        )
        print(f"📋 Registration Status: {response.status_code}")
        print(f"📋 Registration Response: {json.dumps(response.json(), indent=2)}")
        return response.status_code, response.json()
    except requests.exceptions.RequestException as e:
        print(f"❌ Registration failed: {e}")
        return 500, {"error": str(e)}

def test_kraken_webhook_for_client(client_id):
    """Test webhook to verify Kraken-Service received the event"""
    print(f"\n=== Testando webhook para: {client_id} ===")
    
    webhook_payload = {
        "clientId": client_id,
        "symbol": "ETH/USD",
        "strategy": "test-strategy",
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
    
    try:
        response = requests.post(
            f"{KRAKEN_SERVICE_URL}/api/kraken/webhook", 
            headers=headers, 
            json=webhook_payload,
            timeout=10
        )
        print(f"🔗 Webhook Status: {response.status_code}")
        print(f"🔗 Webhook Response: {json.dumps(response.json(), indent=2)}")
        return response.status_code, response.json()
    except requests.exceptions.RequestException as e:
        print(f"❌ Webhook failed: {e}")
        return 500, {"error": str(e)}

def run_rabbitmq_integration_test():
    """Run complete RabbitMQ integration test"""
    print(f"""
    ╔══════════════════════════════════════════════════╗
    ║        TESTE RABBITMQ APÓS CORREÇÃO              ║
    ║        User-Service → Kraken-Service             ║
    ║        Configuração Simplificada                 ║
    ╚══════════════════════════════════════════════════╝
    """)
    
    # Check services health
    print("\n=== Verificando saúde dos serviços ===")
    user_service_up = check_service_health("User", USER_SERVICE_URL)
    kraken_service_up = check_service_health("Kraken", KRAKEN_SERVICE_URL)
    
    if not (user_service_up and kraken_service_up):
        print("❌ Um ou mais serviços não estão funcionando")
        return False
    
    print("✅ Ambos os serviços estão funcionando")
    
    # Generate unique client ID
    client_id = f"rabbitmq_fixed_test_{int(time.time())}"
    print(f"\n🆔 Cliente único: {client_id}")
    
    # Step 1: Register user via User-Service
    reg_status, reg_response = register_user_via_user_service(client_id)
    
    if reg_status != 201:
        print("❌ Registro de usuário falhou, não é possível testar a integração RabbitMQ.")
        return False
    
    print("✅ Usuário registrado com sucesso!")
    
    # Step 2: Wait for RabbitMQ event processing
    print("\n⏳ Aguardando processamento do evento RabbitMQ...")
    for i in range(1, 16):  # Wait for 15 seconds
        print(f"   Aguardando... {i}/15 segundos")
        time.sleep(1)
    
    # Step 3: Test webhook to verify Kraken-Service received the event
    webhook_status, webhook_response = test_kraken_webhook_for_client(client_id)
    
    # Step 4: Analyze results
    if webhook_status == 200 and webhook_response.get("success"):
        print("\n🎉 SUCESSO! RabbitMQ integration is WORKING!")
        print("✅ User-Service → RabbitMQ → Kraken-Service: FUNCIONANDO")
        return True
    elif webhook_status == 400 and "No monitoring account found" in webhook_response.get("message", ""):
        print("\n❌ FALHA! RabbitMQ integration ainda não está funcionando")
        print("❌ Kraken-Service ainda não recebeu o evento do User-Service")
        return False
    else:
        print(f"\n⚠️ Resultado inesperado: Status {webhook_status}")
        print("🔍 Verifique os logs do Kraken-Service para mais detalhes")
        return False

def main():
    """Main function"""
    success = run_rabbitmq_integration_test()
    
    if success:
        print("\n🏆 TESTE CONCLUÍDO COM SUCESSO!")
        print("🎯 RabbitMQ integration está funcionando perfeitamente!")
    else:
        print("\n💥 TESTE FALHOU!")
        print("🔧 Verifique se o Kraken-Service foi rebuilded após as correções")
        print("📋 Comandos para rebuild:")
        print("   docker-compose restart kraken-service")
        print("   # OU")
        print("   cd kraken-service && mvn clean package -DskipTests")

if __name__ == "__main__":
    main()
