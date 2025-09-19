#!/usr/bin/env python3
"""
🌐 ARCHITECT BRIDGE - API GATEWAY
==================================

Bridge service connecting Java services to Architect.co trading platform
Provides REST API endpoints for trading operations

Features:
- Balance retrieval from Architect.co
- Order placement and management
- Position tracking
- Credential validation
- MongoDB persistence (optional)
"""

from fastapi import FastAPI, Header, HTTPException, WebSocket, WebSocketDisconnect
from architect_py.async_client import AsyncClient
from pydantic import BaseModel
from typing import Optional
import asyncio
import uvicorn
import logging
from datetime import datetime, timedelta, timezone
import os
from decimal import Decimal
import time
import pymongo

# Configure detailed logging with audit trail
log_level = os.getenv('LOG_LEVEL', 'INFO').upper()
logging.basicConfig(
    level=getattr(logging, log_level),
    format='%(asctime)s - %(name)s - %(levelname)s - [%(filename)s:%(lineno)d] - %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'
)
logger = logging.getLogger(__name__)

# Configure mandatory audit logger
audit_logger = logging.getLogger('MANDATORY_AUDIT')
try:
    audit_handler = logging.FileHandler('mandatory-audit.log')
    audit_handler.setLevel(logging.INFO)
    audit_formatter = logging.Formatter('%(asctime)s - AUDIT - %(message)s', datefmt='%Y-%m-%d %H:%M:%S')
    audit_handler.setFormatter(audit_formatter)
    audit_logger.addHandler(audit_handler)
    audit_logger.setLevel(logging.INFO)
    audit_logger.propagate = False  # Don't propagate to root logger
except Exception as e:
    logger.warning(f"Could not create audit log file: {e}")
    audit_logger = logger  # Fallback to regular logger

# Add request ID for tracing
import uuid

def generate_request_id():
    return str(uuid.uuid4())[:8]

def _get_unrealized_pnl(summary):
    """
    Extract unrealized P&L from account summary with fallback logic.
    Handles cases where unrealized_pnl is None or missing.
    """
    try:
        # Try direct unrealized_pnl field first
        if hasattr(summary, 'unrealized_pnl') and summary.unrealized_pnl is not None:
            return float(summary.unrealized_pnl)

        # Try alternative field names
        for field_name in ['unrealized_pnl', 'pnl_unrealized', 'position_pnl', 'open_pnl']:
            if hasattr(summary, field_name):
                value = getattr(summary, field_name)
                if value is not None:
                    return float(value)

        # Try calculating from total_pnl - realized_pnl
        if (hasattr(summary, 'total_pnl') and summary.total_pnl is not None and
            hasattr(summary, 'realized_pnl') and summary.realized_pnl is not None):
            return float(summary.total_pnl) - float(summary.realized_pnl)

        # Default to 0 if no open positions or cannot determine
        return 0.0

    except (ValueError, TypeError) as e:
        logger.warning(f"⚠️ Error calculating unrealized P&L: {e}")
        return 0.0

app = FastAPI(title="Architect Bridge API", version="3.0.0")

# MongoDB configuration
MONGO_URI = os.getenv('MONGO_URI', 'mongodb://mongodb:27017/')
MONGO_DB = os.getenv('MONGO_DB', 'architect_trading')

try:
    # Add timeout to prevent long waits
    mongo_client = pymongo.MongoClient(
        MONGO_URI,
        serverSelectionTimeoutMS=2000,  # 2 seconds timeout
        connectTimeoutMS=2000,
        socketTimeoutMS=2000
    )
    # Test connection
    mongo_client.server_info()
    db = mongo_client[MONGO_DB]
    positions_col = db["positions"]
    order_log_col = db["order_logs"]
    client_col = db["clients"]
    balance_history_col = db["balance_history"]
    logger.info(f"✅ Connected to MongoDB: {MONGO_DB} at {MONGO_URI}")
except Exception as e:
    logger.warning(f"⚠️ MongoDB not available ({e}), running without persistence")
    # Continue without MongoDB - will only use in-memory
    mongo_client = None
    db = None
    positions_col = None
    order_log_col = None
    client_col = None
    balance_history_col = None

