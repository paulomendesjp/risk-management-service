#!/usr/bin/env python3
"""
Script para registrar usuário Kraken usando o User-Service
"""

import requests
import json

# Configuração
USER_SERVICE_URL = "http://205.172.56.220:8081"
API_KEY = "/tjRZL5FBJ/IPhYv4CBrox7mPfPgiQ8v9ZT+z5EY9e28Hck4y9sYjOvP"
API_SECRET = "T1qIe4efEWBjletsVTldzux9f4sH/yDpTp3vvL3XAaZhZVdTVBGPPn14MZebBxkPP1V5RNcmIdK2DYGk+N+MPPh9"

def register_kraken_user():
    """Registra um usuário Kraken usando o User-Service"""
    
    # Payload para registrar usuário Kraken
    registration_payload = {
        "clientId": "kraken_test_client",
        "apiKey": API_KEY,
        "apiSecret": API_SECRET,
        "exchange": "KRAKEN",  # Especifica que é Kraken
        "initialBalance": 100000.0,  # Balance inicial em USD
        
        # Daily Risk: 2% do balance inicial
        "dailyRisk": {
            "type": "percentage",
            "value": 2.0
        },
        
        # Max Risk: 5% do balance inicial  
        "maxRisk": {
            "type": "percentage", 
            "value": 5.0
        }
    }
    
    print("=== Registrando Usuário Kraken via User-Service ===")
    print(f"Payload: {json.dumps(registration_payload, indent=2)}")
    
    try:
        response = requests.post(
            f"{USER_SERVICE_URL}/api/users/register",
            headers={"Content-Type": "application/json"},
            json=registration_payload
        )
        
        print(f"\nStatus: {response.status_code}")
        print(f"Response: {json.dumps(response.json(), indent=2)}")
        
        if response.status_code == 200:
            print("✅ Usuário Kraken registrado com sucesso!")
            
            # O User-Service automaticamente:
            # 1. Valida as credenciais Kraken
            # 2. Salva o usuário no MongoDB
            # 3. Publica evento no RabbitMQ
            # 4. Kraken-Service recebe o evento e inicia monitoramento
            
            return response.json()
        else:
            print("❌ Erro no registro")
            return None
            
    except Exception as e:
        print(f"❌ Erro na requisição: {e}")
        return None

def check_user_status(client_id):
    """Verifica o status do usuário registrado"""
    
    print(f"\n=== Verificando Status do Usuário: {client_id} ===")
    
    try:
        response = requests.get(f"{USER_SERVICE_URL}/api/users/{client_id}")
        
        print(f"Status: {response.status_code}")
        print(f"User Info: {json.dumps(response.json(), indent=2)}")
        
        return response.json()
        
    except Exception as e:
        print(f"❌ Erro ao verificar usuário: {e}")
        return None

def test_kraken_service_monitoring():
    """Testa se o Kraken-Service está monitorando o usuário"""
    
    KRAKEN_SERVICE_URL = "http://205.172.56.220:8086"
    client_id = "kraken_test_client"
    
    print(f"\n=== Testando Monitoramento Kraken-Service ===")
    
    try:
        # Testa balance
        response = requests.get(
            f"{KRAKEN_SERVICE_URL}/api/kraken/balance/{client_id}",
            headers={
                "X-API-Key": API_KEY,
                "X-API-Secret": API_SECRET
            }
        )
        
        print(f"Balance Status: {response.status_code}")
        print(f"Balance Response: {json.dumps(response.json(), indent=2)}")
        
        # Testa positions
        response = requests.get(
            f"{KRAKEN_SERVICE_URL}/api/kraken/positions",
            headers={
                "X-API-Key": API_KEY,
                "X-API-Secret": API_SECRET
            }
        )
        
        print(f"Positions Status: {response.status_code}")
        print(f"Positions Response: {json.dumps(response.json(), indent=2)}")
        
    except Exception as e:
        print(f"❌ Erro ao testar Kraken-Service: {e}")

if __name__ == "__main__":
    print("""
    ╔══════════════════════════════════════════════════╗
    ║        REGISTRO DE USUÁRIO KRAKEN                ║
    ║        via User-Service                          ║
    ╚══════════════════════════════════════════════════╝
    """)
    
    # 1. Registrar usuário
    result = register_kraken_user()
    
    if result:
        # 2. Verificar status
        check_user_status("kraken_test_client")
        
        # 3. Testar monitoramento
        test_kraken_service_monitoring()
        
        print("""
        ╔══════════════════════════════════════════════════╗
        ║                FLUXO COMPLETO                    ║
        ╠══════════════════════════════════════════════════╣
        ║ 1. User-Service registra usuário                 ║
        ║ 2. Publica evento no RabbitMQ                    ║
        ║ 3. Kraken-Service recebe evento                  ║
        ║ 4. Inicia monitoramento automático               ║
        ║ 5. Calcula risk limits baseado no balance        ║
        ╚══════════════════════════════════════════════════╝
        """)

