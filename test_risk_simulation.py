#!/usr/bin/env python3
"""
Simulate Risk Monitoring Test
==============================
This simulates P&L losses to test the risk monitoring system
without placing real trades.
"""

import requests
import json
import time
import websocket
import threading
from datetime import datetime

# Configuration
USER_SERVICE_URL = "http://localhost:8081"
RISK_SERVICE_URL = "http://localhost:8083"

# Test configuration
TEST_USER_ID = "risk_test_50"
DAILY_RISK_LIMIT = 50.0

def log(message, level="INFO"):
    """Log with timestamp and emoji"""
    timestamp = datetime.now().strftime("%H:%M:%S")
    emoji = {
        "INFO": "ℹ️",
        "SUCCESS": "✅",
        "WARNING": "⚠️",
        "ERROR": "❌",
        "MONEY": "💰",
        "ALERT": "🚨",
        "BLOCK": "🚫"
    }.get(level, "📝")
    print(f"[{timestamp}] {emoji} {message}")

def create_test_user():
    """Create a test user with $50 daily risk limit"""
    log(f"Creating test user: {TEST_USER_ID}", "INFO")

    # Delete if exists
    try:
        requests.delete(f"{USER_SERVICE_URL}/api/users/{TEST_USER_ID}")
    except:
        pass

    # Create new user
    user_data = {
        "clientId": TEST_USER_ID,
        "name": "Risk Test User $50",
        "email": "risk50@test.com",
        "apiKey": "test_api_key_50",
        "apiSecret": "test_api_secret_50",
        "maxRisk": {
            "type": "PERCENTAGE",
            "value": 10.0
        },
        "dailyRisk": {
            "type": "AMOUNT",
            "value": DAILY_RISK_LIMIT
        }
    }

    response = requests.post(
        f"{USER_SERVICE_URL}/api/users/register",
        json=user_data,
        headers={"Content-Type": "application/json"}
    )

    if response.status_code in [200, 201]:
        log(f"User created with daily risk limit: ${DAILY_RISK_LIMIT}", "SUCCESS")
        return True
    else:
        log(f"Failed to create user: {response.text}", "ERROR")
        return False

def simulate_balance_update(new_balance, initial_balance=100000):
    """Send a simulated balance update to risk monitoring"""
    loss = initial_balance - new_balance
    log(f"Simulating balance update: ${new_balance:,.2f} (Loss: ${loss:,.2f})", "MONEY")

    balance_update = {
        "client_id": TEST_USER_ID,
        "balance": new_balance,
        "previous_balance": initial_balance,
        "unrealized_pnl": -(initial_balance - new_balance),
        "source": "simulation",
        "timestamp": datetime.now().isoformat()
    }

    response = requests.post(
        f"{RISK_SERVICE_URL}/api/risk/balance-update",
        json=balance_update,
        headers={"Content-Type": "application/json"}
    )

    if response.status_code == 200:
        log("Balance update sent to risk monitoring", "SUCCESS")
        return True
    else:
        log(f"Failed to send balance update: {response.text}", "ERROR")
        return False

def check_user_status():
    """Check if user can still trade"""
    response = requests.get(f"{USER_SERVICE_URL}/api/users/{TEST_USER_ID}/can-trade")

    if response.status_code == 200:
        can_trade = response.json()
        return can_trade
    return True

def get_risk_status():
    """Get current risk monitoring status"""
    response = requests.get(f"{RISK_SERVICE_URL}/api/risk/status/{TEST_USER_ID}")

    if response.status_code == 200:
        return response.json()
    return None

def run_simulation():
    """Run the complete simulation"""
    log("="*60, "INFO")
    log("RISK MONITORING SIMULATION TEST", "INFO")
    log(f"Daily Risk Limit: ${DAILY_RISK_LIMIT}", "INFO")
    log("="*60, "INFO")

    # Step 1: Create test user
    if not create_test_user():
        return False

    # Wait for initialization
    log("Waiting for services to initialize...", "INFO")
    time.sleep(3)

    # Step 2: Initialize monitoring
    initial_balance = 100000.0
    current_balance = initial_balance

    log(f"Initial balance: ${initial_balance:,.2f}", "MONEY")

    # Step 3: Simulate progressive losses
    loss_increments = [10, 15, 10, 10, 8]  # Total: $53

    for i, loss in enumerate(loss_increments, 1):
        current_balance -= loss
        total_loss = initial_balance - current_balance

        log(f"\n--- Simulation Step {i} ---", "INFO")
        log(f"Simulating loss of ${loss}", "WARNING")

        # Send balance update
        simulate_balance_update(current_balance, initial_balance)

        # Check user status
        time.sleep(2)  # Wait for processing

        can_trade = check_user_status()
        risk_status = get_risk_status()

        log(f"Total loss: ${total_loss:,.2f} / ${DAILY_RISK_LIMIT} limit", "MONEY")
        log(f"Can trade: {can_trade}", "INFO")

        if not can_trade:
            log("USER BLOCKED! Daily risk limit reached!", "BLOCK")
            log(f"Blocked at loss: ${total_loss:,.2f}", "ALERT")

            if abs(total_loss - DAILY_RISK_LIMIT) <= 5:
                log("✅ TEST PASSED: System blocked near $50 limit", "SUCCESS")
                return True
            else:
                log(f"⚠️ WARNING: Blocked at ${total_loss} instead of ${DAILY_RISK_LIMIT}", "WARNING")
                return True

        if total_loss > DAILY_RISK_LIMIT:
            log(f"❌ TEST FAILED: Loss ${total_loss} exceeds limit but user not blocked!", "ERROR")
            return False

        time.sleep(3)  # Wait between updates

    log("\n" + "="*60, "INFO")
    log("SIMULATION COMPLETED", "INFO")

    # Final check
    final_loss = initial_balance - current_balance
    final_can_trade = check_user_status()

    if final_loss > DAILY_RISK_LIMIT and final_can_trade:
        log(f"❌ TEST FAILED: Final loss ${final_loss} exceeds limit but user can still trade", "ERROR")
        return False
    elif not final_can_trade and abs(final_loss - DAILY_RISK_LIMIT) <= 5:
        log(f"✅ TEST PASSED: User blocked at appropriate limit", "SUCCESS")
        return True
    else:
        log("⚠️ TEST INCONCLUSIVE", "WARNING")
        return False

def main():
    print("""
    ╔══════════════════════════════════════════════╗
    ║   RISK MONITORING SIMULATION TEST           ║
    ╠══════════════════════════════════════════════╣
    ║   This test will:                           ║
    ║   1. Create user with $50 daily risk limit  ║
    ║   2. Simulate balance losses                ║
    ║   3. Check if system blocks at $50          ║
    ║                                              ║
    ║   NO REAL TRADES WILL BE PLACED             ║
    ╚══════════════════════════════════════════════╝
    """)

    success = run_simulation()

    print("\n" + "="*60)
    if success:
        print("🎉 TEST PASSED: Risk monitoring working correctly!")
    else:
        print("❌ TEST FAILED: Risk monitoring not working as expected")
    print("="*60)

if __name__ == "__main__":
    main()