#!/usr/bin/env python3
"""
Test Script for Kraken Service
Tests the risk management functionality with Kraken API
"""

import requests
import json
import time
from datetime import datetime

# Test configuration
BASE_URL = "http://205.172.56.220:8086"
USER_SERVICE_URL = "http://205.172.56.220:8081"
API_KEY = "/tjRZL5FBJ/IPhYv4CBrox7mPfPgiQ8v9ZT+z5EY9e28Hck4y9sYjOvP"
API_SECRET = "T1qIe4efEWBjletsVTldzux9f4sH/yDpTp3vvL3XAaZhZVdTVBGPPn14MZebBxkPP1V5RNcmIdK2DYGk+N+MPPh9"

# Generate unique client ID for each test run
UNIQUE_CLIENT_ID = f"kraken_test_{int(time.time())}"

# Headers with Kraken credentials
headers = {
    "X-API-Key": API_KEY,
    "X-API-Secret": API_SECRET,
    "Content-Type": "application/json"
}

def test_health():
    """Test if Kraken service is healthy"""
    print("\n=== Testing Kraken Service Health ===")
    response = requests.get(f"{BASE_URL}/api/kraken/health")
    print(f"Health Status: {response.json()}")
    assert response.status_code == 200
    assert response.json()["status"] == "UP"
    print("✅ Service is healthy")

def test_public_connection():
    """Test public API connection"""
    print("\n=== Testing Public API Connection ===")
    response = requests.get(f"{BASE_URL}/api/kraken/test-public")
    print(f"Response Status: {response.status_code}")
    print(f"Public API Test: {json.dumps(response.json(), indent=2)}")
    return response.json()

def test_balance():
    """Test balance endpoint"""
    print("\n=== Testing Balance Endpoint ===")
    response = requests.get(
        f"{BASE_URL}/api/kraken/balance/{UNIQUE_CLIENT_ID}",
        headers=headers
    )
    print(f"Response Status: {response.status_code}")
    print(f"Balance Info: {json.dumps(response.json(), indent=2)}")
    return response.json()

def test_positions():
    """Test getting positions"""
    print("\n=== Testing Get Positions ===")
    response = requests.get(
        f"{BASE_URL}/api/kraken/positions",
        headers=headers
    )
    print(f"Response Status: {response.status_code}")
    print(f"Positions: {json.dumps(response.json(), indent=2)}")
    return response.json()