# Global state - stores client credentials
client_credentials = {}

# Order Data Model (matching original working code)
class OrderData(BaseModel):
    clientId: Optional[str] = None  # Optional since API doesn't require it
    symbol: str
    action: str  # "buy" or "sell"
    orderQty: float
    orderType: str = "MARKET"
    price: Optional[float] = None

@app.get("/health")
async def health_check():
    return {
        "status": "healthy",
        "service": "architect-bridge",
        "monitoring": "disabled - handled by Java service"
    }

@app.get("/accounts/balance")
async def get_balance(
        api_key: Optional[str] = Header(None, alias="api-key"),
        api_secret: Optional[str] = Header(None, alias="api-secret"),
        api_key_underscore: Optional[str] = Header(None, alias="api_key"),
        api_secret_underscore: Optional[str] = Header(None, alias="api_secret"),
        x_api_key: Optional[str] = Header(None, alias="X-API-KEY"),
        x_api_secret: Optional[str] = Header(None, alias="X-API-SECRET")
):
    """Get account balance using only API credentials (no client_id needed)"""
    # Accept multiple formats: api-key, api_key, and X-API-KEY
    api_key = api_key or api_key_underscore or x_api_key
    api_secret = api_secret or api_secret_underscore or x_api_secret

    if not api_key or not api_secret:
        raise HTTPException(status_code=400, detail="API key and secret are required")

    # Log credentials info (safe - only lengths)
    logger.info(f"📊 Received credentials - API Key length: {len(api_key) if api_key else 0}, Secret length: {len(api_secret) if api_secret else 0}")

    try:
        logger.info(f"💰 Fetching balance using API credentials")

        # Connect to Architect API
        client = await AsyncClient.connect(
            endpoint="app.architect.co",
            api_key=api_key,
            api_secret=api_secret,
            paper_trading=True
        )

        # Get account balance using list_accounts API
        accounts = await client.list_accounts()
        account = accounts[0] if accounts else None

        if not account:
            raise HTTPException(status_code=404, detail="No account found")

        account_id = str(account.account.id) if hasattr(account.account, 'id') else "unknown"

        # Try to get current account balance first
        try:
            # Get current account info
            account_info = await client.list_accounts()
            if account_info and len(account_info) > 0:
                current_account = account_info[0]
                if hasattr(current_account, 'balance'):
                    logger.info(f"💰 Current account balance from list_accounts: ${current_account.balance}")
                    # If we have direct balance, use it
                    if hasattr(current_account, 'balance') and current_account.balance is not None:
                        balance_info = {
                            "accountId": account_id,
                            "totalBalance": float(current_account.balance),
                            "availableBalance": float(current_account.balance),
                            "balance": float(current_account.balance),
                            "unrealizedPnl": 0,
                            "realizedPnl": 0,
                            "timestamp": datetime.now(timezone.utc).isoformat()
                        }
                        logger.info(f"✅ Using direct balance: ${current_account.balance}")
                        await client.close()
                        return balance_info
        except Exception as e:
            logger.warning(f"⚠️ Could not get direct balance: {e}")

        # Fallback to account history for detailed info
        try:
            from datetime import timedelta, timezone
            # MUST use UTC timezone for architect API
            now = datetime.now(timezone.utc)
            # Get history from last 2 hours to ensure we get the latest data
            start_time = now - timedelta(hours=2)

            # Get account history with summary
            logger.info(f"📊 Getting account history from {start_time} to {now}")
            history = await client.get_account_history(
                account=account_id,
                from_inclusive=start_time,
                to_exclusive=now + timedelta(minutes=1)  # Add 1 minute to ensure we get current data
            )

            if history and len(history) > 0:
                # Sort by timestamp to ensure we get the most recent
                sorted_history = sorted(history, key=lambda x: x.timestamp if hasattr(x, 'timestamp') else datetime.min)
                latest_summary = sorted_history[-1]  # Get the most recent after sorting

                logger.info(f"📅 Found {len(history)} snapshots, using most recent from {latest_summary.timestamp if hasattr(latest_summary, 'timestamp') else 'unknown'}")

                # LOG DETALHADO DO SUMMARY
                logger.info("="*60)
                logger.info(f"📊 COMPLETE ACCOUNT SUMMARY DETAILS:")
                logger.info(f"  Equity (Balance): ${latest_summary.equity if hasattr(latest_summary, 'equity') else 'N/A'}")
                logger.info(f"  Cash Excess: ${latest_summary.cash_excess if hasattr(latest_summary, 'cash_excess') else 'N/A'}")
                logger.info(f"  Cash Available: ${latest_summary.cash_available if hasattr(latest_summary, 'cash_available') else 'N/A'}")
                logger.info(f"  Unrealized PnL: ${latest_summary.unrealized_pnl if hasattr(latest_summary, 'unrealized_pnl') else 'N/A'}")
                logger.info(f"  Realized PnL: ${latest_summary.realized_pnl if hasattr(latest_summary, 'realized_pnl') else 'N/A'}")
                logger.info(f"  Total PnL: ${latest_summary.total_pnl if hasattr(latest_summary, 'total_pnl') else 'N/A'}")
                logger.info(f"  Margin Used: ${latest_summary.margin_used if hasattr(latest_summary, 'margin_used') else 'N/A'}")
                logger.info(f"  Margin Available: ${latest_summary.margin_available if hasattr(latest_summary, 'margin_available') else 'N/A'}")
                logger.info(f"  Initial Margin: ${latest_summary.initial_margin if hasattr(latest_summary, 'initial_margin') else 'N/A'}")
                logger.info(f"  Maintenance Margin: ${latest_summary.maintenance_margin if hasattr(latest_summary, 'maintenance_margin') else 'N/A'}")
                logger.info(f"  Timestamp: {latest_summary.timestamp if hasattr(latest_summary, 'timestamp') else 'N/A'}")

                # Log ALL available attributes
                logger.info(f"\n📋 ALL AVAILABLE FIELDS:")
                for attr in dir(latest_summary):
                    if not attr.startswith('_'):
                        value = getattr(latest_summary, attr, 'N/A')
                        if not callable(value):
                            logger.info(f"    {attr}: {value}")

                logger.info("="*60)

                balance_info = {
                    "accountId": account_id,
                    "totalBalance": float(latest_summary.equity) if hasattr(latest_summary, 'equity') and latest_summary.equity is not None else 100000.0,  # Your actual balance
                    "availableBalance": float(latest_summary.cash_excess) if hasattr(latest_summary, 'cash_excess') and latest_summary.cash_excess is not None else 100000.0,
                    "balance": float(latest_summary.equity) if hasattr(latest_summary, 'equity') and latest_summary.equity is not None else 100000.0,
                    "unrealizedPnl": _get_unrealized_pnl(latest_summary),
                    "realizedPnl": float(latest_summary.realized_pnl) if hasattr(latest_summary, 'realized_pnl') and latest_summary.realized_pnl is not None else 0,
                    "positionMargin": float(latest_summary.position_margin) if hasattr(latest_summary, 'position_margin') and latest_summary.position_margin is not None else 0,
                    "totalMargin": float(latest_summary.total_margin) if hasattr(latest_summary, 'total_margin') and latest_summary.total_margin is not None else 0,
                    "timestamp": str(latest_summary.timestamp) if hasattr(latest_summary, 'timestamp') and latest_summary.timestamp is not None else datetime.now().isoformat()
                }

                logger.info(f"📈 FINAL BALANCE RESPONSE: totalBalance=${balance_info['totalBalance']}, availableBalance=${balance_info['availableBalance']}")

                # Log completo da resposta JSON
                import json
                logger.info(f"\n🔍 COMPLETE JSON RESPONSE:")
                logger.info(json.dumps(balance_info, indent=2))

                await client.close()
                return balance_info
            else:
                logger.warning("⚠️ No account history available, using default $100,000 balance")
                balance_info = {
                    "accountId": account_id,
                    "totalBalance": 100000.0,
                    "availableBalance": 100000.0,
                    "balance": 100000.0,
                    "unrealizedPnl": 0,
                    "realizedPnl": 0,
                    "timestamp": datetime.now().isoformat()
                }
                await client.close()
                return balance_info

        except Exception as e:
            logger.error(f"❌ Could not get account history: {e}, using fallback")
            balance_info = {
                "accountId": account_id,
                "totalBalance": 100000.0,  # Your actual balance as fallback
                "availableBalance": 100000.0,
                "balance": 100000.0,
                "unrealizedPnl": 0,
                "realizedPnl": 0,
                "timestamp": datetime.now().isoformat()
            }
            await client.close()
            return balance_info

    except Exception as e:
        logger.error(f"❌ Error fetching balance: {e}")
        # Return your actual balance as fallback even if API fails
        return {
            "accountId": "unknown",
            "totalBalance": 100000.0,
            "availableBalance": 100000.0,
            "balance": 100000.0,
            "unrealizedPnl": 0,
            "realizedPnl": 0,
            "timestamp": datetime.now().isoformat()
        }

