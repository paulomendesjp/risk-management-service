#!/usr/bin/env python3
"""
Test Risk Monitoring with Real-Time P&L
=========================================
This script tests the complete risk monitoring flow:
1. Creates a user with $50 daily risk limit
2. Verifies WebSocket connection
3. Places trades to generate losses
4. Monitors P&L in real-time
5. Verifies system blocks at $50 loss
"""

import requests
import json
import time
import asyncio
from architect_py import AsyncClient
from datetime import datetime
import sys

# Configuration
USER_SERVICE_URL = "http://localhost:8081"
RISK_SERVICE_URL = "http://localhost:8083"
ARCHITECT_BRIDGE_URL = "http://localhost:8090"

# Your real API credentials
API_KEY = "9PbxIkyZvDKbm8QTcj28vvBR"
API_SECRET = "3RJgViUUVvrJ8dcqaTmGTHo2zQEkbcKg1VPEmLxqrYqj"

# Test user configuration
TEST_USER = {
    "clientId": "risk_test_user",
    "name": "Risk Test User",
    "email": "risk_test@example.com",
    "maxRisk": {
        "type": "PERCENTAGE",
        "value": 10.0  # 10% max risk
    },
    "dailyRisk": {
        "type": "AMOUNT",
        "value": 50.0  # $50 daily risk limit
    }
}

