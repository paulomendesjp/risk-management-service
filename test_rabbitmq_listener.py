#!/usr/bin/env python3
"""
Test script to verify RabbitMQ listener is working in Kraken service
"""

import requests
import json
import time

USER_SERVICE_URL = "http://205.172.56.220:8081"
KRAKEN_SERVICE_URL = "http://205.172.56.220:8086"

API_KEY = "/tjRZL5FBJ/IPhYv4CBrox7mPfPgiQ8v9ZT+z5EY9e28Hck4y9sYjOvP"
API_SECRET = "T1qIe4efEWBjletsVTldzux9f4sH/yDpTp3vvL3XAaZhZVdTVBGPPn14MZebBxkPP1V5RNcmIdK2DYGk+N+MPPh9"

def test_rabbitmq_listener():
    """Test if Kraken service RabbitMQ listener is working"""
    print("""
╔══════════════════════════════════════════════════╗
║        TESTE RABBITMQ LISTENER KRAKEN            ║
║        Após adicionar @EnableRabbit              ║
╚══════════════════════════════════════════════════╝
    """)
    
    # Generate unique client ID
    client_id = f"rabbitmq_listener_test_{int(time.time())}"
    print(f"🆔 Testing with client ID: {client_id}")
    
    # Register user via User-Service
    print("\n=== 1. Registering user via User-Service ===")
    registration_payload = {
        "clientId": client_id,
        "apiKey": API_KEY,
        "apiSecret": API_SECRET,
        "exchange": "KRAKEN",
        "initialBalance": 50000.0,
        "dailyRisk": {"type": "percentage", "value": 3.0},
        "maxRisk": {"type": "percentage", "value": 7.0}
    }
    
    response = requests.post(f"{USER_SERVICE_URL}/api/users/register", json=registration_payload)
    print(f"Registration Status: {response.status_code}")
    print(f"Registration Response: {json.dumps(response.json(), indent=2)}")
    
    if response.status_code != 201:
        print("❌ User registration failed, cannot test RabbitMQ")
        return
    
    print("✅ User registered successfully")
    
    # Wait for RabbitMQ event processing
    print("\n=== 2. Waiting for RabbitMQ event processing ===")
    for i in range(1, 16):
        print(f"   ⏳ Waiting... {i}/15 seconds")
        time.sleep(1)
    
    # Test webhook to see if monitoring account was created
    print("\n=== 3. Testing webhook to verify monitoring account ===")
    webhook_payload = {
        "clientId": client_id,
        "symbol": "ETH/USD",
        "strategy": "test-listener",
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
    
    response = requests.post(f"{KRAKEN_SERVICE_URL}/api/kraken/webhook", headers=headers, json=webhook_payload)
    print(f"Webhook Status: {response.status_code}")
    print(f"Webhook Response: {json.dumps(response.json(), indent=2)}")
    
    # Check the response
    if response.status_code == 200:
        webhook_response = response.json()
        if webhook_response.get("success"):
            print("✅ SUCCESS! RabbitMQ listener is working!")
            print("   Kraken service received the user registration event")
            print("   Monitoring account was created successfully")
        else:
            error_msg = webhook_response.get("error", "Unknown error")
            if "No monitoring account found" in error_msg:
                print("❌ FAILED! RabbitMQ listener is NOT working")
                print("   Kraken service did not receive the user registration event")
                print("   No monitoring account was created")
            else:
                print(f"⚠️  Monitoring account exists but webhook failed: {error_msg}")
    else:
        print(f"❌ Webhook request failed with status: {response.status_code}")
    
    print(f"\n🔍 Check Kraken service logs for:")
    print(f"   - '🎯 RECEIVED USER REGISTRATION EVENT'")
    print(f"   - '🆔 Processing registration for clientId: {client_id}'")
    print(f"   - '🦑 New Kraken user registered: {client_id}'")
    print(f"   - '✅ Kraken monitoring activated for user: {client_id}'")

if __name__ == "__main__":
    test_rabbitmq_listener()