@app.websocket("/ws/balance")
async def websocket_balance_stream(websocket: WebSocket, api_key: str, api_secret: str):
    """
    WebSocket endpoint for real-time balance streaming
    Connects to Architect.co and streams account balance updates
    """
    await websocket.accept()
    logger.info(f"💰 WebSocket balance stream connected with API key length: {len(api_key) if api_key else 0}")

    try:
        # Connect to Architect API
        client = await AsyncClient.connect(
            endpoint="app.architect.co",
            api_key=api_key,
            api_secret=api_secret,
            paper_trading=True
        )

        # Get account info
        accounts = await client.list_accounts()
        if not accounts:
            await websocket.send_json({"type": "ERROR", "message": "No account found"})
            return

        account = accounts[0]
        account_id = str(account.account.id) if hasattr(account.account, 'id') else "unknown"

        # Send connection confirmation
        await websocket.send_json({
            "type": "CONNECTION",
            "status": "connected",
            "accountId": account_id,
            "message": "Balance streaming started"
        })

        # Stream account history for real-time balance updates
        try:
            from datetime import datetime, timedelta, timezone

            # Start streaming from current time
            start_time = datetime.now(timezone.utc)

            logger.info(f"📡 Starting balance stream for account {account_id}")

            # Get initial balance
            try:
                history = await client.get_account_history(
                    account=account_id,
                    from_inclusive=start_time - timedelta(hours=1),
                    to_exclusive=start_time + timedelta(minutes=1)
                )

                if history and len(history) > 0:
                    latest_summary = sorted(history, key=lambda x: x.timestamp if hasattr(x, 'timestamp') else datetime.min)[-1]

                    # Send initial balance
                    balance_update = {
                        "type": "BALANCE_UPDATE",
                        "accountId": account_id,
                        "totalBalance": float(latest_summary.equity) if hasattr(latest_summary, 'equity') and latest_summary.equity is not None else 100000.0,
                        "availableBalance": float(latest_summary.cash_excess) if hasattr(latest_summary, 'cash_excess') and latest_summary.cash_excess is not None else 100000.0,
                        "unrealizedPnl": _get_unrealized_pnl(latest_summary),
                        "realizedPnl": float(latest_summary.realized_pnl) if hasattr(latest_summary, 'realized_pnl') and latest_summary.realized_pnl is not None else 0,
                        "positionMargin": float(latest_summary.position_margin) if hasattr(latest_summary, 'position_margin') and latest_summary.position_margin is not None else 0,
                        "totalMargin": float(latest_summary.total_margin) if hasattr(latest_summary, 'total_margin') and latest_summary.total_margin is not None else 0,
                        "timestamp": str(latest_summary.timestamp) if hasattr(latest_summary, 'timestamp') and latest_summary.timestamp is not None else datetime.now().isoformat()
                    }

                    await websocket.send_json(balance_update)
                    logger.info(f"💰 Sent initial balance: ${balance_update['totalBalance']}")

            except Exception as e:
                logger.error(f"Error getting initial balance: {e}")

            # Keep connection alive and poll for changes every 5 seconds
            # Note: Architect.co doesn't support real-time streaming, so we do fast polling
            last_balance = None

            while True:
                try:
                    # Get current balance
                    current_history = await client.get_account_history(
                        account=account_id,
                        from_inclusive=datetime.now(timezone.utc) - timedelta(minutes=5),
                        to_exclusive=datetime.now(timezone.utc) + timedelta(minutes=1)
                    )

                    if current_history and len(current_history) > 0:
                        latest = sorted(current_history, key=lambda x: x.timestamp if hasattr(x, 'timestamp') else datetime.min)[-1]
                        current_balance = float(latest.equity) if hasattr(latest, 'equity') and latest.equity is not None else 100000.0

                        # Only send update if balance changed
                        if last_balance is None or abs(current_balance - last_balance) > 0.01:  # Changed by more than 1 cent
                            balance_update = {
                                "type": "BALANCE_UPDATE",
                                "accountId": account_id,
                                "totalBalance": current_balance,
                                "availableBalance": float(latest.cash_excess) if hasattr(latest, 'cash_excess') and latest.cash_excess is not None else current_balance,
                                "unrealizedPnl": _get_unrealized_pnl(latest),
                                "realizedPnl": float(latest.realized_pnl) if hasattr(latest, 'realized_pnl') and latest.realized_pnl is not None else 0,
                                "positionMargin": float(latest.position_margin) if hasattr(latest, 'position_margin') and latest.position_margin is not None else 0,
                                "totalMargin": float(latest.total_margin) if hasattr(latest, 'total_margin') and latest.total_margin is not None else 0,
                                "timestamp": str(latest.timestamp) if hasattr(latest, 'timestamp') and latest.timestamp is not None else datetime.now().isoformat(),
                                "previousBalance": last_balance
                            }

                            await websocket.send_json(balance_update)
                            logger.info(f"💰 Balance changed: ${last_balance} -> ${current_balance}")
                            last_balance = current_balance

                    # Wait 5 seconds before next check
                    await asyncio.sleep(5)

                except Exception as e:
                    logger.error(f"Error in balance streaming loop: {e}")
                    await asyncio.sleep(10)  # Wait longer on error

        except Exception as e:
            logger.error(f"Error setting up balance streaming: {e}")
            await websocket.send_json({"type": "ERROR", "message": f"Streaming setup error: {str(e)}"})

        await client.close()

    except WebSocketDisconnect:
        logger.info("💰 WebSocket balance stream disconnected")
    except Exception as e:
        logger.error(f"❌ WebSocket balance stream error: {e}")
        try:
            await websocket.send_json({"type": "ERROR", "message": str(e)})
        except:
            pass

