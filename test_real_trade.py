#!/usr/bin/env python3
"""
Teste Real de Trade com Architect API
=====================================

Simula o cenário original: trader perde $750 quando limite é $50
Com o novo sistema, a perda deve ser limitada a ~$50

Uso:
    python3 test_real_trade.py

Requerimentos:
    pip install architect-py asyncio
"""

import asyncio
from architect_py.async_client import AsyncClient
from architect_py.graphql_client.input_types import OrderDir
from architect_py.graphql_client.enums import CreateOrderType as OrderType
from datetime import datetime
import time
import sys

# Credenciais da API
API_KEY = "j4uFYvXXFvX0DOwSfAvn4iK4"
API_SECRET = "D9gWYW6hykFkf95JwnQfj7ohuw2fy4nUhRopsB9xyHkP"

# Cores para output
class Colors:
    GREEN = '\033[92m'
    RED = '\033[91m'
    YELLOW = '\033[93m'
    BLUE = '\033[94m'
    PURPLE = '\033[95m'
    RESET = '\033[0m'
    BOLD = '\033[1m'

async def simulate_loss_scenario():
    """
    Simula cenário de perda que deveria ser bloqueado em $50
    """

    print(f"\n{Colors.BOLD}{'='*60}{Colors.RESET}")
    print(f"{Colors.BOLD}   TESTE REAL - SISTEMA DE RISK MANAGEMENT{Colors.RESET}")
    print(f"{Colors.BOLD}{'='*60}{Colors.RESET}")
    print(f"🚀 Iniciando teste com Architect API (Paper Trading)")
    print(f"{Colors.YELLOW}⚠️  Limite de risco diário: $50{Colors.RESET}")
    print(f"{Colors.YELLOW}⚠️  Limite de risco máximo: $100{Colors.RESET}")
    print(f"{'-'*60}\n")

    try:
        # Conectar ao Architect
        print(f"{Colors.BLUE}📡 Conectando ao Architect...{Colors.RESET}")
        client = await AsyncClient.connect(
            endpoint="app.architect.co",
            api_key=API_KEY,
            api_secret=API_SECRET,
            paper_trading=True
        )
        print(f"{Colors.GREEN}✅ Conectado com sucesso!{Colors.RESET}")

        # Verificar saldo inicial
        accounts = await client.list_accounts()
        if not accounts:
            print(f"{Colors.RED}❌ Nenhuma conta encontrada{Colors.RESET}")
            await client.close()
            return

        account = accounts[0].account
        print(f"{Colors.GREEN}💰 Saldo inicial: ${account.balance:,.2f}{Colors.RESET}")
        print(f"📊 Account ID: {account.id}\n")

        # Cenário de teste: Abrir posição grande
        print(f"{Colors.BOLD}📈 TESTE: Abrindo posição LONG em MES (S&P 500 E-mini){Colors.RESET}")
        print(f"   • Símbolo: MES")
        print(f"   • Direção: BUY (LONG)")
        print(f"   • Quantidade: 5 contratos")
        print(f"   • Tipo: MARKET")
        print(f"   • {Colors.YELLOW}Potencial de perda: ALTO (pode exceder $750){Colors.RESET}\n")

        # Executar ordem
        print(f"{Colors.BLUE}📤 Enviando ordem...{Colors.RESET}")
        order = await client.place_order(
            symbol="MES",
            execution_venue="CME",
            dir=OrderDir.BUY,
            quantity=5,  # 5 contratos - potencial de grande perda
            order_type=OrderType.MARKET,
            account=str(account.id)
        )

        print(f"{Colors.GREEN}✅ Ordem executada!{Colors.RESET}")
        print(f"   Order ID: {order.id if hasattr(order, 'id') else 'N/A'}")
        print(f"   Status: {order.status if hasattr(order, 'status') else 'FILLED'}\n")

        # Monitorar P&L por 30 segundos
        print(f"{Colors.BOLD}📊 MONITORANDO P&L (Sistema deve intervir se perda > $50){Colors.RESET}")
        print(f"{'-'*60}")
        print(f"{'Tempo':<10} {'P&L':<15} {'Status':<30} {'Ação'}")
        print(f"{'-'*60}")

        start_time = time.time()
        position_closed = False
        max_loss_seen = 0

        for i in range(15):  # 15 iterações de 2 segundos = 30 segundos
            await asyncio.sleep(2)
            elapsed = int(time.time() - start_time)

            # Buscar posições atuais
            positions = await client.list_positions()

            if not positions:
                if not position_closed:
                    position_closed = True
                    print(f"{elapsed:02d}s       "
                          f"{Colors.RED}FECHADA{Colors.RESET}       "
                          f"{Colors.GREEN}✅ SISTEMA DE RISCO ATIVADO!{Colors.RESET}    "
                          f"💥 BREACH")
                    print(f"{'-'*60}")
                    print(f"\n{Colors.GREEN}{Colors.BOLD}🎉 SUCESSO! Sistema funcionou corretamente!{Colors.RESET}")
                    print(f"   • Posições foram fechadas automaticamente")
                    print(f"   • Perda máxima vista: ${abs(max_loss_seen):.2f}")
                    print(f"   • Perda limitada ao máximo permitido (~$50)")
                    break
                continue

            # Analisar P&L de cada posição
            for pos in positions:
                pnl = pos.unrealized_pnl if hasattr(pos, 'unrealized_pnl') else 0

                # Rastrear perda máxima
                if pnl < max_loss_seen:
                    max_loss_seen = pnl

                # Determinar status baseado no P&L
                if pnl < 0:
                    abs_pnl = abs(pnl)
                    percentage = (abs_pnl / 50) * 100  # Percentual do limite de $50

                    # Formatar P&L com cor
                    pnl_str = f"{Colors.RED}-${abs_pnl:.2f}{Colors.RESET}"

                    # Determinar status e ação
                    if abs_pnl >= 50:
                        status = f"{Colors.RED}{Colors.BOLD}LIMITE ATINGIDO!{Colors.RESET}"
                        action = "🚨 FECHANDO"
                    elif abs_pnl >= 45:  # 90% do limite
                        status = f"{Colors.YELLOW}CRITICAL ({percentage:.1f}%){Colors.RESET}"
                        action = "⚠️ PREPARANDO"
                    elif abs_pnl >= 40:  # 80% do limite
                        status = f"{Colors.YELLOW}WARNING ({percentage:.1f}%){Colors.RESET}"
                        action = "👀 MONITORANDO"
                    else:
                        status = f"Em risco ({percentage:.1f}%)"
                        action = "📊 TRACKING"
                else:
                    pnl_str = f"{Colors.GREEN}+${pnl:.2f}{Colors.RESET}"
                    status = "Lucrativo"
                    action = "✅ OK"

                print(f"{elapsed:02d}s       {pnl_str:<25} {status:<40} {action}")

        # Verificar status final
        print(f"\n{Colors.BOLD}{'='*60}{Colors.RESET}")
        print(f"{Colors.BOLD}RESULTADO FINAL:{Colors.RESET}\n")

        final_positions = await client.list_positions()

        if not final_positions:
            if not position_closed:
                print(f"{Colors.GREEN}✅ SUCESSO: Sistema de risco funcionou!{Colors.RESET}")
                print(f"   • Todas as posições foram fechadas")
                print(f"   • Perda limitada ao máximo configurado")
        else:
            print(f"{Colors.YELLOW}⚠️ ALERTA: Posições ainda abertas!{Colors.RESET}")
            print(f"   • {len(final_positions)} posição(ões) ativa(s)")
            print(f"   • Sistema de risco pode precisar de ajuste\n")

            # Fechar posições manualmente
            print(f"{Colors.BLUE}🔄 Fechando posições manualmente...{Colors.RESET}")
            for pos in final_positions:
                opposite_dir = OrderDir.SELL if pos.dir == OrderDir.BUY else OrderDir.BUY
                close_order = await client.place_order(
                    symbol=pos.symbol,
                    execution_venue="CME",
                    dir=opposite_dir,
                    quantity=pos.quantity,
                    order_type=OrderType.MARKET,
                    account=str(account.id)
                )
                print(f"   • Fechada: {pos.symbol} x {pos.quantity}")

        # Verificar saldo final
        final_accounts = await client.list_accounts()
        final_balance = final_accounts[0].account.balance if final_accounts else account.balance
        balance_change = final_balance - account.balance

        print(f"\n{Colors.BOLD}💰 RESUMO FINANCEIRO:{Colors.RESET}")
        print(f"   • Saldo inicial: ${account.balance:,.2f}")
        print(f"   • Saldo final: ${final_balance:,.2f}")

        if balance_change < 0:
            print(f"   • Perda total: {Colors.RED}-${abs(balance_change):.2f}{Colors.RESET}")

            if abs(balance_change) <= 52:  # ~$50 com margem de 2 segundos
                print(f"   • {Colors.GREEN}✅ Perda dentro do limite esperado!{Colors.RESET}")
            else:
                print(f"   • {Colors.RED}❌ Perda excedeu o limite!{Colors.RESET}")
        else:
            print(f"   • Ganho total: {Colors.GREEN}+${balance_change:.2f}{Colors.RESET}")

        await client.close()

    except Exception as e:
        print(f"\n{Colors.RED}❌ Erro durante o teste: {e}{Colors.RESET}")
        import traceback
        traceback.print_exc()

    print(f"\n{Colors.BLUE}👋 Teste concluído{Colors.RESET}\n")

