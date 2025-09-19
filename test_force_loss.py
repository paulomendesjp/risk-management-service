#!/usr/bin/env python3
"""
Force Loss Test for Risk Monitoring
====================================
This script forces immediate realized losses to test the risk monitoring system.
It uses spot trading (not futures) to ensure immediate balance impact.
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
TEST_USER_ID = "force_loss_test"
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
        "LOSS": "📉",
        "BLOCK": "🚫"
    }
    timestamp = datetime.now().strftime("%H:%M:%S")
    print(f"[{timestamp}] {emojis.get(level, '📝')} {msg}")

async def create_test_user():
    """Create test user with risk limits"""
    log(f"Creating user: {TEST_USER_ID} with ${DAILY_LIMIT} daily limit")

    # Delete if exists
    try:
        requests.delete(f"{USER_SERVICE}/api/users/{TEST_USER_ID}")
    except:
        pass

    # Create user with risk limits
    user_data = {
        "clientId": TEST_USER_ID,
        "name": "Force Loss Test",
        "email": "forceloss@test.com",
        "apiKey": API_KEY,
        "apiSecret": API_SECRET,
        "maxRisk": {
            "type": "AMOUNT",
            "value": 100.0  # $100 max risk
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

async def force_immediate_loss(client, account_id, loss_amount=20):
    """
    Force an immediate realized loss through spot trading
    Uses market orders with slippage to guarantee loss
    """
    try:
        log(f"Forcing ${loss_amount} immediate loss", "LOSS")

        # Strategy: Buy high, sell low immediately (spot trading)
        # This realizes the loss immediately in the balance

        # Step 1: Buy BTC at market price (slightly above ask)
        ticker = await client.get_ticker("BTC/USD")
        current_price = float(ticker.ask)

        # Calculate quantity for target loss
        # We'll buy and immediately sell at a loss
        quantity = loss_amount / (current_price * 0.01)  # 1% loss per trade
        quantity = round(quantity, 6)  # Round to 6 decimal places

        log(f"BTC Ask Price: ${current_price:,.2f}", "INFO")
        log(f"Buying {quantity:.6f} BTC at market", "TRADE")

        # Buy BTC
        buy_order = await client.place_order(
            symbol="BTC/USD",
            side="buy",
            order_type="market",
            quantity=quantity
        )

        if hasattr(buy_order, 'order_id'):
            log(f"Buy order executed: {buy_order.order_id}", "SUCCESS")

        # Wait for order to fill
        await asyncio.sleep(2)

        # Step 2: Immediately sell at a lower price (market order)
        log(f"Selling {quantity:.6f} BTC at market (forcing loss)", "TRADE")

        # Place limit sell order below current bid to ensure loss
        ticker = await client.get_ticker("BTC/USD")
        bid_price = float(ticker.bid)
        sell_price = bid_price * 0.99  # Sell 1% below bid

        sell_order = await client.place_order(
            symbol="BTC/USD",
            side="sell",
            order_type="limit",
            quantity=quantity,
            price=sell_price,
            time_in_force="IOC"  # Immediate or cancel
        )

        if hasattr(sell_order, 'order_id'):
            log(f"Sell order executed: {sell_order.order_id}", "SUCCESS")

        # If limit order didn't fill, use market order
        await asyncio.sleep(1)

        # Check if we still have BTC position
        positions = await client.get_positions()
        for pos in positions:
            if pos.symbol == "BTC/USD" and pos.quantity > 0:
                log("Limit sell didn't fill, using market order", "WARNING")
                market_sell = await client.place_order(
                    symbol="BTC/USD",
                    side="sell",
                    order_type="market",
                    quantity=pos.quantity
                )
                log(f"Market sell executed: {market_sell.order_id}", "SUCCESS")

        # Calculate actual loss
        actual_loss = quantity * current_price * 0.01  # Approximate 1% loss
        log(f"Forced loss of approximately ${actual_loss:.2f}", "LOSS")

        return actual_loss

    except Exception as e:
        log(f"Error forcing loss: {e}", "ERROR")
        return 0

async def check_monitoring_status():
    """Check risk monitoring status"""
    resp = requests.get(f"{RISK_SERVICE}/api/risk/status/{TEST_USER_ID}")
    if resp.status_code == 200:
        data = resp.json().get('data', {})
        return {
            'balance': data.get('currentBalance', 0),
            'initial': data.get('initialBalance', 0),
            'realized_pnl': data.get('realizedPnl', 0),
            'unrealized_pnl': data.get('unrealizedPnl', 0),
            'can_trade': data.get('canTrade', True)
        }
    return None

async def check_can_trade():
    """Check if user can still trade"""
    resp = requests.get(f"{USER_SERVICE}/api/users/{TEST_USER_ID}/can-trade")
    if resp.status_code == 200:
        return resp.json()
    return True

async def main():
    """Main test execution"""
    log("="*60, "INFO")
    log("FORCE LOSS TEST - RISK MONITORING VALIDATION", "INFO")
    log(f"Target: Force immediate losses to test ${DAILY_LIMIT} limit", "INFO")
    log("="*60, "INFO")

    # Step 1: Create test user
    if not await create_test_user():
        return False

    # Step 2: Wait for monitoring to initialize
    log("Waiting for risk monitoring to initialize...", "INFO")
    await asyncio.sleep(10)

    # Trigger initial balance update
    resp = requests.post(f"{RISK_SERVICE}/api/risk/update-balance/{TEST_USER_ID}")
    if resp.status_code == 200:
        log("Risk monitoring activated", "SUCCESS")

    await asyncio.sleep(5)

    # Step 3: Connect to Architect
    log("Connecting to Architect API...", "INFO")
    client = await AsyncClient.connect(
        api_key=API_KEY,
        api_secret=API_SECRET,
        paper_trading=False  # Use real account for actual balance impact
    )

    # Get account info
    accounts = await client.list_accounts()
    if not accounts:
        log("No accounts found", "ERROR")
        return False

    account_id = accounts[0].account_id if hasattr(accounts[0], 'account_id') else accounts[0].account

    # Get initial balance
    account_summary = await client.get_account_summary(account_id)
    initial_balance = float(account_summary.equity) if hasattr(account_summary, 'equity') else 100000
    log(f"Initial balance: ${initial_balance:,.2f}", "MONEY")

    # Step 4: Force losses in increments
    total_loss = 0
    loss_increments = [10, 10, 10, 10, 15]  # Total: $55 (exceeds $50 limit)

    for i, loss_amount in enumerate(loss_increments, 1):
        log(f"\n--- Trade #{i} - Target Loss: ${loss_amount} ---", "INFO")

        # Check if can still trade
        can_trade = await check_can_trade()
        if not can_trade:
            log("USER BLOCKED by risk monitoring!", "BLOCK")
            log(f"Total loss at block: ${total_loss:.2f}", "MONEY")

            if 45 <= total_loss <= 55:  # Within reasonable range of $50
                log("✅ TEST PASSED: Blocked at correct limit", "SUCCESS")
            else:
                log(f"⚠️ Blocked at ${total_loss} instead of ${DAILY_LIMIT}", "WARNING")
            break

        # Force the loss
        actual_loss = await force_immediate_loss(client, account_id, loss_amount)
        total_loss += actual_loss

        # Wait for balance update
        await asyncio.sleep(5)

        # Check monitoring status
        status = await check_monitoring_status()
        if status:
            current_balance = status['balance']
            realized_loss = initial_balance - current_balance
            log(f"Current balance: ${current_balance:,.2f}", "MONEY")
            log(f"Realized loss: ${realized_loss:.2f} / ${DAILY_LIMIT} limit", "MONEY")

            # Check if approaching limit
            loss_percentage = (realized_loss / DAILY_LIMIT) * 100
            if loss_percentage >= 90:
                log(f"🚨 CRITICAL: {loss_percentage:.1f}% of limit reached", "WARNING")
            elif loss_percentage >= 80:
                log(f"⚠️ WARNING: {loss_percentage:.1f}% of limit reached", "WARNING")

        # Safety check
        if total_loss >= DAILY_LIMIT:
            log(f"Loss ${total_loss:.2f} exceeds limit, checking block...", "WARNING")
            await asyncio.sleep(5)

            can_trade = await check_can_trade()
            if not can_trade:
                log("✅ User correctly blocked after exceeding limit!", "BLOCK")
            else:
                log("❌ ERROR: Limit exceeded but user not blocked!", "ERROR")
            break

    # Clean up
    await client.close()

    log("\n" + "="*60, "INFO")
    log("TEST COMPLETED", "INFO")
    log(f"Final total loss: ${total_loss:.2f}", "MONEY")
    log("="*60, "INFO")

    return True

if __name__ == "__main__":
    print("""
    ╔══════════════════════════════════════════════╗
    ║        💸 FORCE LOSS TEST 💸                 ║
    ╠══════════════════════════════════════════════╣
    ║                                              ║
    ║  This test will:                             ║
    ║  1. Create a test user with $50 daily limit  ║
    ║  2. Force immediate realized losses          ║
    ║  3. Verify risk monitoring blocks at limit   ║
    ║                                              ║
    ║  Method: Buy high, sell low (spot trading)   ║
    ║  Expected: Block at ~$50 loss                ║
    ║                                              ║
    ╚══════════════════════════════════════════════╝
    """)

    confirm = input("⚠️  Continue with force loss test? (yes/no): ")
    if confirm.lower() == 'yes':
        asyncio.run(main())
    else:
        print("Test cancelled")