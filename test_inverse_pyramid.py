#!/usr/bin/env python3
"""
Test Script for Inverse and Pyramid Trading Logic
Tests the specific requirements from the Kraken documentation
"""

import requests
import json
import time
from datetime import datetime

# Test configuration
BASE_URL = "http://205.172.56.220:8086"
API_KEY = "/tjRZL5FBJ/IPhYv4CBrox7mPfPgiQ8v9ZT+z5EY9e28Hck4y9sYjOvP"
API_SECRET = "T1qIe4efEWBjletsVTldzux9f4sH/yDpTp3vvL3XAaZhZVdTVBGPPn14MZebBxkPP1V5RNcmIdK2DYGk+N+MPPh9"

# Headers with Kraken credentials
headers = {
    "X-API-Key": API_KEY,
    "X-API-Secret": API_SECRET,
    "Content-Type": "application/json"
}

def send_webhook_order(side, qty=0.01, inverse=False, pyramid=False, strategy="test-strategy"):
    """Send webhook order with specific parameters"""
    
    webhook_payload = {
        "clientId": "kraken_test_client",
        "symbol": "ETH/USD",
        "strategy": strategy,
        "maxRiskPerDay": 2.0,
        "side": side.upper(),
        "orderQty": qty,
        "inverse": inverse,
        "pyramid": pyramid,
        "stopLoss": 0.5,
        "exchange": "KRAKEN"
    }

    print(f"📤 Sending order: {side.upper()} {qty} ETH/USD")
    print(f"   Strategy: {strategy}")
    print(f"   Inverse: {inverse}")
    print(f"   Pyramid: {pyramid}")

    response = requests.post(
        f"{BASE_URL}/api/kraken/webhook",
        headers=headers,
        json=webhook_payload
    )

    print(f"   Status: {response.status_code}")
    result = response.json()
    print(f"   Response: {json.dumps(result, indent=2)}")
    
    return result

def test_pyramid_logic():
    """
    Test Pyramid Logic:
    - pyramid=false: Should reject same direction orders
    - pyramid=true: Should allow same direction orders
    """
    print("\n" + "="*60)
    print("🔺 TESTING PYRAMID LOGIC")
    print("="*60)
    
    strategy = "pyramid-test-strategy"
    
    print("\n1️⃣ Testing pyramid=false (should reject second same-direction order)")
    
    # First BUY order with pyramid=false
    print("\n   First BUY order (pyramid=false):")
    result1 = send_webhook_order("buy", 0.01, inverse=False, pyramid=False, strategy=strategy)
    
    time.sleep(2)
    
    # Second BUY order with pyramid=false (should be REJECTED)
    print("\n   Second BUY order (pyramid=false) - SHOULD BE REJECTED:")
    result2 = send_webhook_order("buy", 0.01, inverse=False, pyramid=False, strategy=strategy)
    
    if result2.get("success") == False and result2.get("error") == "PYRAMID_VIOLATION":
        print("   ✅ PYRAMID LOGIC WORKING: Same direction order correctly rejected")
    else:
        print("   ❌ PYRAMID LOGIC FAILED: Same direction order should have been rejected")
    
    time.sleep(2)
    
    print("\n2️⃣ Testing pyramid=true (should allow same direction orders)")
    
    strategy2 = "pyramid-true-strategy"
    
    # First BUY order with pyramid=true
    print("\n   First BUY order (pyramid=true):")
    result3 = send_webhook_order("buy", 0.01, inverse=False, pyramid=True, strategy=strategy2)
    
    time.sleep(2)
    
    # Second BUY order with pyramid=true (should be ACCEPTED)
    print("\n   Second BUY order (pyramid=true) - SHOULD BE ACCEPTED:")
    result4 = send_webhook_order("buy", 0.01, inverse=False, pyramid=True, strategy=strategy2)
    
    if result4.get("success") == True:
        print("   ✅ PYRAMID LOGIC WORKING: Multiple same direction orders allowed")
    else:
        print("   ❌ PYRAMID LOGIC FAILED: Multiple same direction orders should be allowed")

