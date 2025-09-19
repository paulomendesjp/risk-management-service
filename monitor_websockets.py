#!/usr/bin/env python3
"""
Monitor WebSockets em Tempo Real para Risk Management System
============================================================

Este script monitora os WebSockets de OrderFlow e Position para visualizar
em tempo real as atualizações de trades e P&L.

Uso:
    python3 monitor_websockets.py

Requerimentos:
    pip install websockets asyncio
"""

import asyncio
import websockets
import json
from datetime import datetime
import sys

# Credenciais da API
API_KEY = "j4uFYvXXFvX0DOwSfAvn4iK4"
API_SECRET = "D9gWYW6hykFkf95JwnQfj7ohuw2fy4nUhRopsB9xyHkP"

# Cores para output no terminal
class Colors:
    GREEN = '\033[92m'
    RED = '\033[91m'
    YELLOW = '\033[93m'
    BLUE = '\033[94m'
    PURPLE = '\033[95m'
    RESET = '\033[0m'
    BOLD = '\033[1m'

def print_header():
    """Imprime o cabeçalho do monitor"""
    print(f"\n{Colors.BOLD}{'='*60}{Colors.RESET}")
    print(f"{Colors.BOLD}   WEBSOCKET MONITOR - REAL-TIME RISK TRACKING{Colors.RESET}")
    print(f"{Colors.BOLD}{'='*60}{Colors.RESET}")
    print(f"{Colors.BLUE}API Key: {API_KEY[:10]}...{Colors.RESET}")
    print(f"Monitoring OrderFlow (fills) and Positions (P&L)")
    print(f"{Colors.BOLD}{'-'*60}{Colors.RESET}\n")

async def monitor_orderflow():
    """Monitor OrderFlow WebSocket para fills em tempo real"""
    uri = f"ws://localhost:8090/ws/orderflow?api_key={API_KEY}&api_secret={API_SECRET}"
    print(f"{Colors.BLUE}📡 Connecting to OrderFlow WebSocket...{Colors.RESET}")

    try:
        async with websockets.connect(uri) as websocket:
            print(f"{Colors.GREEN}✅ OrderFlow WebSocket connected{Colors.RESET}")

            while True:
                try:
                    message = await websocket.recv()
                    data = json.loads(message)
                    timestamp = datetime.now().strftime("%H:%M:%S.%f")[:-3]

                    # Processar diferentes tipos de mensagens
                    if data.get("type") == "CONNECTION":
                        print(f"[{timestamp}] {Colors.GREEN}📡 OrderFlow connection established{Colors.RESET}")

                    elif data.get("event") == "ORDER_FILL":
                        symbol = data.get('symbol', 'N/A')
                        quantity = data.get('quantity', 0)
                        price = data.get('price', 0)
                        side = data.get('side', 'N/A')

                        # Colorir baseado no side
                        side_color = Colors.GREEN if side in ['BUY', 'LONG'] else Colors.RED

                        print(f"[{timestamp}] {Colors.BOLD}🎯 ORDER FILL:{Colors.RESET}")
                        print(f"              Symbol: {symbol}")
                        print(f"              Side: {side_color}{side}{Colors.RESET}")
                        print(f"              Qty: {quantity} @ ${price:,.2f}")
                        print(f"              Value: ${quantity * price:,.2f}")

                    elif data.get("type") == "ERROR":
                        error_msg = data.get('message', 'Unknown error')
                        print(f"[{timestamp}] {Colors.RED}❌ OrderFlow Error: {error_msg}{Colors.RESET}")

                    else:
                        # Outras mensagens
                        if data.get("type") != "HEARTBEAT":
                            print(f"[{timestamp}] OrderFlow: {json.dumps(data, indent=2)}")

                except websockets.exceptions.ConnectionClosed:
                    print(f"{Colors.YELLOW}⚠️ OrderFlow connection closed. Reconnecting...{Colors.RESET}")
                    await asyncio.sleep(5)
                    break
                except Exception as e:
                    print(f"{Colors.RED}❌ OrderFlow error: {e}{Colors.RESET}")

    except Exception as e:
        print(f"{Colors.RED}❌ Failed to connect OrderFlow: {e}{Colors.RESET}")
        await asyncio.sleep(5)

