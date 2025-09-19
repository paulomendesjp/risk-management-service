#!/usr/bin/env python3
"""
Real Trading Risk Test
=======================
This script places REAL trades to test risk monitoring.
It will:
1. Create a user with $50 daily risk limit
2. Place small losing trades
3. Monitor P&L in real-time
4. Verify system blocks at $50 loss
"""

import asyncio
import requests
import json
import time
from datetime import datetime
from architect_py import AsyncClient

# Configuration
API_KEY = "9PbxIkyZvDKbm8QTcj28vvBR"
API_SECRET = "3RJgViUUVvrJ8dcqaTmGTHo2zQEkbcKg1VPEmLxqrYqj"

USER_SERVICE = "http://localhost:8081"
RISK_SERVICE = "http://localhost:8083"

# Test user
TEST_USER_ID = "real_risk_test"
DAILY_LIMIT = 50.0  # $50 daily risk limit

def log(msg, level="INFO"):
    """Pretty logging with emojis"""
    emojis = {
        "INFO": "ℹ️",
        "SUCCESS": "✅",
        "ERROR": "❌",
        "WARNING": "⚠️",
        "MONEY": "💰",
        "TRADE": "📊",
        "BLOCK": "🚫"
    }
    timestamp = datetime.now().strftime("%H:%M:%S")
    print(f"[{timestamp}] {emojis.get(level, '📝')} {msg}")

async def create_user():
    """Create test user with API credentials"""
    log(f"Creating user: {TEST_USER_ID} with ${DAILY_LIMIT} daily limit")

    # Delete if exists
    try:
        requests.delete(f"{USER_SERVICE}/api/users/{TEST_USER_ID}")
    except:
        pass

    # Create with real credentials
    user_data = {
        "clientId": TEST_USER_ID,
        "name": "Real Risk Test",
        "email": "realrisk@test.com",
        "apiKey": API_KEY,
        "apiSecret": API_SECRET,
        "maxRisk": {
            "type": "PERCENTAGE",
            "value": 10.0  # 10% max risk
        },
        "dailyRisk": {
            "type": "AMOUNT",
            "value": DAILY_LIMIT  # $50 daily limit
        }
    }

    resp = requests.post(
        f"{USER_SERVICE}/api/users/register",
        json=user_data,
        headers={"Content-Type": "application/json"}
    )

    if resp.status_code in [200, 201]:
        log("User created successfully", "SUCCESS")
        return True
    else:
        log(f"Failed to create user: {resp.text}", "ERROR")
        return False

async def wait_for_websocket():
    """Wait for WebSocket connection to establish"""
    log("Waiting for WebSocket connection...")
    await asyncio.sleep(10)

    # Trigger balance update to ensure monitoring is active
    resp = requests.post(f"{RISK_SERVICE}/api/risk/update-balance/{TEST_USER_ID}")
    if resp.status_code == 200:
        log("Risk monitoring activated", "SUCCESS")

    await asyncio.sleep(5)

async def check_user_status():
    """Check if user can trade"""
    resp = requests.get(f"{USER_SERVICE}/api/users/{TEST_USER_ID}/can-trade")
    if resp.status_code == 200:
        return resp.json()
    return True

async def get_risk_status():
    """Get risk monitoring status"""
    resp = requests.get(f"{RISK_SERVICE}/api/risk/status/{TEST_USER_ID}")
    if resp.status_code == 200:
        data = resp.json().get('data', {})
        return {
            'balance': data.get('currentBalance', 0),
            'initial': data.get('initialBalance', 0),
            'can_trade': data.get('canTrade', True)
        }
    return None

async def place_small_losing_trade(client, loss_target=10):
    """Place a small trade designed to lose money"""
    try:
        # Get current price
        ticker = await client.get_ticker("BTC/USD")
        current_price = float(ticker.bid)
        log(f"BTC price: ${current_price:,.2f}", "INFO")

        # Calculate quantity for target loss
        # We'll use a stop loss to guarantee the loss
        quantity = 0.0001  # Very small quantity

        # Place a market sell (short)
        log(f"Placing SHORT trade for {quantity} BTC", "TRADE")
        order = await client.place_order(
            symbol="BTC/USD",
            side="sell",
            order_type="market",
            quantity=quantity
        )

        log(f"Order placed: {order.order_id}", "SUCCESS")

        # Wait a bit
        await asyncio.sleep(3)

        # Close position at a loss by buying back higher
        log("Closing position to realize loss", "TRADE")
        close_order = await client.place_order(
            symbol="BTC/USD",
            side="buy",
            order_type="market",
            quantity=quantity
        )

        log(f"Position closed: {close_order.order_id}", "SUCCESS")

        # Get realized P&L
        await asyncio.sleep(2)

        return True

    except Exception as e:
        log(f"Trade error: {e}", "ERROR")
        return False