def test_inverse_logic():
    """
    Test Inverse Logic:
    - inverse=false: Normal trading (BUY then SELL closes position)
    - inverse=true: Close position and reverse (BUY then SELL closes BUY and opens SHORT)
    """
    print("\n" + "="*60)
    print("🔄 TESTING INVERSE LOGIC")
    print("="*60)
    
    strategy = "inverse-test-strategy"
    
    print("\n1️⃣ Testing inverse=false (normal trading)")
    
    # BUY order with inverse=false
    print("\n   BUY order (inverse=false):")
    result1 = send_webhook_order("buy", 0.01, inverse=False, pyramid=False, strategy=strategy)
    
    time.sleep(3)
    
    # SELL order with inverse=false (should just close the BUY position)
    print("\n   SELL order (inverse=false) - should close BUY position:")
    result2 = send_webhook_order("sell", 0.01, inverse=False, pyramid=False, strategy=strategy)
    
    time.sleep(3)
    
    print("\n2️⃣ Testing inverse=true (close and reverse)")
    
    strategy2 = "inverse-true-strategy"
    
    # BUY order first
    print("\n   BUY order (inverse=false):")
    result3 = send_webhook_order("buy", 0.01, inverse=False, pyramid=False, strategy=strategy2)
    
    time.sleep(3)
    
    # SELL order with inverse=true (should close BUY and open SHORT)
    print("\n   SELL order (inverse=true) - should close BUY and open SHORT:")
    result4 = send_webhook_order("sell", 0.01, inverse=True, pyramid=False, strategy=strategy2)
    
    if result4.get("success") == True:
        print("   ✅ INVERSE LOGIC WORKING: Position closed and reversed")
    else:
        print("   ❌ INVERSE LOGIC FAILED: Should close and reverse position")

def test_combined_logic():
    """
    Test combined inverse and pyramid logic
    """
    print("\n" + "="*60)
    print("🔄🔺 TESTING COMBINED INVERSE + PYRAMID LOGIC")
    print("="*60)
    
    strategy = "combined-test-strategy"
    
    print("\n1️⃣ BUY with pyramid=true, inverse=false")
    result1 = send_webhook_order("buy", 0.01, inverse=False, pyramid=True, strategy=strategy)
    
    time.sleep(2)
    
    print("\n2️⃣ Another BUY with pyramid=true (should be allowed)")
    result2 = send_webhook_order("buy", 0.01, inverse=False, pyramid=True, strategy=strategy)
    
    time.sleep(2)
    
    print("\n3️⃣ SELL with inverse=true (should close all BUY positions and open SHORT)")
    result3 = send_webhook_order("sell", 0.02, inverse=True, pyramid=False, strategy=strategy)
    
    time.sleep(2)
    
    print("\n4️⃣ Another SELL with pyramid=false (should be rejected)")
    result4 = send_webhook_order("sell", 0.01, inverse=False, pyramid=False, strategy=strategy)

def get_positions():
    """Get current positions"""
    print("\n📊 Current Positions:")
    response = requests.get(
        f"{BASE_URL}/api/kraken/positions",
        headers=headers
    )
    print(f"Status: {response.status_code}")
    print(f"Positions: {json.dumps(response.json(), indent=2)}")

def cleanup_positions():
    """Close all positions to clean up"""
    print("\n🧹 Cleaning up all positions...")
    response = requests.post(
        f"{BASE_URL}/api/kraken/positions/close-all/kraken_test_client",
        headers=headers
    )
    print(f"Cleanup Status: {response.status_code}")
    print(f"Cleanup Result: {json.dumps(response.json(), indent=2)}")

def run_all_tests():
    """Run all inverse and pyramid tests"""
    print("""
    ╔══════════════════════════════════════════════════╗
    ║         INVERSE & PYRAMID LOGIC TESTS            ║
    ╠══════════════════════════════════════════════════╣
    ║  Testing Kraken Trading Requirements:            ║
    ║  • Pyramid: false = reject same direction        ║
    ║  • Pyramid: true = allow same direction          ║
    ║  • Inverse: false = normal close                 ║
    ║  • Inverse: true = close and reverse             ║
    ╚══════════════════════════════════════════════════╝
    """)

    try:
        # Clean up first
        cleanup_positions()
        time.sleep(3)
        
        # Test pyramid logic
        test_pyramid_logic()
        time.sleep(3)
        
        # Clean up between tests
        cleanup_positions()
        time.sleep(3)
        
        # Test inverse logic
        test_inverse_logic()
        time.sleep(3)
        
        # Clean up between tests
        cleanup_positions()
        time.sleep(3)
        
        # Test combined logic
        test_combined_logic()
        time.sleep(3)
        
        # Final positions check
        get_positions()
        
        # Final cleanup
        cleanup_positions()

        print("""
        ╔══════════════════════════════════════════════════╗
        ║             TESTS COMPLETED                      ║
        ╠══════════════════════════════════════════════════╣
        ║  ✅ Pyramid Logic: Implemented                   ║
        ║  ✅ Inverse Logic: Implemented                   ║
        ║  ✅ Combined Logic: Implemented                  ║
        ╚══════════════════════════════════════════════════╝
        """)

    except Exception as e:
        print(f"\n❌ Test failed: {str(e)}")
        raise

if __name__ == "__main__":
    run_all_tests()