async def monitor_positions():
    """Monitor Position WebSocket para P&L a cada 2 segundos"""
    uri = f"ws://localhost:8090/ws/positions?api_key={API_KEY}&api_secret={API_SECRET}"
    print(f"{Colors.BLUE}📊 Connecting to Position WebSocket...{Colors.RESET}")

    try:
        async with websockets.connect(uri) as websocket:
            print(f"{Colors.GREEN}✅ Position WebSocket connected{Colors.RESET}")
            print(f"{Colors.PURPLE}📊 P&L updates every 2 seconds{Colors.RESET}\n")

            while True:
                try:
                    message = await websocket.recv()
                    data = json.loads(message)
                    timestamp = datetime.now().strftime("%H:%M:%S.%f")[:-3]

                    if data.get("type") == "CONNECTION":
                        print(f"[{timestamp}] {Colors.GREEN}📊 Position monitoring started{Colors.RESET}")

                    elif data.get("type") == "POSITION_UPDATE":
                        pnl = data.get("totalUnrealizedPnl", 0)
                        balance = data.get("accountBalance", 0)
                        positions = data.get("positions", [])

                        # Colorir P&L baseado em profit/loss
                        if pnl < 0:
                            pnl_color = Colors.RED
                            pnl_emoji = "🔴"

                            # Calcular percentual de risco
                            daily_limit = 50  # Limite diário configurado
                            risk_percent = abs(pnl) / daily_limit * 100

                            if risk_percent >= 100:
                                alert = f" {Colors.BOLD}💥 LIMIT BREACHED!{Colors.RESET}"
                            elif risk_percent >= 90:
                                alert = f" {Colors.BOLD}🚨 CRITICAL ({risk_percent:.1f}%){Colors.RESET}"
                            elif risk_percent >= 80:
                                alert = f" {Colors.YELLOW}⚠️ WARNING ({risk_percent:.1f}%){Colors.RESET}"
                            else:
                                alert = f" ({risk_percent:.1f}% of limit)"
                        else:
                            pnl_color = Colors.GREEN
                            pnl_emoji = "🟢"
                            alert = ""

                        print(f"[{timestamp}] {Colors.BOLD}P&L Update:{Colors.RESET}")
                        print(f"              {pnl_emoji} Unrealized P&L: {pnl_color}${pnl:,.2f}{Colors.RESET}{alert}")
                        print(f"              💰 Balance: ${balance:,.2f}")

                        # Mostrar posições abertas
                        if positions:
                            print(f"              📈 Open Positions:")
                            for pos in positions:
                                symbol = pos.get('symbol', 'N/A')
                                qty = pos.get('quantity', 0)
                                pos_pnl = pos.get('unrealizedPnl', 0)
                                pos_color = Colors.GREEN if pos_pnl >= 0 else Colors.RED
                                print(f"                 • {symbol}: Qty {qty} | P&L: {pos_color}${pos_pnl:,.2f}{Colors.RESET}")

                    elif data.get("type") == "RISK_ALERT":
                        alert_type = data.get("alertType", "UNKNOWN")
                        message = data.get("message", "Risk alert triggered")

                        if alert_type == "CRITICAL":
                            print(f"[{timestamp}] {Colors.RED}{Colors.BOLD}🚨🚨🚨 CRITICAL RISK ALERT: {message}{Colors.RESET}")
                        elif alert_type == "WARNING":
                            print(f"[{timestamp}] {Colors.YELLOW}{Colors.BOLD}⚠️ WARNING: {message}{Colors.RESET}")

                    elif data.get("type") == "ERROR":
                        error_msg = data.get('message', 'Unknown error')
                        print(f"[{timestamp}] {Colors.RED}❌ Position Error: {error_msg}{Colors.RESET}")

                except websockets.exceptions.ConnectionClosed:
                    print(f"{Colors.YELLOW}⚠️ Position connection closed. Reconnecting...{Colors.RESET}")
                    await asyncio.sleep(5)
                    break
                except Exception as e:
                    print(f"{Colors.RED}❌ Position error: {e}{Colors.RESET}")

    except Exception as e:
        print(f"{Colors.RED}❌ Failed to connect Position: {e}{Colors.RESET}")
        await asyncio.sleep(5)

async def main():
    """Executa ambos os monitores em paralelo"""
    print_header()

    # Executar monitores com reconexão automática
    while True:
        try:
            # Executar ambos concorrentemente
            await asyncio.gather(
                monitor_orderflow(),
                monitor_positions(),
                return_exceptions=True
            )
        except KeyboardInterrupt:
            print(f"\n{Colors.YELLOW}👋 Monitor stopped by user{Colors.RESET}")
            break
        except Exception as e:
            print(f"{Colors.RED}❌ Monitor error: {e}{Colors.RESET}")
            print(f"{Colors.YELLOW}🔄 Restarting in 5 seconds...{Colors.RESET}")
            await asyncio.sleep(5)

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print(f"\n{Colors.YELLOW}👋 Monitor stopped{Colors.RESET}")
        sys.exit(0)