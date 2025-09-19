#!/usr/bin/env python3
"""
Script para verificar status das conexões WebSocket
Mostra se o sistema está conectado e recebendo updates
"""
import asyncio
import websockets
import json
import time
from datetime import datetime

# Suas credenciais
API_KEY = "9PbxIkyZvDKbm8QTcj28vvBR"
API_SECRET = "3RJgViUUVvrJ8dcqaTmGTHo2zQEkbcKg1VPEmLxqrYqj"

async def test_balance_websocket():
    """Testa WebSocket de balance (polling 5s)"""
    url = f"ws://localhost:8090/ws/balance?api_key={API_KEY}&api_secret={API_SECRET}"

    try:
        async with websockets.connect(url) as ws:
            print("  ✅ Conectado ao /ws/balance")
            msg = await asyncio.wait_for(ws.recv(), timeout=2)
            data = json.loads(msg)
            if data.get("type") == "CONNECTION":
                print(f"  📊 Account ID: {data.get('accountId')}")
                return True
    except Exception as e:
        print(f"  ❌ Erro: {e}")
        return False

async def test_realtime_websocket():
    """Testa WebSocket real-time (streaming)"""
    url = f"ws://localhost:8090/ws/realtime?api_key={API_KEY}&api_secret={API_SECRET}"

    try:
        async with websockets.connect(url) as ws:
            print("  ✅ Conectado ao /ws/realtime")
            msg = await asyncio.wait_for(ws.recv(), timeout=2)
            data = json.loads(msg)
            if data.get("type") == "CONNECTION":
                print(f"  📊 Account ID: {data.get('accountId')}")
                print(f"  💬 Status: {data.get('message')}")

                # Espera por um update
                try:
                    msg = await asyncio.wait_for(ws.recv(), timeout=6)
                    data = json.loads(msg)
                    if data.get("type") == "BALANCE_UPDATE":
                        print(f"  💰 Balance: ${data.get('totalBalance', 0):,.2f}")
                        print(f"  📈 Source: {data.get('source', 'unknown')}")
                    elif data.get("type") == "PNL_UPDATE":
                        print(f"  💹 P&L Update recebido")
                        print(f"  📊 Total P&L: ${data.get('totalUnrealizedPnl', 0):,.2f}")
                except asyncio.TimeoutError:
                    print("  ⏱️ Aguardando updates... (normal se não há trades)")

                return True
    except Exception as e:
        print(f"  ❌ Erro: {e}")
        return False

def check_services():
    """Verifica se os serviços estão rodando"""
    import subprocess

    print("\n🔍 VERIFICANDO SERVIÇOS:")
    print("=" * 50)

    services = ["architect-bridge", "risk-monitoring-service", "user-service"]
    running = {}

    for service in services:
        try:
            result = subprocess.run(
                ["docker", "ps", "--filter", f"name={service}", "--format", "{{.Status}}"],
                capture_output=True,
                text=True
            )
            if result.stdout.strip():
                status = result.stdout.strip()
                print(f"✅ {service:25} {status}")
                running[service] = True
            else:
                print(f"❌ {service:25} NOT RUNNING")
                running[service] = False
        except:
            print(f"❌ {service:25} UNKNOWN")
            running[service] = False

    return running

def check_recent_logs():
    """Verifica logs recentes do architect-bridge"""
    import subprocess

    print("\n📋 LOGS RECENTES DO ARCHITECT-BRIDGE:")
    print("=" * 50)

    try:
        result = subprocess.run(
            ["docker", "logs", "architect-bridge", "--tail", "15"],
            capture_output=True,
            text=True,
            stderr=subprocess.STDOUT
        )

        lines = result.stdout.strip().split('\n')
        for line in lines:
            if "WebSocket" in line or "connected" in line or "ERROR" in line:
                if "ERROR" in line:
                    print(f"  ❌ {line}")
                elif "connected" in line:
                    print(f"  ✅ {line}")
                else:
                    print(f"  📡 {line}")
    except Exception as e:
        print(f"  ❌ Erro ao buscar logs: {e}")

