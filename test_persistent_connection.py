#!/usr/bin/env python3
import asyncio
import websockets
import json
from datetime import datetime
import signal
import sys

running = True

def signal_handler(sig, frame):
    global running
    running = False
    print("\n\n❌ Interrompido pelo usuário")
    sys.exit(0)

signal.signal(signal.SIGINT, signal_handler)

async def test_persistent_connection():
    url = "ws://localhost:8090/ws/realtime?api_key=9PbxIkyZvDKbm8QTcj28vvBR&api_secret=3RJgViUUVvrJ8dcqaTmGTHo2zQEkbcKg1VPEmLxqrYqj"
    
    print("🔗 Testando conexão persistente do WebSocket...")
    print("=" * 60)
    
    try:
        async with websockets.connect(url) as ws:
            print(f"✅ CONECTADO às {datetime.now().strftime('%H:%M:%S')}")
            
            # Recebe primeira mensagem
            msg = await ws.recv()
            data = json.loads(msg)
            
            if data.get("type") == "CONNECTION":
                print(f"✅ Confirmação recebida: {data.get('message')}")
                print(f"   Account ID: {data.get('accountId')}")
            
            print("\n📊 Monitorando conexão (aguardando 20 segundos)...")
            print("-" * 60)
            
            # Monitora por 20 segundos
            start_time = asyncio.get_event_loop().time()
            messages_received = 0
            
            while asyncio.get_event_loop().time() - start_time < 20:
                try:
                    msg = await asyncio.wait_for(ws.recv(), timeout=1.0)
                    data = json.loads(msg)
                    messages_received += 1
                    
                    timestamp = datetime.now().strftime("%H:%M:%S")
                    msg_type = data.get("type", "UNKNOWN")
                    
                    if msg_type == "BALANCE_UPDATE":
                        print(f"[{timestamp}] 💰 Balance: ${data.get('totalBalance', 0):,.2f}")
                    elif msg_type == "PNL_UPDATE":
                        print(f"[{timestamp}] 💹 P&L: ${data.get('totalUnrealizedPnl', 0):,.2f}")
                    else:
                        print(f"[{timestamp}] 📡 {msg_type}")
                        
                except asyncio.TimeoutError:
                    print(".", end="", flush=True)
                    
            print("\n" + "=" * 60)
            
            # Verifica se ainda está conectado
            if ws.open:
                print(f"✅ AINDA CONECTADO após 20 segundos!")
                print(f"   Mensagens recebidas: {messages_received}")
                
                # Testa envio de ping
                await ws.ping()
                print("✅ Ping enviado com sucesso")
                
                return True
            else:
                print("❌ Conexão perdida")
                return False
                
    except Exception as e:
        print(f"❌ Erro: {e}")
        return False

if __name__ == "__main__":
    result = asyncio.run(test_persistent_connection())
    
    if result:
        print("\n🎉 TESTE CONCLUÍDO: WebSocket mantém conexão!")
    else:
        print("\n❌ TESTE FALHOU: WebSocket não manteve conexão")
