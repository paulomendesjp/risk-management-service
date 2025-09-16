#!/usr/bin/env python3
"""
🧪 SCRIPT DE TESTE PARA RISK MANAGEMENT
======================================
Simula cenários de perda para testar o sistema de risk management

Cenários de teste:
1. Abrir posições perdedoras para gerar loss
2. Testar daily risk violation (perda diária)
3. Testar max risk violation (perda máxima)
4. Verificar notificações e bloqueios
"""

import requests
import json
import time
from datetime import datetime

# Configuração dos serviços
PYTHON_BRIDGE_URL = "http://localhost:8090"
USER_SERVICE_URL = "http://localhost:8081"
RISK_SERVICE_URL = "http://localhost:8083"

# Cliente de teste
TEST_CLIENT_ID = "test_user"  # Use um cliente que já existe no sistema

def print_section(title):
    """Imprime seção formatada"""
    print(f"\n{'='*60}")
    print(f"📊 {title}")
    print(f"{'='*60}")

def get_client_info():
    """Busca informações do cliente de teste"""
    print_section("Informações do Cliente")

    try:
        # Buscar configuração do cliente
        response = requests.get(f"{USER_SERVICE_URL}/api/clients/{TEST_CLIENT_ID}")
        if response.status_code == 200:
            client = response.json()
            print(f"✅ Cliente: {client['name']}")
            print(f"   Initial Balance: ${client['initialBalance']}")
            print(f"   Daily Risk: ${client['dailyRisk']}")
            print(f"   Max Risk: ${client['maxRisk']}")
            return client
        else:
            print(f"❌ Cliente não encontrado: {TEST_CLIENT_ID}")
            return None
    except Exception as e:
        print(f"❌ Erro ao buscar cliente: {e}")
        return None

def start_monitoring(api_key, api_secret):
    """Inicia monitoramento do cliente"""
    print_section("Iniciando Monitoramento")

    try:
        response = requests.post(
            f"{PYTHON_BRIDGE_URL}/start-monitoring/{TEST_CLIENT_ID}",
            headers={
                "api-key": api_key,
                "api-secret": api_secret
            }
        )
        if response.status_code == 200:
            print(f"✅ Monitoramento iniciado para {TEST_CLIENT_ID}")
            return True
        else:
            print(f"❌ Erro ao iniciar monitoramento: {response.text}")
            return False
    except Exception as e:
        print(f"❌ Erro ao iniciar monitoramento: {e}")
        return False

def get_current_balance(api_key, api_secret):
    """Obtém saldo atual da conta"""
    try:
        response = requests.get(
            f"{PYTHON_BRIDGE_URL}/account-balance/{TEST_CLIENT_ID}",
            headers={
                "api-key": api_key,
                "api-secret": api_secret
            }
        )
        if response.status_code == 200:
            data = response.json()
            balance = data.get('balance', 0)
            print(f"💰 Saldo atual: ${balance}")
            return balance
        else:
            print(f"❌ Erro ao obter saldo: {response.text}")
            return None
    except Exception as e:
        print(f"❌ Erro ao obter saldo: {e}")
        return None

def place_losing_order(api_key, api_secret, symbol="BTC-USD", qty=0.001):
    """
    Coloca uma ordem que resultará em perda
    Estratégia: Comprar alto e vender baixo (simulando perda)
    """
    print(f"\n📉 Colocando ordem perdedora: {symbol} qty={qty}")

    try:
        # Primeiro, comprar (abrir posição)
        buy_order = {
            "clientId": TEST_CLIENT_ID,
            "symbol": symbol,
            "action": "buy",
            "orderQty": qty,
            "orderType": "MARKET"
        }

        response = requests.post(
            f"{PYTHON_BRIDGE_URL}/place-order",
            headers={
                "api-key": api_key,
                "api-secret": api_secret,
                "Content-Type": "application/json"
            },
            json=buy_order
        )

        if response.status_code == 200:
            print(f"   ✅ Ordem de compra executada")

            # Aguardar um pouco
            time.sleep(2)

            # Depois, vender (fechar posição com perda)
            sell_order = {
                "clientId": TEST_CLIENT_ID,
                "symbol": symbol,
                "action": "sell",
                "orderQty": qty,
                "orderType": "MARKET"
            }

            response = requests.post(
                f"{PYTHON_BRIDGE_URL}/place-order",
                headers={
                    "api-key": api_key,
                    "api-secret": api_secret,
                    "Content-Type": "application/json"
                },
                json=sell_order
            )

            if response.status_code == 200:
                print(f"   ✅ Ordem de venda executada (posição fechada)")
                return True
            else:
                print(f"   ❌ Erro na ordem de venda: {response.text}")
                return False
        else:
            print(f"   ❌ Erro na ordem de compra: {response.text}")
            return False

    except Exception as e:
        print(f"   ❌ Erro ao colocar ordem: {e}")
        return False