async def monitor_realtime(duration=30):
    """Monitora WebSocket real-time por um período"""
    url = f"ws://localhost:8090/ws/realtime?api_key={API_KEY}&api_secret={API_SECRET}"

    print(f"\n📊 MONITORANDO WEBSOCKET POR {duration} SEGUNDOS:")
    print("=" * 50)

    try:
        async with websockets.connect(url) as ws:
            print(f"✅ Conectado! Aguardando mensagens...")
            print("-" * 50)

            start_time = time.time()
            message_count = 0

            while time.time() - start_time < duration:
                try:
                    msg = await asyncio.wait_for(ws.recv(), timeout=1)
                    data = json.loads(msg)
                    message_count += 1

                    timestamp = datetime.now().strftime("%H:%M:%S")
                    msg_type = data.get("type", "UNKNOWN")

                    if msg_type == "CONNECTION":
                        print(f"[{timestamp}] 🔗 {msg_type}: {data.get('message', '')}")
                    elif msg_type == "BALANCE_UPDATE":
                        print(f"[{timestamp}] 💰 {msg_type}: ${data.get('totalBalance', 0):,.2f} (source: {data.get('source', 'unknown')})")
                    elif msg_type == "PNL_UPDATE":
                        print(f"[{timestamp}] 💹 {msg_type}: P&L ${data.get('totalUnrealizedPnl', 0):,.2f}")
                    else:
                        print(f"[{timestamp}] 📡 {msg_type}")

                except asyncio.TimeoutError:
                    # Sem mensagens por 1 segundo - normal
                    pass
                except Exception as e:
                    print(f"[{datetime.now().strftime('%H:%M:%S')}] ❌ Erro: {e}")
                    break

            print("-" * 50)
            print(f"📊 Total de mensagens recebidas: {message_count}")

    except Exception as e:
        print(f"❌ Erro de conexão: {e}")

async def main():
    print("=" * 50)
    print("🔍 VERIFICADOR DE STATUS WEBSOCKET")
    print("=" * 50)

    # 1. Verifica serviços
    services = check_services()

    if not services.get("architect-bridge"):
        print("\n❌ architect-bridge não está rodando! Execute:")
        print("   docker-compose up -d architect-bridge")
        return

    # 2. Testa conexões WebSocket
    print("\n🧪 TESTANDO CONEXÕES WEBSOCKET:")
    print("=" * 50)

    print("\n1️⃣ WebSocket Balance (polling 5s):")
    balance_ok = await test_balance_websocket()

    print("\n2️⃣ WebSocket Real-time (streaming):")
    realtime_ok = await test_realtime_websocket()

    # 3. Mostra logs recentes
    check_recent_logs()

    # 4. Resumo
    print("\n📊 RESUMO DO STATUS:")
    print("=" * 50)

    if balance_ok and realtime_ok:
        print("✅ WEBSOCKETS FUNCIONANDO CORRETAMENTE!")
        print("\nDeseja monitorar mensagens em tempo real? (s/n): ", end="")

        import sys
        try:
            response = input().strip().lower()
            if response == 's':
                await monitor_realtime(30)
        except:
            pass

    elif realtime_ok:
        print("✅ WebSocket Real-time funcionando")
        print("⚠️ WebSocket Balance com problema")
    elif balance_ok:
        print("✅ WebSocket Balance funcionando")
        print("⚠️ WebSocket Real-time com problema")
    else:
        print("❌ NENHUM WEBSOCKET ESTÁ FUNCIONANDO!")
        print("\nPossíveis soluções:")
        print("1. Verifique se o architect-bridge está rodando:")
        print("   docker-compose up -d architect-bridge")
        print("2. Verifique as credenciais da API")
        print("3. Verifique os logs: docker logs architect-bridge")

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\n\n👋 Verificação interrompida")