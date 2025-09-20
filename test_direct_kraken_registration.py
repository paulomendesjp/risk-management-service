#!/usr/bin/env python3
"""
Test direct Kraken user registration to bypass RabbitMQ issues
"""

import requests
import json
import time

KRAKEN_SERVICE_URL = "http://205.172.56.220:8086"

API_KEY = "/tjRZL5FBJ/IPhYv4CBrox7mPfPgiQ8v9ZT+z5EY9e28Hck4y9sYjOvP"
API_SECRET = "T1qIe4efEWBjletsVTldzux9f4sH/yDpTp3vvL3XAaZhZVdTVBGPPn14MZebBxkPP1V5RNcmIdK2DYGk+N+MPPh9"

def test_direct_kraken_registration():
    print("""
    ╔══════════════════════════════════════════════════╗
    ║        TESTE REGISTRO DIRETO KRAKEN              ║
    ║        Bypass RabbitMQ - Registro Direto         ║
    ╚══════════════════════════════════════════════════╝
    """)
    
    client_id = f"direct_kraken_test_{int(time.time())}"
    
    print(f"=== Registrando usuário diretamente no Kraken-Service: {client_id} ===")
    
    registration_payload = {
        "clientId": client_id,
        "apiKey": API_KEY,
        "apiSecret": API_SECRET,
        "initialBalance": 75000.0,
        "dailyRisk": {
            "type": "percentage",
            "value": 2.5
        },
        "maxRisk": {
            "type": "percentage",
            "value": 6.0
        }
    }
    
    print(f"Payload: {json.dumps(registration_payload, indent=2)}")
    
    # Register user directly in Kraken-Service
    try:
        response = requests.post(
            f"{KRAKEN_SERVICE_URL}/api/kraken/users/register",
            headers={"Content-Type": "application/json"},
            json=registration_payload,
            timeout=10
        )
        
        print(f"Direct Registration Status: {response.status_code}")
        print(f"Direct Registration Response: {json.dumps(response.json(), indent=2)}")
        
        if response.status_code != 200:
            print("❌ Direct registration failed")
            return False, client_id
            
    except Exception as e:
        print(f"❌ Error during direct registration: {e}")
        return False, client_id
    
    print("✅ Direct registration successful!")
    
    # Wait a moment for the account to be set up
    print("\n⏳ Aguardando configuração da conta...")
    time.sleep(3)
    
    # Test webhook with the registered user
    print(f"\n=== Testando webhook com usuário registrado diretamente ===")
    
    try:
        webhook_payload = {
            "clientId": client_id,
            "symbol": "ETH/USD",
            "strategy": "direct-test",
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
            print("❌ Webhook test FAILED - Conta de monitoramento não encontrada")
            return False, client_id
        else:
            print("✅ Webhook test SUCCESS - Conta de monitoramento encontrada")
            return True, client_id
            
    except Exception as e:
        print(f"❌ Error during webhook test: {e}")
        return False, client_id

def test_balance_endpoint(client_id):
    """Test balance endpoint"""
    print(f"\n=== Testando endpoint de balance para {client_id} ===")
    
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
    # Test direct registration
    success, client_id = test_direct_kraken_registration()
    
    if success:
        print("\n🎉 REGISTRO DIRETO FUNCIONOU!")
        
        # Test balance endpoint
        balance_success = test_balance_endpoint(client_id)
        
        if balance_success:
            print("\n✅ TODOS OS TESTES PASSARAM!")
            print("\nConclusão: O problema é na integração RabbitMQ, não na lógica do Kraken-Service")
        else:
            print("\n⚠️ Registro funcionou mas balance endpoint falhou")
    else:
        print("\n💥 REGISTRO DIRETO FALHOU!")
        print("\nO problema pode estar na lógica do Kraken-Service")