def test_user_registration():
    """Test user registration via User-Service"""
    print("\n=== Testing User Registration via User-Service ===")
    print(f"🆔 Using unique client ID: {UNIQUE_CLIENT_ID}")
    
    registration_payload = {
        "clientId": UNIQUE_CLIENT_ID,
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

    print(f"Registering user via User-Service: {json.dumps(registration_payload, indent=2)}")

    response = requests.post(
        f"{USER_SERVICE_URL}/api/users/register",
        headers={"Content-Type": "application/json"},
        json=registration_payload
    )

    print(f"Registration Response Status: {response.status_code}")
    print(f"Registration Response: {json.dumps(response.json(), indent=2)}")
    
    # Aguardar um pouco para o evento RabbitMQ ser processado
    if response.status_code == 200:
        print("⏳ Aguardando processamento do evento RabbitMQ...")
        time.sleep(5)  # Aguarda 5 segundos para o Kraken-Service processar
    
    return response.json()

def test_webhook_order(action="buy", qty=0.01):
    """Test webhook order placement"""
    print(f"\n=== Testing Webhook Order ({action}) ===")

    webhook_payload = {
        "clientId": UNIQUE_CLIENT_ID,
        "symbol": "ETH/USD",
        "strategy": "test-model1",
        "maxRiskPerDay": 2.0,  # 2% daily risk (sem % no nome)
        "side": action.upper(),  # Mudando de "action" para "side"
        "orderQty": qty,
        "inverse": False,
        "pyramid": False,
        "stopLoss": 0.5,  # Sem % no nome
        "exchange": "KRAKEN"
    }

    print(f"Sending webhook payload: {json.dumps(webhook_payload, indent=2)}")

    response = requests.post(
        f"{BASE_URL}/api/kraken/webhook",
        headers=headers,
        json=webhook_payload
    )

    print(f"Response Status: {response.status_code}")
    print(f"Response: {json.dumps(response.json(), indent=2)}")
    return response.json()

def test_risk_management():
    """Test risk management features"""
    print("\n=== Testing Risk Management ===")

    # 0. Register user first
    print("0. Registering user...")
    registration_result = test_user_registration()
    
    if not registration_result.get("success", False):
        print("❌ User registration failed, skipping risk management tests")
        return registration_result

    # 1. Get initial balance
    print("1. Getting initial balance...")
    balance1 = test_balance()

    # 2. Place a test order
    print("\n2. Placing test order...")
    order_result = test_webhook_order("buy", 0.01)

    # Wait for order to process
    time.sleep(3)

    # 3. Check positions
    print("\n3. Checking positions after order...")
    positions = test_positions()

    # 4. Check balance again
    print("\n4. Checking balance after order...")
    balance2 = test_balance()

    # 5. Test close all positions (risk breach simulation)
    print("\n5. Testing close all positions...")
    client_id = UNIQUE_CLIENT_ID
    response = requests.post(
        f"{BASE_URL}/api/kraken/positions/close-all/{client_id}",
        headers=headers
    )
    print(f"Close All Response: {json.dumps(response.json(), indent=2)}")

    return {
        "initial_balance": balance1,
        "order_result": order_result,
        "positions": positions,
        "final_balance": balance2
    }

def test_pyramid_trading():
    """Test pyramid trading logic"""
    print("\n=== Testing Pyramid Trading Logic ===")

    # Test with pyramid=false (should reject same direction)
    print("\n1. Testing pyramid=false (should reject second buy)...")

    # First buy order
    order1 = test_webhook_order("buy", 0.01)
    time.sleep(2)

    # Second buy order (should be rejected)
    webhook_payload = {
        "clientId": UNIQUE_CLIENT_ID,
        "symbol": "ETH/USD",
        "strategy": "test-model1",
        "maxriskperday%": "2",
        "action": "buy",
        "orderQty": 0.01,
        "inverse": False,
        "pyramid": False,  # Should reject same direction
        "stopLoss%": 0.5,
        "exchange": "KRAKEN"
    }

    response = requests.post(
        f"{BASE_URL}/api/kraken/webhook",
        headers=headers,
        json=webhook_payload
    )

    print(f"Second buy order response: {json.dumps(response.json(), indent=2)}")

    # Test with pyramid=true (should accept same direction)
    print("\n2. Testing pyramid=true (should accept multiple buys)...")

    webhook_payload["pyramid"] = True
    response = requests.post(
        f"{BASE_URL}/api/kraken/webhook",
        headers=headers,
        json=webhook_payload
    )

    print(f"Third buy order response (pyramid=true): {json.dumps(response.json(), indent=2)}")

def test_inverse_trading():
    """Test inverse trading logic"""
    print("\n=== Testing Inverse Trading Logic ===")

    # Place buy order
    print("\n1. Placing initial buy order...")
    test_webhook_order("buy", 0.01)
    time.sleep(2)

    # Place sell order with inverse=true
    print("\n2. Placing sell order with inverse=true (should close buy and open short)...")
    webhook_payload = {
        "clientId": UNIQUE_CLIENT_ID,
        "symbol": "ETH/USD",
        "strategy": "test-model1",
        "maxriskperday%": "2",
        "action": "sell",
        "orderQty": 0.01,
        "inverse": True,  # Should close and reverse position
        "pyramid": False,
        "stopLoss%": 0.5,
        "exchange": "KRAKEN"
    }

    response = requests.post(
        f"{BASE_URL}/api/kraken/webhook",
        headers=headers,
        json=webhook_payload
    )

    print(f"Inverse sell order response: {json.dumps(response.json(), indent=2)}")

def run_all_tests():
    """Run all tests"""
    print(f"""
    ╔══════════════════════════════════════════════╗
    ║           KRAKEN SERVICE TEST SUITE          ║
    ╠══════════════════════════════════════════════╣
    ║  Testing Risk Management System Integration  ║
    ║  with Kraken Futures API                    ║
    ║  🆔 Client ID: {UNIQUE_CLIENT_ID}            ║
    ╚══════════════════════════════════════════════╝
    """)

    try:
        # Basic tests
        test_health()
        test_public_connection()

        # User registration
        test_user_registration()

        # Balance and positions
        test_balance()
        test_positions()

        # Risk management
        results = test_risk_management()

        # Trading logic tests
        test_pyramid_trading()
        test_inverse_trading()

        print("""
        ╔══════════════════════════════════════════════╗
        ║             ALL TESTS COMPLETED              ║
        ╚══════════════════════════════════════════════╝
        """)

        return results

    except Exception as e:
        print(f"\n❌ Test failed: {str(e)}")
        raise

if __name__ == "__main__":
    run_all_tests()