def simulate_balance_drop(new_balance):
    """
    Simula uma queda no saldo enviando update direto para o Risk Service
    ATENÇÃO: Use apenas para testes!
    """
    print_section(f"Simulando Queda de Saldo para ${new_balance}")

    try:
        # Enviar atualização de saldo diretamente via WebSocket ou API
        balance_update = {
            "type": "BALANCE_UPDATE",
            "clientId": TEST_CLIENT_ID,
            "balance": new_balance,
            "timestamp": datetime.now().isoformat()
        }

        # Tentar enviar via API do Risk Service (se houver endpoint de teste)
        response = requests.post(
            f"{RISK_SERVICE_URL}/api/risk/simulate-balance",
            json=balance_update
        )

        if response.status_code == 200:
            print(f"✅ Saldo simulado enviado: ${new_balance}")
            return True
        else:
            print(f"❌ Erro ao simular saldo: {response.text}")
            return False

    except Exception as e:
        print(f"❌ Erro ao simular saldo: {e}")
        # Se não houver endpoint de simulação, precisamos fazer trades reais
        print("ℹ️  Não há endpoint de simulação. Use trades reais para gerar perdas.")
        return False

def check_risk_status():
    """Verifica status de risco do cliente"""
    print_section("Status de Risco")

    try:
        response = requests.get(f"{RISK_SERVICE_URL}/api/risk/status/{TEST_CLIENT_ID}")
        if response.status_code == 200:
            status = response.json()
            print(f"📊 Status de Risco:")
            print(f"   Daily PnL: ${status.get('dailyPnl', 0)}")
            print(f"   Total PnL: ${status.get('totalPnl', 0)}")
            print(f"   Daily Blocked: {status.get('dailyBlocked', False)}")
            print(f"   Permanently Blocked: {status.get('permanentlyBlocked', False)}")
            return status
        else:
            print(f"❌ Erro ao verificar status: {response.text}")
            return None
    except Exception as e:
        print(f"❌ Erro ao verificar status: {e}")
        return None

def main():
    """Executa cenários de teste"""
    print("\n" + "="*60)
    print("🧪 TESTE DO SISTEMA DE RISK MANAGEMENT")
    print("="*60)

    # 1. Obter informações do cliente
    client = get_client_info()
    if not client:
        print("\n❌ Configure primeiro um cliente de teste no sistema!")
        print("   Use o endpoint POST /api/clients para criar um cliente")
        return

    # Extrair credenciais
    api_key = client.get('apiKey', '')
    api_secret = client.get('apiSecret', '')

    if not api_key or not api_secret:
        print("\n❌ Cliente não tem credenciais API configuradas!")
        return

    # 2. Iniciar monitoramento
    if not start_monitoring(api_key, api_secret):
        print("\n❌ Não foi possível iniciar o monitoramento")
        return

    time.sleep(3)  # Aguardar conexão estabelecer

    # 3. Verificar saldo inicial
    initial_balance = get_current_balance(api_key, api_secret)
    if initial_balance is None:
        print("\n❌ Não foi possível obter saldo inicial")
        return

    # 4. Cenário 1: Testar Daily Risk (perda diária)
    print_section("CENÁRIO 1: Teste de Daily Risk")
    print(f"Objetivo: Gerar perda > ${client['dailyRisk']}")

    daily_risk_limit = float(client['dailyRisk'])
    target_loss = daily_risk_limit * 1.1  # 10% acima do limite

    print(f"Meta de perda: ${target_loss:.2f}")

    # Opção 1: Tentar simular (mais rápido para testes)
    simulated_balance = initial_balance - target_loss
    if simulate_balance_drop(simulated_balance):
        print("✅ Saldo simulado com sucesso")
    else:
        # Opção 2: Fazer trades reais (mais lento mas real)
        print("\n📈 Executando trades para gerar perda real...")

        # Execute várias ordens pequenas
        for i in range(3):
            place_losing_order(api_key, api_secret, qty=0.0001)
            time.sleep(2)

    # 5. Aguardar processamento
    print("\n⏳ Aguardando processamento do risco (5 segundos)...")
    time.sleep(5)

    # 6. Verificar se daily risk foi acionado
    status = check_risk_status()
    if status and status.get('dailyBlocked'):
        print("\n✅ DAILY RISK ACIONADO COM SUCESSO!")
        print("   - Cliente bloqueado para o dia")
        print("   - Notificações devem ter sido enviadas")

    # 7. Cenário 2: Testar Max Risk (perda máxima)
    print_section("CENÁRIO 2: Teste de Max Risk")
    print(f"Objetivo: Gerar perda > ${client['maxRisk']}")

    max_risk_limit = float(client['maxRisk'])
    target_max_loss = max_risk_limit * 1.1  # 10% acima do limite máximo

    print(f"Meta de perda total: ${target_max_loss:.2f}")

    # Simular perda máxima
    simulated_balance = float(client['initialBalance']) - target_max_loss
    if simulate_balance_drop(simulated_balance):
        print("✅ Saldo simulado com sucesso")

    # 8. Aguardar processamento
    print("\n⏳ Aguardando processamento do risco (5 segundos)...")
    time.sleep(5)

    # 9. Verificar se max risk foi acionado
    status = check_risk_status()
    if status and status.get('permanentlyBlocked'):
        print("\n✅ MAX RISK ACIONADO COM SUCESSO!")
        print("   - Cliente bloqueado permanentemente")
        print("   - Todas as posições devem ter sido fechadas")
        print("   - Notificações críticas enviadas")

    # 10. Resumo final
    print_section("RESUMO DO TESTE")
    print("✅ Teste concluído!")
    print("\nVerifique:")
    print("1. Logs do notification-service para ver as notificações")
    print("2. MongoDB para ver o histórico de eventos")
    print("3. RabbitMQ Management para ver as mensagens")
    print("4. Logs do risk-monitoring-service para ver o processamento")

if __name__ == "__main__":
    main()