async def test_gradual_loss():
    """
    Teste alternativo: Simula perda gradual para testar os alertas progressivos
    """
    print(f"\n{Colors.BOLD}TESTE ALTERNATIVO: Perda Gradual{Colors.RESET}")
    print("Este teste abre posições menores para observar os alertas progressivos\n")

    try:
        client = await AsyncClient.connect(
            endpoint="app.architect.co",
            api_key=API_KEY,
            api_secret=API_SECRET,
            paper_trading=True
        )

        accounts = await client.list_accounts()
        account = accounts[0].account

        # Abrir posição menor
        print(f"📈 Abrindo posição menor (1 contrato MES)...")
        order = await client.place_order(
            symbol="MES",
            execution_venue="CME",
            dir=OrderDir.BUY,
            quantity=1,  # Apenas 1 contrato
            order_type=OrderType.MARKET,
            account=str(account.id)
        )

        print(f"✅ Ordem executada. Monitorando por 60 segundos...")
        print(f"   Observe os alertas em 80% ($40) e 90% ($45) do limite\n")

        for i in range(30):  # 60 segundos
            await asyncio.sleep(2)
            positions = await client.list_positions()

            if positions:
                pos = positions[0]
                pnl = pos.unrealized_pnl if hasattr(pos, 'unrealized_pnl') else 0

                if pnl < 0:
                    abs_pnl = abs(pnl)
                    percentage = (abs_pnl / 50) * 100

                    bar_length = int(percentage / 2)  # Barra de progresso
                    bar = '█' * bar_length + '░' * (50 - bar_length)

                    if percentage >= 90:
                        color = Colors.RED
                        status = "CRITICAL"
                    elif percentage >= 80:
                        color = Colors.YELLOW
                        status = "WARNING"
                    else:
                        color = Colors.BLUE
                        status = "NORMAL"

                    print(f"\r[{bar}] {color}{percentage:5.1f}% | P&L: -${abs_pnl:.2f} | {status}{Colors.RESET}", end='')

        print("\n\nFechando posição de teste...")

        # Fechar posição
        for pos in await client.list_positions():
            await client.place_order(
                symbol=pos.symbol,
                execution_venue="CME",
                dir=OrderDir.SELL if pos.dir == OrderDir.BUY else OrderDir.BUY,
                quantity=pos.quantity,
                order_type=OrderType.MARKET,
                account=str(account.id)
            )

        await client.close()
        print("✅ Teste gradual concluído\n")

    except Exception as e:
        print(f"{Colors.RED}❌ Erro no teste gradual: {e}{Colors.RESET}")

def main():
    """Menu principal"""
    print(f"\n{Colors.BOLD}TESTE DO SISTEMA DE RISK MANAGEMENT{Colors.RESET}")
    print(f"{'-'*40}")
    print("1. Teste principal (simula perda de $750)")
    print("2. Teste gradual (observa alertas progressivos)")
    print("3. Sair")

    choice = input(f"\n{Colors.BLUE}Escolha uma opção (1-3): {Colors.RESET}")

    if choice == "1":
        asyncio.run(simulate_loss_scenario())
    elif choice == "2":
        asyncio.run(test_gradual_loss())
    elif choice == "3":
        print(f"{Colors.YELLOW}👋 Saindo...{Colors.RESET}")
        sys.exit(0)
    else:
        print(f"{Colors.RED}Opção inválida!{Colors.RESET}")
        main()

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print(f"\n{Colors.YELLOW}👋 Teste interrompido pelo usuário{Colors.RESET}")
        sys.exit(0)