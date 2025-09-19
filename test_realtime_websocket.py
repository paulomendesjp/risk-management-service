#!/usr/bin/env python3
"""
Test Real-time WebSocket streaming endpoint
Tests orderflow streaming and position P&L monitoring
"""
import asyncio
import websockets
import json
from datetime import datetime

async def test_realtime_websocket():
    # Your real API credentials
    api_key = "9PbxIkyZvDKbm8QTcj28vvBR"
    api_secret = "3RJgViUUVvrJ8dcqaTmGTHo2zQEkbcKg1VPEmLxqrYqj"

    # Real-time WebSocket URL
    ws_url = f"ws://localhost:8090/ws/realtime?api_key={api_key}&api_secret={api_secret}"

    print(f"🚀 Connecting to Real-time WebSocket: {ws_url.split('?')[0]}")
    print("="*60)

    try:
        async with websockets.connect(ws_url) as websocket:
            print("✅ Real-time WebSocket connected!")
            print("="*60)

            # Listen for messages continuously
            message_count = 0
            balance_updates = 0
            pnl_updates = 0

            while True:
                try:
                    # Wait for message with timeout
                    message = await asyncio.wait_for(websocket.recv(), timeout=60.0)
                    data = json.loads(message)
                    message_count += 1

                    print(f"\n[{datetime.now().strftime('%H:%M:%S')}] Message #{message_count}")
                    print(f"📡 Type: {data['type']}")

                    if data['type'] == 'CONNECTION':
                        print(f"   ✅ Status: {data.get('status')}")
                        print(f"   📊 Account ID: {data.get('accountId')}")
                        print(f"   💬 Message: {data.get('message')}")

                    elif data['type'] == 'BALANCE_UPDATE':
                        balance_updates += 1
                        source = data.get('source', 'unknown')
                        trigger = data.get('trigger', '')

                        print(f"   💰 BALANCE UPDATE #{balance_updates}")
                        print(f"   📍 Source: {source} {f'({trigger})' if trigger else ''}")
                        print(f"   💵 Total Balance: ${data.get('totalBalance', 0):,.2f}")
                        print(f"   💳 Available: ${data.get('availableBalance', 0):,.2f}")
                        print(f"   📈 Unrealized P&L: ${data.get('unrealizedPnl', 0):,.2f}")
                        print(f"   💹 Realized P&L: ${data.get('realizedPnl', 0):,.2f}")

                        if data.get('positionMargin'):
                            print(f"   🔒 Position Margin: ${data.get('positionMargin', 0):,.2f}")
                        if data.get('totalMargin'):
                            print(f"   🔐 Total Margin: ${data.get('totalMargin', 0):,.2f}")

                        if data.get('previousBalance'):
                            change = data.get('totalBalance', 0) - data.get('previousBalance', 0)
                            print(f"   📊 Change: ${change:+,.2f}")

                    elif data['type'] == 'PNL_UPDATE':
                        pnl_updates += 1
                        print(f"   💹 P&L UPDATE #{pnl_updates}")
                        print(f"   📊 Total Unrealized P&L: ${data.get('totalUnrealizedPnl', 0):,.2f}")
                        print(f"   💵 Total Balance: ${data.get('totalBalance', 0):,.2f}")

                        positions = data.get('positions', [])
                        if positions:
                            print(f"   📈 Positions ({len(positions)}):")
                            for pos in positions:
                                print(f"      • {pos['symbol']}: ${pos['unrealizedPnl']:,.2f}")
                        else:
                            print(f"   📉 No open positions")

                    elif data['type'] == 'ERROR':
                        print(f"   ❌ Error: {data.get('message')}")

                    print("-"*60)

                    # Show statistics every 10 messages
                    if message_count % 10 == 0:
                        print(f"\n📊 STATISTICS:")
                        print(f"   Total Messages: {message_count}")
                        print(f"   Balance Updates: {balance_updates}")
                        print(f"   P&L Updates: {pnl_updates}")
                        print("="*60)

                except asyncio.TimeoutError:
                    print(f"\n⏱️ No message received in 60 seconds")
                    print("💡 This is normal if no trades/P&L changes occurred")
                    print("💡 Try placing a trade to see instant updates!")
                    continue

                except KeyboardInterrupt:
                    print("\n\n👋 Test interrupted by user")
                    break

                except Exception as e:
                    print(f"\n❌ Error receiving message: {e}")
                    break

            print(f"\n✅ Test completed - Received {message_count} messages")

    except Exception as e:
        print(f"❌ Connection failed: {e}")

if __name__ == "__main__":
    print("🧪 Testing Real-time WebSocket Streaming")
    print("="*60)
    print("This test will:")
    print("1. Connect to /ws/realtime endpoint")
    print("2. Receive instant balance updates on trades (orderflow)")
    print("3. Monitor P&L changes every second")
    print("4. Fallback balance check every 5 seconds")
    print("="*60)

    try:
        asyncio.run(test_realtime_websocket())
    except KeyboardInterrupt:
        print("\n👋 Test stopped by user")