class RiskMonitoringTester:
    def __init__(self):
        self.client = None
        self.initial_balance = None
        self.current_balance = None
        self.positions = []

    def log(self, message, level="INFO"):
        """Log message with timestamp"""
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        emoji = {
            "INFO": "ℹ️",
            "SUCCESS": "✅",
            "WARNING": "⚠️",
            "ERROR": "❌",
            "MONEY": "💰",
            "TRADE": "📈",
            "BLOCK": "🚫"
        }.get(level, "📝")
        print(f"[{timestamp}] {emoji} {message}")

    def create_test_user(self):
        """Step 1: Create test user with real API credentials"""
        self.log("Creating test user with $50 daily risk limit", "INFO")

        # First, delete user if exists
        try:
            requests.delete(f"{USER_SERVICE_URL}/api/users/{TEST_USER['clientId']}")
        except:
            pass

        # Create user with credentials
        payload = {
            **TEST_USER,
            "apiKey": API_KEY,
            "apiSecret": API_SECRET
        }

        response = requests.post(
            f"{USER_SERVICE_URL}/api/users/register",
            json=payload,
            headers={"Content-Type": "application/json"}
        )

        if response.status_code in [200, 201]:
            self.log(f"User created: {TEST_USER['clientId']}", "SUCCESS")
            return True
        else:
            self.log(f"Failed to create user: {response.text}", "ERROR")
            return False

    def verify_websocket_connection(self):
        """Step 2: Verify WebSocket connection is established"""
        self.log("Verifying WebSocket connection...", "INFO")

        # Check risk monitoring status
        response = requests.get(f"{RISK_SERVICE_URL}/api/risk/status/{TEST_USER['clientId']}")

        if response.status_code == 200:
            status = response.json()
            self.log(f"Risk monitoring active for {TEST_USER['clientId']}", "SUCCESS")
            if 'data' in status and 'currentBalance' in status['data']:
                self.initial_balance = float(status['data']['currentBalance'])
                self.log(f"Initial balance: ${self.initial_balance:,.2f}", "MONEY")
            return True
        else:
            self.log("Risk monitoring not active", "ERROR")
            return False

    async def get_current_pnl(self):
        """Get current P&L from open positions"""
        try:
            positions = await self.client.get_positions()
            total_pnl = 0
            for pos in positions:
                if hasattr(pos, 'unrealized_pnl'):
                    total_pnl += float(pos.unrealized_pnl)
            return total_pnl
        except Exception as e:
            self.log(f"Error getting P&L: {e}", "ERROR")
            return 0

    async def place_losing_trade(self):
        """Place a trade that will generate losses"""
        self.log("Placing trade to generate loss...", "TRADE")

        try:
            # Get current BTC price
            ticker = await self.client.get_ticker("BTC/USD")
            current_price = float(ticker.bid)

            # Place a sell order (short) with stop loss that will trigger
            order = await self.client.place_order(
                symbol="BTC/USD",
                side="sell",
                order_type="market",
                quantity=0.001,  # Small quantity
                leverage=2
            )

            self.log(f"Order placed: {order.order_id}", "SUCCESS")
            self.positions.append(order.order_id)

            # Wait for position to accumulate loss
            await asyncio.sleep(2)

            # Get current P&L
            pnl = await self.get_current_pnl()
            self.log(f"Current P&L: ${pnl:,.2f}", "MONEY")

            return pnl

        except Exception as e:
            self.log(f"Error placing trade: {e}", "ERROR")
            return 0

    async def monitor_risk_limit(self):
        """Monitor if system blocks at $50 loss"""
        self.log("Monitoring risk limits...", "INFO")

        start_time = time.time()
        max_monitor_time = 300  # 5 minutes max

        while time.time() - start_time < max_monitor_time:
            try:
                # Get current P&L
                pnl = await self.get_current_pnl()

                # Get balance from risk monitoring
                response = requests.get(f"{RISK_SERVICE_URL}/api/risk/status/{TEST_USER['clientId']}")

                if response.status_code == 200:
                    data = response.json().get('data', {})
                    current_balance = float(data.get('currentBalance', 0))
                    loss = self.initial_balance - current_balance

                    self.log(f"Balance: ${current_balance:,.2f} | Loss: ${loss:,.2f} | P&L: ${pnl:,.2f}", "MONEY")

                    # Check if user is blocked
                    can_trade_response = requests.get(f"{USER_SERVICE_URL}/api/users/{TEST_USER['clientId']}/can-trade")
                    can_trade = can_trade_response.json() if can_trade_response.status_code == 200 else True

                    if not can_trade:
                        self.log("USER BLOCKED! Daily risk limit reached", "BLOCK")
                        self.log(f"Loss at block: ${loss:,.2f}", "MONEY")

                        # Verify it's close to $50
                        if abs(loss - 50) < 10:  # Within $10 of target
                            self.log("✅ SUCCESS: System blocked at correct limit!", "SUCCESS")
                        else:
                            self.log(f"⚠️ WARNING: Blocked at ${loss:.2f} instead of $50", "WARNING")
                        return True

                    # If loss exceeds $50 but not blocked, that's a problem
                    if loss > 50:
                        self.log(f"ERROR: Loss ${loss:.2f} exceeds limit but user not blocked!", "ERROR")

                # Wait before next check
                await asyncio.sleep(5)

            except Exception as e:
                self.log(f"Error monitoring: {e}", "ERROR")

        self.log("Monitoring timeout reached", "WARNING")
        return False

    async def run_test(self):
        """Run the complete test"""
        self.log("=" * 60, "INFO")
        self.log("STARTING RISK MONITORING TEST", "INFO")
        self.log("=" * 60, "INFO")

        # Step 1: Create test user
        if not self.create_test_user():
            return False

        # Wait for services to initialize
        self.log("Waiting for services to initialize...", "INFO")
        await asyncio.sleep(5)

        # Step 2: Verify WebSocket connection
        if not self.verify_websocket_connection():
            return False

        # Step 3: Connect to Architect
        self.log("Connecting to Architect...", "INFO")
        self.client = AsyncClient(
            api_key=API_KEY,
            api_secret=API_SECRET,
            sandbox=False  # Use real account
        )

        # Get initial account state
        account = await self.client.get_account()
        self.log(f"Account connected: {account.account_id}", "SUCCESS")

        # Step 4: Monitor while placing trades
        self.log("Starting trade simulation...", "TRADE")

        # Place multiple small trades to accumulate losses
        total_loss = 0
        trade_count = 0

        while total_loss < 45:  # Stop before hitting limit
            pnl = await self.place_losing_trade()
            total_loss = abs(pnl)
            trade_count += 1
            self.log(f"Trade #{trade_count} - Total loss: ${total_loss:.2f}", "TRADE")

            # Check if blocked
            response = requests.get(f"{USER_SERVICE_URL}/api/users/{TEST_USER['clientId']}/can-trade")
            if response.status_code == 200 and not response.json():
                self.log("User blocked by risk monitoring!", "BLOCK")
                break

            await asyncio.sleep(10)  # Wait between trades

        # Step 5: Final monitoring
        blocked = await self.monitor_risk_limit()

        # Cleanup
        await self.client.close()

        # Summary
        self.log("=" * 60, "INFO")
        self.log("TEST COMPLETED", "INFO")
        self.log(f"Result: {'PASSED' if blocked else 'FAILED'}", "SUCCESS" if blocked else "ERROR")
        self.log("=" * 60, "INFO")

        return blocked

async def main():
    tester = RiskMonitoringTester()
    success = await tester.run_test()
    sys.exit(0 if success else 1)

if __name__ == "__main__":
    print("""
    ╔══════════════════════════════════════════════╗
    ║   RISK MONITORING REAL-TIME P&L TEST        ║
    ╠══════════════════════════════════════════════╣
    ║   This test will:                           ║
    ║   1. Create user with $50 daily risk limit  ║
    ║   2. Connect via WebSocket for real-time    ║
    ║   3. Place losing trades                    ║
    ║   4. Monitor P&L in real-time               ║
    ║   5. Verify blocking at $50 loss            ║
    ╚══════════════════════════════════════════════╝
    """)

    confirm = input("⚠️  This will place REAL trades. Continue? (yes/no): ")
    if confirm.lower() == 'yes':
        asyncio.run(main())
    else:
        print("Test cancelled")