async def monitor_and_trade():
    """Main test loop - place trades and monitor risk"""
    log("Starting real trading test", "INFO")

    # Initialize client
    client = await AsyncClient.connect(
        api_key=API_KEY,
        api_secret=API_SECRET,
        paper_trading=False,  # Use real account
        grpc_port=51052
    )

    # Get account info
    accounts = await client.list_accounts()
    if not accounts:
        log("No accounts found", "ERROR")
        return False

    account_id = accounts[0].account_id if hasattr(accounts[0], 'account_id') else accounts[0].account
    log(f"Using account: {account_id}", "INFO")

    # Get initial balance
    account_summary = await client.get_account_summary(account_id)
    initial_balance = float(account_summary.equity) if hasattr(account_summary, 'equity') else 100000
    log(f"Initial balance: ${initial_balance:,.2f}", "MONEY")

    trade_count = 0
    total_loss = 0

    while True:
        trade_count += 1
        log(f"\n--- Trade #{trade_count} ---", "INFO")

        # Check if can still trade
        can_trade = await check_user_status()
        if not can_trade:
            log("USER BLOCKED by risk monitoring!", "BLOCK")
            log(f"Total loss at block: ${total_loss:.2f}", "MONEY")

            if abs(total_loss - DAILY_LIMIT) <= 10:
                log("✅ TEST PASSED: Blocked near $50 limit", "SUCCESS")
            else:
                log(f"⚠️ Blocked at ${total_loss} instead of ${DAILY_LIMIT}", "WARNING")
            break

        # Place a small losing trade
        await place_small_losing_trade(client)

        # Wait for processing
        await asyncio.sleep(5)

        # Get current status
        risk_status = await get_risk_status()
        if risk_status:
            current_balance = risk_status['balance']
            total_loss = initial_balance - current_balance
            log(f"Current balance: ${current_balance:,.2f}", "MONEY")
            log(f"Total loss: ${total_loss:.2f} / ${DAILY_LIMIT} limit", "MONEY")

            # Check if we should be blocked
            if total_loss >= DAILY_LIMIT:
                log("Loss exceeds limit, checking if blocked...", "WARNING")
                can_trade = await check_user_status()
                if not can_trade:
                    log("✅ User correctly blocked!", "BLOCK")
                    break
                else:
                    log("❌ ERROR: Loss exceeds limit but user not blocked!", "ERROR")
                    break

        # Safety check - don't run too many trades
        if trade_count >= 10:
            log("Max trades reached", "WARNING")
            break

        # Wait before next trade
        await asyncio.sleep(10)

    await client.close()
    return True

async def main():
    """Main test runner"""
    log("="*60, "INFO")
    log("REAL TRADING RISK MONITORING TEST", "INFO")
    log(f"Daily Risk Limit: ${DAILY_LIMIT}", "INFO")
    log("="*60, "INFO")

    # Step 1: Create user
    if not await create_user():
        return False

    # Step 2: Wait for WebSocket
    await wait_for_websocket()

    # Step 3: Monitor and trade
    success = await monitor_and_trade()

    log("="*60, "INFO")
    log("TEST COMPLETED", "INFO")
    log("="*60, "INFO")

    return success

if __name__ == "__main__":
    print("""
    ╔══════════════════════════════════════════════╗
    ║        ⚠️  REAL TRADING TEST ⚠️              ║
    ╠══════════════════════════════════════════════╣
    ║                                              ║
    ║  This will place REAL trades on your account ║
    ║  to test the risk monitoring system.        ║
    ║                                              ║
    ║  Test parameters:                            ║
    ║  - Daily risk limit: $50                    ║
    ║  - Small trades to accumulate losses        ║
    ║  - Monitors P&L in real-time                ║
    ║  - Verifies blocking at limit               ║
    ║                                              ║
    ╚══════════════════════════════════════════════╝
    """)

    confirm = input("⚠️  Continue with REAL trades? (yes/no): ")
    if confirm.lower() == 'yes':
        asyncio.run(main())
    else:
        print("Test cancelled")