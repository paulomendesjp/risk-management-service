#!/usr/bin/env python3
"""
Script para testar conexão WebSocket diretamente
"""
import asyncio
import websockets
import json
from datetime import datetime

API_KEY = "9PbxIkyZvDKbm8QTcj28vvBR"
API_SECRET = "3RJgViUUVvrJ8dcqaTmGTHo2zQEkbcKg1VPEmLxqrYqj"

async def monitor_websocket():
    url = f"ws://localhost:8090/ws/realtime?api_key={API_KEY}&api_secret={API_SECRET}"
    
    print(f"🔗 Conectando ao WebSocket...")
    print(f"URL: {url.split('?')[0]}")
    print("="*60)
    
    async with websockets.connect(url) as ws:
        print("✅ WebSocket CONECTADO!")
        print("="*60)
        print("Aguardando mensagens (Ctrl+C para parar)...")
        print("-"*60)
        
        while True:
            try:
                msg = await asyncio.wait_for(ws.recv(), timeout=5)
                data = json.loads(msg)
                
                timestamp = datetime.now().strftime("%H:%M:%S")
                msg_type = data.get("type")
                
                if msg_type == "CONNECTION":
                    print(f"[{timestamp}] ✅ CONECTADO - Account: {data.get('accountId')}")
                elif msg_type == "BALANCE_UPDATE":
                    print(f"[{timestamp}] 💰 BALANCE: ${data.get('totalBalance'):,.2f}")
                elif msg_type == "PNL_UPDATE":
                    print(f"[{timestamp}] 💹 P&L: ${data.get('totalUnrealizedPnl'):,.2f}")
                else:
                    print(f"[{timestamp}] 📡 {msg_type}")
                    
            except asyncio.TimeoutError:
                print(".", end="", flush=True)

if __name__ == "__main__":
    print("\n🔍 TESTE DE CONEXÃO WEBSOCKET")
    print("="*60)
    try:
        asyncio.run(monitor_websocket())
    except KeyboardInterrupt:
        print("\n\n✅ Teste finalizado")