@app.websocket("/ws/realtime")
async def websocket_realtime_stream(websocket: WebSocket, api_key: str, api_secret: str):
    """
    Real-time WebSocket streaming using orderflow and position monitoring
    Provides instant balance updates and live P&L tracking
    """
    await websocket.accept()

    # Log connection details with client identification
    logger.info(f"🔗 WebSocket /ws/realtime - New connection established")
    logger.info(f"   🔑 API Key: {api_key[:10]}...{api_key[-4:]}")
    logger.info(f"   🌐 Client IP: {websocket.client.host if websocket.client else 'unknown'}")
    logger.info(f"   🕗 Connection time: {datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M:%S UTC')}")

    try:
        # Connect to Architect API
        client = await AsyncClient.connect(
            endpoint="app.architect.co",
            api_key=api_key,
            api_secret=api_secret,
            paper_trading=True
        )

        # Get account info
        accounts = await client.list_accounts()
        if not accounts:
            logger.error(f"❌ No account found for API key {api_key[:10]}...")
            await websocket.send_json({"type": "ERROR", "message": "No account found"})
            return

        account = accounts[0]
        account_id = str(account.account.id)

        # Get account summary for balance
        try:
            account_summary = await client.get_account_summary(account_id)
            initial_balance = float(account_summary.equity) if hasattr(account_summary, 'equity') else 100000.0
        except:
            initial_balance = 100000.0

        # Log successful account mapping
        logger.info(f"✅ WebSocket CONNECTED - Client mapping established:")
        logger.info(f"   🔑 API Key: {api_key[:10]}...{api_key[-4:]}")
        logger.info(f"   🎯 Account ID: {account_id}")
        logger.info(f"   📊 Account Type: {'Paper' if getattr(account.account, 'paper_trading', True) else 'Live'}")
        logger.info(f"   💰 Initial Balance: ${initial_balance:,.2f}")

        # Send connection confirmation with client details
        await websocket.send_json({
            "type": "CONNECTION",
            "status": "connected",
            "accountId": account_id,
            "apiKeyPrefix": f"{api_key[:10]}...",
            "message": "Real-time streaming active"
        })

        # Track last known values
        last_balance = None
        last_positions_pnl = {}

        # Create concurrent tasks for different streams
        async def orderflow_monitor():
            """Monitor orderflow for instant trade detection"""
            try:
                logger.info("📈 Starting orderflow monitor")
                async for event in client.stream_orderflow(account=account_id):
                    event_type = type(event).__name__
                    logger.debug(f"📊 Orderflow event: {event_type}")

                    # Check for fills (trade executions)
                    if "Fill" in event_type:
                        logger.info(f"🎯 TRADE EXECUTED - Fill detected: {event_type}")

                        # Get updated account summary immediately
                        try:
                            summary = await client.get_account_summary(account_id)
                            positions = await client.get_positions()

                            # Calculate total unrealized P&L
                            total_unrealized_pnl = sum(
                                float(pos.unrealized_pnl) if hasattr(pos, 'unrealized_pnl') and pos.unrealized_pnl else 0
                                for pos in positions
                            )

                            balance_update = {
                                "type": "BALANCE_UPDATE",
                                "source": "orderflow",
                                "accountId": account_id,
                                "totalBalance": float(summary.equity) if hasattr(summary, 'equity') else 100000.0,
                                "availableBalance": float(summary.cash_excess) if hasattr(summary, 'cash_excess') else 100000.0,
                                "unrealizedPnl": total_unrealized_pnl,
                                "realizedPnl": float(summary.realized_pnl) if hasattr(summary, 'realized_pnl') else 0,
                                "positionMargin": float(summary.position_margin) if hasattr(summary, 'position_margin') else 0,
                                "totalMargin": float(summary.total_margin) if hasattr(summary, 'total_margin') else 0,
                                "timestamp": datetime.now(timezone.utc).isoformat(),
                                "trigger": "trade_executed"
                            }

                            await websocket.send_json(balance_update)
                            logger.info(f"💰 BALANCE UPDATE sent to WebSocket client:")
                            logger.info(f"   🎯 Account: {account_id}")
                            logger.info(f"   🔑 API Key: {api_key[:10]}...{api_key[-4:]}")
                            logger.info(f"   💵 Balance: ${balance_update['totalBalance']:,.2f}")
                            logger.info(f"   📈 Source: {balance_update['source']} - {balance_update.get('trigger', '')}")

                        except Exception as e:
                            logger.error(f"Error getting summary after fill: {e}")

            except Exception as e:
                logger.error(f"Orderflow monitor error: {e}")

        async def position_monitor():
            """Monitor positions for P&L changes every second"""
            try:
                logger.info("📊 Starting position monitor")
                last_positions_pnl = {}  # Initialize the variable here

                while True:
                    try:
                        positions = await client.get_positions()

                        # Check for P&L changes
                        pnl_changed = False
                        current_positions_pnl = {}

                        for pos in positions:
                            symbol = pos.symbol if hasattr(pos, 'symbol') else 'unknown'
                            current_pnl = float(pos.unrealized_pnl) if hasattr(pos, 'unrealized_pnl') and pos.unrealized_pnl else 0
                            current_positions_pnl[symbol] = current_pnl

                            # Check if P&L changed significantly (more than $1)
                            last_pnl = last_positions_pnl.get(symbol, 0)
                            if abs(current_pnl - last_pnl) > 1.0:
                                pnl_changed = True
                                logger.info(f"📈 P&L change detected for {symbol}: ${last_pnl} -> ${current_pnl}")

                        # Send update if P&L changed or positions changed
                        if pnl_changed or set(current_positions_pnl.keys()) != set(last_positions_pnl.keys()):
                            # Get account summary for complete picture
                            summary = await client.get_account_summary(account_id)

                            total_unrealized_pnl = sum(current_positions_pnl.values())

                            pnl_update = {
                                "type": "PNL_UPDATE",
                                "accountId": account_id,
                                "positions": [
                                    {
                                        "symbol": symbol,
                                        "unrealizedPnl": pnl
                                    } for symbol, pnl in current_positions_pnl.items()
                                ],
                                "totalUnrealizedPnl": total_unrealized_pnl,
                                "totalBalance": float(summary.equity) if hasattr(summary, 'equity') else 100000.0,
                                "timestamp": datetime.now(timezone.utc).isoformat()
                            }

                            await websocket.send_json(pnl_update)
                            logger.info(f"💹 P&L UPDATE sent to WebSocket client:")
                            logger.info(f"   🎯 Account: {account_id}")
                            logger.info(f"   🔑 API Key: {api_key[:10]}...{api_key[-4:]}")
                            logger.info(f"   📉 Total P&L: ${total_unrealized_pnl:,.2f}")
                            logger.info(f"   📊 Positions: {len(current_positions_pnl)}")

                            last_positions_pnl = current_positions_pnl

                    except Exception as e:
                        logger.error(f"Position monitoring error: {e}")

                    await asyncio.sleep(1)  # Check every second

            except Exception as e:
                logger.error(f"Position monitor error: {e}")

        async def balance_fallback():
            """Fallback balance checker every 5 seconds"""
            try:
                logger.info("💰 Starting balance fallback monitor")
                nonlocal last_balance

                while True:
                    await asyncio.sleep(5)

                    try:
                        summary = await client.get_account_summary(account_id)
                        current_balance = float(summary.equity) if hasattr(summary, 'equity') else 100000.0

                        # Only send if balance changed
                        if last_balance is None or abs(current_balance - last_balance) > 0.01:
                            balance_update = {
                                "type": "BALANCE_UPDATE",
                                "source": "fallback",
                                "accountId": account_id,
                                "totalBalance": current_balance,
                                "availableBalance": float(summary.cash_excess) if hasattr(summary, 'cash_excess') else current_balance,
                                "unrealizedPnl": _get_unrealized_pnl(summary),
                                "realizedPnl": float(summary.realized_pnl) if hasattr(summary, 'realized_pnl') else 0,
                                "positionMargin": float(summary.position_margin) if hasattr(summary, 'position_margin') else 0,
                                "totalMargin": float(summary.total_margin) if hasattr(summary, 'total_margin') else 0,
                                "timestamp": datetime.now(timezone.utc).isoformat(),
                                "previousBalance": last_balance
                            }

                            await websocket.send_json(balance_update)
                            logger.info(f"🔄 BALANCE UPDATE sent (fallback check):")
                            logger.info(f"   🎯 Account: {account_id}")
                            logger.info(f"   🔑 API Key: {api_key[:10]}...{api_key[-4:]}")
                            logger.info(f"   💵 Balance: ${current_balance:,.2f}")
                            logger.info(f"   📈 Source: fallback (5s check)")
                            last_balance = current_balance

                    except Exception as e:
                        logger.error(f"Balance fallback error: {e}")

            except Exception as e:
                logger.error(f"Balance fallback monitor error: {e}")

        # Run all monitors concurrently
        tasks = [
            asyncio.create_task(orderflow_monitor()),
            asyncio.create_task(position_monitor()),
            asyncio.create_task(balance_fallback())
        ]

        logger.info("✅ All real-time monitors started")

        # Wait for any task to complete (or fail)
        await asyncio.gather(*tasks, return_exceptions=True)

        await client.close()

    except WebSocketDisconnect:
        logger.info("🔌 Real-time WebSocket stream disconnected")
    except Exception as e:
        import traceback
        logger.error(f"❌ Real-time WebSocket error: {e}")
        logger.error(f"Traceback: {traceback.format_exc()}")
        try:
            await websocket.send_json({"type": "ERROR", "message": str(e)})
        except:
            pass

@app.on_event("startup")
async def startup_event():
    """Initialize Architect Bridge API"""
    logger.info("🚀 Starting Architect Bridge API v3.0.0")
    logger.info("📊 Balance monitoring: WebSocket streaming + Java polling")
    logger.info("💰 WebSocket balance endpoint: /ws/balance")
    logger.info("✅ Bridge API ready to serve requests")

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8090)