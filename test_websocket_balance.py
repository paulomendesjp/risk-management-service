#!/usr/bin/env python3
"""
Test WebSocket balance streaming endpoint
"""
import asyncio
import websockets
import json
from datetime import datetime

async def test_balance_websocket():
    # Your real API credentials
    api_key = "9PbxIkyZvDKbm8QTcj28vvBR"
    api_secret = "3RJgViUUVvrJ8dcqaTmGTHo2zQEkbcKg1VPEmLxqrYqj"

    # WebSocket URL
    ws_url = f"ws://localhost:8090/ws/balance?api_key={api_key}&api_secret={api_secret}"

    print(f"🚀 Connecting to WebSocket: {ws_url.split('?')[0]}")

    try:
        async with websockets.connect(ws_url) as websocket:
            print("✅ WebSocket connected!")

            # Listen for messages for 30 seconds
            timeout = 30
            start_time = datetime.now()

            while (datetime.now() - start_time).seconds < timeout:
                try:
                    # Wait for message with 5 second timeout
                    message = await asyncio.wait_for(websocket.recv(), timeout=5.0)
                    data = json.loads(message)

                    print(f"📡 Received: {data['type']}")

                    if data['type'] == 'CONNECTION':
                        print(f"   Status: {data.get('status')}")
                        print(f"   Account ID: {data.get('accountId')}")
                        print(f"   Message: {data.get('message')}")

                    elif data['type'] == 'BALANCE_UPDATE':
                        print(f"   💰 Balance: ${data.get('totalBalance')}")
                        print(f"   💰 Available: ${data.get('availableBalance')}")
                        print(f"   📊 Unrealized P&L: ${data.get('unrealizedPnl')}")
                        print(f"   📅 Timestamp: {data.get('timestamp')}")
                        if data.get('previousBalance'):
                            print(f"   📈 Change: ${data.get('totalBalance') - data.get('previousBalance')}")

                    elif data['type'] == 'ERROR':
                        print(f"   ❌ Error: {data.get('message')}")
                        break

                except asyncio.TimeoutError:
                    print("⏱️ No message received in 5 seconds, continuing...")
                    continue
                except Exception as e:
                    print(f"❌ Error receiving message: {e}")
                    break

            print(f"✅ Test completed after {timeout} seconds")

    except Exception as e:
        print(f"❌ Connection failed: {e}")

if __name__ == "__main__":
    print("🧪 Testing WebSocket Balance Streaming")
    asyncio.run(test_balance_websocket())