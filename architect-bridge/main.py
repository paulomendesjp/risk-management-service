#!/usr/bin/env python3
"""
🌐 ARCHITECT BRIDGE - WEBSOCKET TO JAVA
======================================

Bridge service with WebSocket connection to Java Risk Monitoring Service
Implements real-time bidirectional communication between Python and Java

Features:
- WebSocket streaming from Architect.co API
- WebSocket connection TO Java Risk Service
- Real-time balance updates
- Automatic reconnection
- Heartbeat monitoring
"""

from fastapi import FastAPI, Header, HTTPException
from architect_py import AsyncClient, OrderDir, OrderType
from pydantic import BaseModel
from typing import Optional
import asyncio
import uvicorn
import logging
import json
import websockets
from datetime import datetime
import aiohttp
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

app = FastAPI(title="Architect Bridge - WebSocket to Java", version="3.0.0")

# MongoDB configuration
MONGO_URI = os.getenv('MONGO_URI', 'mongodb://localhost:27017/')
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

# Global state
monitoring_tasks = {}
client_credentials = {}
last_balances = {}
java_websocket = None
java_connection_task = None

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
        "service": "architect-bridge-websocket-java",
        "active_monitors": len([t for t in monitoring_tasks.values() if not t.done()]),
        "java_websocket_connected": java_websocket is not None and not java_websocket.close_code
    }

@app.post("/start-monitoring/{client_id}")
async def start_monitoring(client_id: str, api_key: str = Header(...), api_secret: str = Header(...)):
    """Start real-time WebSocket monitoring with Java connection"""
    request_id = generate_request_id()
    logger.info(f"[{request_id}] 🚀 Starting monitoring request for client: {client_id}")
    
    try:
        # Validate API key format
        if not api_key or len(api_key) != 24:
            logger.error(f"[{request_id}] ❌ Invalid API key format for client {client_id}: length={len(api_key) if api_key else 0}")
            raise HTTPException(status_code=400, detail="API key must be exactly 24 alphanumeric characters")
        
        if not api_secret or len(api_secret) < 20:
            logger.error(f"[{request_id}] ❌ Invalid API secret format for client {client_id}: length={len(api_secret) if api_secret else 0}")
            raise HTTPException(status_code=400, detail="API secret must be at least 20 characters")
        
        logger.debug(f"[{request_id}] 🔐 Storing credentials for client: {client_id}")
        client_credentials[client_id] = {"api_key": api_key, "api_secret": api_secret}
        
        # Ensure Java WebSocket connection
        logger.debug(f"[{request_id}] 🔗 Ensuring Java WebSocket connection...")
        await ensure_java_websocket_connection()
        
        # Cancel existing monitoring task if running
        if client_id in monitoring_tasks and not monitoring_tasks[client_id].done():
            logger.debug(f"[{request_id}] 🛑 Cancelling existing monitoring task for client: {client_id}")
            monitoring_tasks[client_id].cancel()
        
        # Start new monitoring task
        logger.debug(f"[{request_id}] 📊 Creating monitoring task for client: {client_id}")
        monitoring_tasks[client_id] = asyncio.create_task(monitor_client_realtime(client_id))
        
        logger.info(f"[{request_id}] ✅ Started WebSocket monitoring for {client_id} with Java connection")
        
        # MANDATORY AUDIT LOG - Client monitoring started
        audit_logger.info(f"CLIENT_MONITORING_STARTED|client_id={client_id}|request_id={request_id}|api_key_suffix={api_key[-4:]}|java_connected={java_websocket is not None and not java_websocket.close_code}")
        
        return {
            "status": "started", 
            "client_id": client_id, 
            "method": "websocket_to_java",
            "request_id": request_id,
            "java_connected": java_websocket is not None and not java_websocket.close_code
        }
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"[{request_id}] ❌ Error starting monitoring for {client_id}: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))

async def ensure_java_websocket_connection():
    """Ensure WebSocket connection to Java Risk Service"""
    global java_websocket, java_connection_task
    
    if java_websocket is None or java_websocket.close_code:
        if java_connection_task is None or java_connection_task.done():
            java_connection_task = asyncio.create_task(connect_to_java_websocket())
        await java_connection_task

async def connect_to_java_websocket():
    """Connect to Java Risk Service WebSocket"""
    global java_websocket
    
    max_retries = 5
    retry_delay = 2
    
    for attempt in range(max_retries):
        try:
            logger.info(f"🔗 Attempting to connect to Java WebSocket (attempt {attempt + 1})")
            
            # Use environment variable for Java WebSocket URL (Docker support)
            java_ws_url = os.getenv('JAVA_WEBSOCKET_URL', 'ws://localhost:8083/python-bridge')
            
            java_websocket = await websockets.connect(
                java_ws_url,
                ping_interval=30,
                ping_timeout=10
            )
            
            logger.info(f"✅ Connected to Java Risk Service at {java_ws_url}")
            return
            
        except Exception as e:
            logger.warning(f"⚠️ Failed to connect to Java WebSocket (attempt {attempt + 1}): {e}")
            if attempt < max_retries - 1:
                await asyncio.sleep(retry_delay)
                retry_delay *= 2
            else:
                logger.error("❌ Failed to connect to Java WebSocket after all retries")
                raise

async def monitor_client_realtime(client_id: str):
    """Monitor client with WebSocket streaming and send updates to Java"""
    monitoring_id = generate_request_id()
    logger.info(f"[{monitoring_id}] 🚀 Starting real-time monitoring for client: {client_id}")
    
    try:
        credentials = client_credentials.get(client_id)
        if not credentials:
            logger.error(f"[{monitoring_id}] ❌ No credentials found for client {client_id}")
            raise ValueError(f"No credentials found for client {client_id}")
        
        logger.debug(f"[{monitoring_id}] 🔐 Using credentials for client {client_id}: api_key=***{credentials['api_key'][-4:]}")
        
        # Connect to Architect WebSocket
        logger.debug(f"[{monitoring_id}] 🔗 Connecting to Architect WebSocket...")
        client = await AsyncClient.connect(
            endpoint="app.architect.co",
            api_key=credentials["api_key"],
            api_secret=credentials["api_secret"],
            paper_trading=True
        )
        logger.info(f"[{monitoring_id}] ✅ Connected to Architect API for client: {client_id}")
        
        # Get account info
        logger.debug(f"[{monitoring_id}] 📋 Fetching account information...")
        accounts = await client.list_accounts()
        if not accounts:
            logger.error(f"[{monitoring_id}] ❌ No accounts found for client {client_id}")
            raise ValueError(f"No accounts found for client {client_id}")
        
        account_id = str(accounts[0].account.id)
        logger.info(f"[{monitoring_id}] 📊 Connected to Architect WebSocket for account {account_id}")
        
        # Start WebSocket streaming
        logger.debug(f"[{monitoring_id}] 📡 Starting WebSocket streaming...")
        message_count = 0
        
        async for message in client.stream():
            message_count += 1
            logger.debug(f"[{monitoring_id}] 📨 Received message #{message_count} for client {client_id}")
            
            try:
                if hasattr(message, 'account_summary'):
                    # Process account summary updates
                    summary = message.account_summary
                    
                    current_balance = float(summary.net_liquidation_value) if summary.net_liquidation_value else 0.0
                    previous_balance = last_balances.get(client_id, current_balance)
                    balance_change = current_balance - previous_balance
                    
                    logger.debug(f"[{monitoring_id}] 💰 Balance update for {client_id}: ${current_balance:.2f} (change: ${balance_change:.2f})")
                    
                    # Check for significant balance changes (risk events)
                    if abs(balance_change) > 1000:  # Threshold for risk alerts
                        logger.warning(f"[{monitoring_id}] 🚨 Significant balance change detected for {client_id}: ${balance_change:.2f}")
                        
                        risk_data = {
                            "type": "balance_alert",
                            "client_id": client_id,
                            "account_id": account_id,
                            "previous_balance": previous_balance,
                            "current_balance": current_balance,
                            "change": balance_change,
                            "timestamp": datetime.now().isoformat(),
                            "monitoring_id": monitoring_id
                        }
                        
                        # Send to Java Risk Service
                        if java_websocket and not java_websocket.close_code:
                            await java_websocket.send(json.dumps(risk_data))
                            logger.info(f"[{monitoring_id}] 📤 Sent balance alert to Java: ${balance_change:.2f}")
                        else:
                            logger.warning(f"[{monitoring_id}] ⚠️ Java WebSocket not connected - balance alert not sent")
                    
                    last_balances[client_id] = current_balance
                    
                elif hasattr(message, 'fill'):
                    # Process fill updates
                    fill = message.fill
                    symbol = getattr(fill, 'symbol', 'unknown')
                    side = getattr(fill, 'side', 'unknown')
                    quantity = getattr(fill, 'quantity', 0)
                    price = getattr(fill, 'price', 0)
                    
                    logger.info(f"[{monitoring_id}] 📈 Fill received for {client_id}: {symbol} {side} {quantity}@{price}")
                    
                    fill_data = {
                        "type": "fill_update",
                        "client_id": client_id,
                        "account_id": account_id,
                        "fill_info": {
                            "symbol": symbol,
                            "side": side,
                            "quantity": quantity,
                            "price": price,
                            "timestamp": getattr(fill, 'timestamp', 'unknown')
                        },
                        "timestamp": datetime.now().isoformat(),
                        "monitoring_id": monitoring_id
                    }
                    
                    # Send to Java Risk Service
                    if java_websocket and not java_websocket.close_code:
                        await java_websocket.send(json.dumps(fill_data))
                        logger.info(f"[{monitoring_id}] 📤 Sent fill update to Java: {symbol}")
                    else:
                        logger.warning(f"[{monitoring_id}] ⚠️ Java WebSocket not connected - fill update not sent")
                
                else:
                    logger.debug(f"[{monitoring_id}] 📝 Unhandled message type for client {client_id}: {type(message)}")
                
            except Exception as e:
                logger.error(f"[{monitoring_id}] ❌ Error processing WebSocket message for {client_id}: {e}", exc_info=True)
                continue
                
    except Exception as e:
        logger.error(f"[{monitoring_id}] ❌ Error in real-time monitoring for {client_id}: {e}", exc_info=True)
        raise
    finally:
        logger.info(f"[{monitoring_id}] 🛑 Stopping monitoring for client: {client_id}")
        if client_id in client_credentials:
            del client_credentials[client_id]
            logger.debug(f"[{monitoring_id}] 🗑️ Cleaned up credentials for client: {client_id}")

@app.post("/place-order")
@app.post("/place_order")  # Also support underscore version
async def place_order(
    order_data: OrderData,
    api_key: str = Header(...), 
    api_secret: str = Header(...)
):
    """Place a real order via Architect API (matching original working code)"""
    try:
        logger.info(f"📈 Placing REAL order via Architect API: {order_data.dict()}")

        # Log received data for debugging
        logger.debug(f"Received order data - action: {order_data.action}, orderType: {order_data.orderType}")

        # Default values if missing
        if not order_data.action:
            logger.warning("Action is missing, defaulting to BUY")
            order_data.action = "BUY"
        if not order_data.orderType:
            logger.warning("OrderType is missing, defaulting to MARKET")
            order_data.orderType = "MARKET"
        
        # Connect to Architect API
        client = await AsyncClient.connect(
            endpoint="app.architect.co",
            api_key=api_key,
            api_secret=api_secret,
            paper_trading=True  # Set to False for live trading
        )
        
        # Convert action to OrderDir (like original code)
        if order_data.action.lower() == "buy":
            direction = OrderDir.BUY
        elif order_data.action.lower() == "sell":
            direction = OrderDir.SELL
        else:
            raise HTTPException(status_code=400, detail=f"Invalid action: {order_data.action}")
        
        # Convert order type to enum
        if order_data.orderType.upper() == "MARKET":
            order_type = OrderType.MARKET
        elif order_data.orderType.upper() == "LIMIT":
            order_type = OrderType.LIMIT
        else:
            order_type = OrderType.MARKET
        
        # Get account ID (like original code)
        accounts = await client.list_accounts()
        account_id = str(accounts[0].account.id)
        
        logger.info(f"🔗 Placing order: {order_data.symbol} {direction} {order_data.orderQty} {order_type}")
        
        # Place order using the SAME method as original working code
        qty = Decimal(str(order_data.orderQty))
        
        # Fix symbol format - Architect.co expects full format with valid expiration dates
        symbol = order_data.symbol
        if symbol == "6A":
            symbol = "6A 20250214 CME Future"  # Australian Dollar - February 2025
        elif symbol == "ES":
            symbol = "ES 20241220 CME Future"  # E-mini S&P 500 - December 2024 (quarterly)
        elif symbol == "GC":
            symbol = "GC 20250626 CME Future"  # Gold - June 2025
        elif symbol == "CL":
            symbol = "CL 20241220 CME Future"  # Crude Oil - December 2024
        elif symbol == "NQ":
            symbol = "NQ 20241220 CME Future"  # E-mini NASDAQ - December 2024
        # Add more symbol mappings as needed - use quarterly expirations (Mar, Jun, Sep, Dec)
        
        logger.info(f"🔧 Using corrected symbol format: {symbol}")
        
        if order_type == OrderType.MARKET:
            # MARKET order (like original code)
            order_response = await client.place_order(
                symbol=symbol,
                execution_venue="CME",  # Add execution venue like original code
                dir=direction,
                quantity=qty,
                order_type=OrderType.MARKET,
                account=account_id
            )
        elif order_type == OrderType.LIMIT and order_data.price:
            # LIMIT order
            order_response = await client.place_order(
                symbol=symbol,
                execution_venue="CME",
                dir=direction,
                quantity=qty,
                order_type=OrderType.LIMIT,
                limit_price=Decimal(str(order_data.price)),
                account=account_id
            )
        else:
            raise HTTPException(status_code=400, detail="Invalid order type or missing price for LIMIT order")
        
        # Process response (like original code)
        result = {
            "success": True,
            "orderId": str(order_response.id) if hasattr(order_response, 'id') else f"ARCH-{int(datetime.now().timestamp())}",
            "clientId": order_data.clientId,
            "symbol": order_data.symbol,
            "action": order_data.action,
            "quantity": order_data.orderQty,
            "orderType": order_data.orderType,
            "status": str(order_response.status) if hasattr(order_response, 'status') else "SUBMITTED",
            "timestamp": datetime.now().isoformat(),
            "message": "Order placed successfully via Architect API",
            "source": "architect_api_real"
        }
        
        # Add price info if available
        if hasattr(order_response, 'price') and order_response.price:
            result["price"] = float(order_response.price)
        if hasattr(order_response, 'filled_price') and order_response.filled_price:
            result["fillPrice"] = float(order_response.filled_price)

        # Save to MongoDB if available
        if db is not None and order_log_col is not None:
            try:
                order_doc = {
                    **result,
                    "_id": result["orderId"],
                    "createdAt": datetime.now(),
                    "updatedAt": datetime.now()
                }
                order_log_col.insert_one(order_doc)
                logger.info(f"📝 Order saved to MongoDB: {result['orderId']}")
            except Exception as e:
                logger.warning(f"⚠️ Failed to save order to MongoDB: {e}")

        await client.close()

        logger.info(f"✅ Order placed successfully: {result['orderId']}")
        return result
        
    except Exception as e:
        logger.error(f"❌ Error placing order: {e}")
        raise HTTPException(status_code=500, detail=f"Order placement failed: {str(e)}")

@app.get("/accounts")
async def get_accounts(
    api_key: Optional[str] = Header(None, alias="api-key"),
    api_secret: Optional[str] = Header(None, alias="api-secret"),
    api_key_underscore: Optional[str] = Header(None, alias="api_key"),
    api_secret_underscore: Optional[str] = Header(None, alias="api_secret")
):
    """Get account information using only API credentials"""
    # Accept both formats: api-key and api_key
    api_key = api_key or api_key_underscore
    api_secret = api_secret or api_secret_underscore

    if not api_key or not api_secret:
        raise HTTPException(status_code=400, detail="API key and secret are required")
    try:
        logger.info(f"📊 Fetching accounts using API credentials")

        # Connect to Architect API
        client = await AsyncClient.connect(
            endpoint="app.architect.co",
            api_key=api_key,
            api_secret=api_secret,
            paper_trading=True
        )

        # Get accounts
        accounts = await client.list_accounts()

        if not accounts:
            raise HTTPException(status_code=404, detail="No accounts found")

        # Return first account info
        account = accounts[0]
        account_info = {
            "accountId": str(account.account.id) if hasattr(account.account, 'id') else "unknown",
            "totalBalance": float(account.account.total_balance) if hasattr(account.account, 'total_balance') else 0,
            "availableBalance": float(account.account.available_balance) if hasattr(account.account, 'available_balance') else 0,
            "usedMargin": float(account.account.used_margin) if hasattr(account.account, 'used_margin') else 0,
            "timestamp": datetime.now().isoformat()
        }

        await client.close()

        logger.info(f"✅ Account info fetched")
        return [account_info]  # Return as array to match expected format

    except Exception as e:
        logger.error(f"❌ Error fetching accounts: {e}")
        raise HTTPException(status_code=500, detail=f"Failed to fetch accounts: {str(e)}")

@app.get("/accounts/balance")
async def get_balance(
    api_key: Optional[str] = Header(None, alias="api-key"),
    api_secret: Optional[str] = Header(None, alias="api-secret"),
    api_key_underscore: Optional[str] = Header(None, alias="api_key"),
    api_secret_underscore: Optional[str] = Header(None, alias="api_secret")
):
    """Get account balance using only API credentials (no client_id needed)"""
    # Accept both formats: api-key and api_key
    api_key = api_key or api_key_underscore
    api_secret = api_secret or api_secret_underscore

    if not api_key or not api_secret:
        raise HTTPException(status_code=400, detail="API key and secret are required")
    try:
        logger.info(f"💰 Fetching balance using API credentials")

        # Connect to Architect API
        client = await AsyncClient.connect(
            endpoint="app.architect.co",
            api_key=api_key,
            api_secret=api_secret,
            paper_trading=True
        )

        # Get account balance
        accounts = await client.list_accounts()
        account = accounts[0] if accounts else None

        if not account:
            raise HTTPException(status_code=404, detail="No account found")

        balance_info = {
            "accountId": str(account.account.id) if hasattr(account.account, 'id') else "unknown",
            "totalBalance": float(account.account.total_balance) if hasattr(account.account, 'total_balance') else 0,
            "availableBalance": float(account.account.available_balance) if hasattr(account.account, 'available_balance') else 0,
            "usedMargin": float(account.account.used_margin) if hasattr(account.account, 'used_margin') else 0,
            "unrealizedPnl": float(account.account.unrealized_pnl) if hasattr(account.account, 'unrealized_pnl') else 0,
            "realizedPnl": float(account.account.realized_pnl) if hasattr(account.account, 'realized_pnl') else 0,
            "timestamp": datetime.now().isoformat()
        }

        await client.close()

        logger.info(f"✅ Balance fetched: ${balance_info['totalBalance']}")
        return balance_info

    except Exception as e:
        logger.error(f"❌ Error fetching balance: {e}")
        raise HTTPException(status_code=500, detail=f"Failed to fetch balance: {str(e)}")

@app.get("/account-balance/{client_id}")
async def get_account_balance(client_id: str, api_key: str = Header(...), api_secret: str = Header(...)):
    """Get real-time account balance from Architect API (legacy endpoint with client_id)"""
    try:
        logger.info(f"💰 Fetching balance for client: {client_id}")

        # Connect to Architect API
        client = await AsyncClient.connect(
            endpoint="app.architect.co",
            api_key=api_key,
            api_secret=api_secret,
            paper_trading=True
        )

        # Get account balance
        accounts = await client.list_accounts()
        account = accounts[0] if accounts else None

        if not account:
            raise HTTPException(status_code=404, detail="No account found")

        balance_info = {
            "clientId": client_id,
            "totalBalance": float(account.account.total_balance) if hasattr(account.account, 'total_balance') else 0,
            "availableBalance": float(account.account.available_balance) if hasattr(account.account, 'available_balance') else 0,
            "usedMargin": float(account.account.used_margin) if hasattr(account.account, 'used_margin') else 0,
            "unrealizedPnl": float(account.account.unrealized_pnl) if hasattr(account.account, 'unrealized_pnl') else 0,
            "realizedPnl": float(account.account.realized_pnl) if hasattr(account.account, 'realized_pnl') else 0,
            "timestamp": datetime.now().isoformat()
        }

        await client.close()

        logger.info(f"✅ Balance fetched: ${balance_info['totalBalance']}")
        return balance_info

    except Exception as e:
        logger.error(f"❌ Error fetching balance: {e}")
        raise HTTPException(status_code=500, detail=f"Failed to fetch balance: {str(e)}")

@app.get("/positions/{client_id}")
async def get_positions(client_id: str, api_key: str = Header(...), api_secret: str = Header(...)):
    """Get all open positions for a client from Architect API"""
    try:
        logger.info(f"📊 Fetching positions for client: {client_id}")

        # Connect to Architect API
        client = await AsyncClient.connect(
            endpoint="app.architect.co",
            api_key=api_key,
            api_secret=api_secret,
            paper_trading=True
        )

        # Get positions
        positions = await client.list_positions()

        positions_list = []
        for pos in positions:
            position_data = {
                "positionId": str(pos.id) if hasattr(pos, 'id') else f"POS-{int(time.time())}",
                "symbol": pos.symbol if hasattr(pos, 'symbol') else "UNKNOWN",
                "side": str(pos.dir) if hasattr(pos, 'dir') else "LONG",
                "quantity": float(pos.quantity) if hasattr(pos, 'quantity') else 0,
                "averagePrice": float(pos.avg_price) if hasattr(pos, 'avg_price') else 0,
                "currentPrice": float(pos.current_price) if hasattr(pos, 'current_price') else 0,
                "unrealizedPnl": float(pos.unrealized_pnl) if hasattr(pos, 'unrealized_pnl') else 0,
                "realizedPnl": float(pos.realized_pnl) if hasattr(pos, 'realized_pnl') else 0,
                "status": "OPEN"
            }
            positions_list.append(position_data)

        await client.close()

        logger.info(f"✅ Found {len(positions_list)} positions for client {client_id}")
        return {"clientId": client_id, "positions": positions_list, "count": len(positions_list)}

    except Exception as e:
        logger.error(f"❌ Error fetching positions: {e}")
        raise HTTPException(status_code=500, detail=f"Failed to fetch positions: {str(e)}")

@app.post("/close-position")
async def close_position(position_id: str, api_key: str = Header(...), api_secret: str = Header(...)):
    """Close a specific position"""
    try:
        logger.info(f"🔴 Closing position: {position_id}")

        # Connect to Architect API
        client = await AsyncClient.connect(
            endpoint="app.architect.co",
            api_key=api_key,
            api_secret=api_secret,
            paper_trading=True
        )

        # Get position details first
        positions = await client.list_positions()
        target_position = None

        for pos in positions:
            if str(pos.id) == position_id or pos.symbol == position_id:
                target_position = pos
                break

        if not target_position:
            raise HTTPException(status_code=404, detail=f"Position {position_id} not found")

        # Place opposite order to close position
        opposite_dir = OrderDir.SELL if target_position.dir == OrderDir.BUY else OrderDir.BUY

        order_response = await client.place_order(
            symbol=target_position.symbol,
            execution_venue="CME",
            dir=opposite_dir,
            quantity=target_position.quantity,
            order_type=OrderType.MARKET,
            account=str((await client.list_accounts())[0].account.id)
        )

        await client.close()

        result = {
            "success": True,
            "positionId": position_id,
            "orderId": str(order_response.id) if hasattr(order_response, 'id') else "CLOSE-ORDER",
            "message": f"Position {position_id} closed successfully"
        }

        logger.info(f"✅ Position closed: {position_id}")
        return result

    except Exception as e:
        logger.error(f"❌ Error closing position: {e}")
        raise HTTPException(status_code=500, detail=f"Failed to close position: {str(e)}")

@app.post("/close-all-positions/{client_id}")
async def close_all_positions(client_id: str, reason: str = "RISK_VIOLATION",
                              api_key: str = Header(...), api_secret: str = Header(...)):
    """Close all open positions for a client (called by Risk Service)"""
    try:
        logger.warning(f"🚨 CLOSING ALL POSITIONS for client {client_id}. Reason: {reason}")

        # Log to mandatory audit
        audit_logger.info(f"RISK_ACTION: Closing all positions for {client_id}. Reason: {reason}")

        # Connect to Architect API
        client = await AsyncClient.connect(
            endpoint="app.architect.co",
            api_key=api_key,
            api_secret=api_secret,
            paper_trading=True
        )

        # Get all positions
        positions = await client.list_positions()
        accounts = await client.list_accounts()
        account_id = str(accounts[0].account.id)

        closed_count = 0
        failed_count = 0
        closed_positions = []

        for pos in positions:
            try:
                # Place opposite order to close each position
                opposite_dir = OrderDir.SELL if pos.dir == OrderDir.BUY else OrderDir.BUY

                order_response = await client.place_order(
                    symbol=pos.symbol,
                    execution_venue="CME",
                    dir=opposite_dir,
                    quantity=pos.quantity,
                    order_type=OrderType.MARKET,
                    account=account_id
                )

                closed_count += 1
                closed_positions.append({
                    "positionId": str(pos.id) if hasattr(pos, 'id') else f"POS-{closed_count}",
                    "symbol": pos.symbol,
                    "quantity": float(pos.quantity),
                    "closeOrderId": str(order_response.id) if hasattr(order_response, 'id') else f"CLOSE-{closed_count}"
                })

                logger.info(f"✅ Closed position: {pos.symbol} x {pos.quantity}")

            except Exception as e:
                failed_count += 1
                logger.error(f"❌ Failed to close position {pos.symbol}: {e}")

        await client.close()

        result = {
            "success": closed_count > 0,
            "clientId": client_id,
            "closedCount": closed_count,
            "failedCount": failed_count,
            "closedPositions": closed_positions,
            "reason": reason,
            "timestamp": datetime.now().isoformat(),
            "message": f"Closed {closed_count} positions, {failed_count} failed"
        }

        # Send notification to Java WebSocket if connected
        if java_websocket and not java_websocket.close_code:
            await send_to_java_websocket({
                "type": "positions_closed",
                "data": result
            })

        logger.warning(f"⚠️ Risk action completed: {closed_count} positions closed for {client_id}")
        audit_logger.info(f"RISK_ACTION_COMPLETE: {closed_count} positions closed, {failed_count} failed for {client_id}")

        return result

    except Exception as e:
        logger.error(f"❌ Error closing all positions: {e}")
        audit_logger.error(f"RISK_ACTION_FAILED: Failed to close positions for {client_id}: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Failed to close all positions: {str(e)}")

@app.post("/stop-monitoring/{client_id}")
async def stop_monitoring(client_id: str):
    """Stop real-time WebSocket monitoring for a client"""
    try:
        if client_id in monitoring_tasks:
            monitoring_tasks[client_id].cancel()
            del monitoring_tasks[client_id]
            logger.info(f"🛑 Stopped monitoring for client: {client_id}")
        
        if client_id in client_credentials:
            del client_credentials[client_id]
        
        if client_id in last_balances:
            del last_balances[client_id]
            
        return {"status": "stopped", "client_id": client_id}
        
    except Exception as e:
        logger.error(f"❌ Error stopping monitoring: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/start-all-monitoring")
async def start_all_monitoring():
    """Start monitoring for all registered clients by fetching from User Service"""
    try:
        logger.info("🚀 Starting monitoring for all registered clients")
        
        # Call User Service to get all registered clients
        async with aiohttp.ClientSession() as session:
            async with session.get("http://localhost:8080/api/users/all") as response:
                if response.status == 200:
                    users = await response.json()
                    
                    started_count = 0
                    for user in users:
                        client_id = user.get("clientId")
                        if client_id:
                            # Get credentials for this client
                            async with session.get(f"http://localhost:8080/api/users/{client_id}/credentials") as cred_response:
                                if cred_response.status == 200:
                                    credentials = await cred_response.json()
                                    
                                    # Start monitoring for this client
                                    client_credentials[client_id] = {
                                        "api_key": credentials.get("apiKey"),
                                        "api_secret": credentials.get("apiSecret")
                                    }
                                    
                                    if client_id in monitoring_tasks and not monitoring_tasks[client_id].done():
                                        monitoring_tasks[client_id].cancel()
                                    
                                    monitoring_tasks[client_id] = asyncio.create_task(monitor_client_realtime(client_id))
                                    started_count += 1
                                    logger.info(f"✅ Started monitoring for client: {client_id}")
                    
                    return {
                        "status": "success",
                        "message": f"Started monitoring for {started_count} clients",
                        "clients_monitored": started_count
                    }
                else:
                    raise HTTPException(status_code=500, detail="Failed to fetch users from User Service")
                    
    except Exception as e:
        logger.error(f"❌ Error starting all monitoring: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/monitoring-status")
async def get_monitoring_status():
    """Get current monitoring status"""
    active_clients = [client_id for client_id, task in monitoring_tasks.items() if not task.done()]
    
    return {
        "active_monitoring": len(active_clients),
        "clients": active_clients,
        "java_websocket_connected": java_websocket is not None and not java_websocket.close_code,
        "total_credentials_stored": len(client_credentials)
    }

@app.get("/get-java-connection-status")
async def get_java_connection_status():
    """Get Java WebSocket connection status"""
    return {
        "connected": java_websocket is not None and not java_websocket.close_code,
        "websocket_url": os.getenv('JAVA_WEBSOCKET_URL', 'ws://localhost:8083/python-bridge')
    }

@app.get("/get-orders")
async def get_orders(
    api_key: Optional[str] = Header(None, alias="api-key"),
    api_secret: Optional[str] = Header(None, alias="api-secret"),
    api_key_underscore: Optional[str] = Header(None, alias="api_key"),
    api_secret_underscore: Optional[str] = Header(None, alias="api_secret")
):
    """Get all open orders from Architect API - returns ArchitectOrderResponse format"""
    # Accept both formats: api-key and api_key
    api_key = api_key or api_key_underscore
    api_secret = api_secret or api_secret_underscore

    if not api_key or not api_secret:
        raise HTTPException(status_code=400, detail="API key and secret are required")

    try:
        logger.info("📋 Fetching orders from Architect API")

        # Connect to Architect API
        client = await AsyncClient.connect(
            endpoint="app.architect.co",
            api_key=api_key,
            api_secret=api_secret,
            paper_trading=True
        )

        # Get open orders
        open_orders = await client.get_open_orders()

        if not open_orders:
            # Return empty ArchitectOrderResponse if no orders
            empty_response = {
                "orderId": None,
                "clientOrderId": None,
                "symbol": None,
                "side": None,
                "quantity": None,
                "orderQty": None,
                "filledQty": None,
                "orderType": None,
                "status": None,
                "price": None,
                "fillPrice": None,
                "averageFillPrice": None,
                "timestamp": datetime.now().isoformat(),
                "updatedAt": datetime.now().isoformat(),
                "message": "No open orders found"
            }
            await client.close()
            return empty_response

        # For now, return the first order in ArchitectOrderResponse format
        # TODO: The Java client expects a single response, but we have multiple orders
        # This might need to be refactored to return a list
        order = open_orders[0]

        order_response = {
            "orderId": str(order.order.id) if hasattr(order.order, 'id') else None,
            "clientOrderId": str(order.order.client_order_id) if hasattr(order.order, 'client_order_id') else None,
            "symbol": str(order.order.symbol) if hasattr(order.order, 'symbol') else None,
            "side": str(order.order.dir) if hasattr(order.order, 'dir') else None,
            "quantity": float(order.order.quantity) if hasattr(order.order, 'quantity') else None,
            "orderQty": float(order.order.quantity) if hasattr(order.order, 'quantity') else None,
            "filledQty": float(order.order.filled_quantity) if hasattr(order.order, 'filled_quantity') else 0,
            "orderType": str(order.order.type) if hasattr(order.order, 'type') else None,
            "status": "OPEN" if hasattr(order.order, 'status') and str(order.order.status) == "OrderStatus.OPEN" else str(order.order.status) if hasattr(order.order, 'status') else None,
            "price": float(order.order.price) if hasattr(order.order, 'price') and order.order.price else None,
            "fillPrice": float(order.order.average_fill_price) if hasattr(order.order, 'average_fill_price') and order.order.average_fill_price else None,
            "averageFillPrice": float(order.order.average_fill_price) if hasattr(order.order, 'average_fill_price') and order.order.average_fill_price else None,
            "timestamp": str(order.order.created_at) if hasattr(order.order, 'created_at') else datetime.now().isoformat(),
            "updatedAt": str(order.order.updated_at) if hasattr(order.order, 'updated_at') else datetime.now().isoformat(),
            "message": f"Found {len(open_orders)} open orders, returning first"
        }

        await client.close()

        logger.info(f"✅ Found {len(open_orders)} orders, returning first in ArchitectOrderResponse format")
        return order_response

    except Exception as e:
        logger.error(f"❌ Error fetching orders: {e}")
        # Return error in ArchitectOrderResponse format
        return {
            "orderId": None,
            "clientOrderId": None,
            "symbol": None,
            "side": None,
            "quantity": None,
            "orderQty": None,
            "filledQty": None,
            "orderType": None,
            "status": None,
            "price": None,
            "fillPrice": None,
            "averageFillPrice": None,
            "timestamp": datetime.now().isoformat(),
            "updatedAt": datetime.now().isoformat(),
            "message": f"Error: {str(e)}"
        }

@app.get("/orders/list")
async def get_orders_list(
    api_key: Optional[str] = Header(None, alias="api-key"),
    api_secret: Optional[str] = Header(None, alias="api-secret"),
    api_key_underscore: Optional[str] = Header(None, alias="api_key"),
    api_secret_underscore: Optional[str] = Header(None, alias="api_secret")
):
    """Get all open orders as a list - for Java service compatibility"""
    # Accept both formats: api-key and api_key
    api_key = api_key or api_key_underscore
    api_secret = api_secret or api_secret_underscore

    if not api_key or not api_secret:
        raise HTTPException(status_code=400, detail="API key and secret are required")

    try:
        logger.info("📋 Fetching orders list from Architect API")

        # Connect to Architect API
        client = await AsyncClient.connect(
            endpoint="app.architect.co",
            api_key=api_key,
            api_secret=api_secret,
            paper_trading=True
        )

        # Get open orders
        open_orders = await client.get_open_orders()
        orders_list = []

        for order in open_orders:
            order_response = {
                "orderId": str(order.id) if hasattr(order, 'id') else None,
                "clientOrderId": str(order.client_order_id) if hasattr(order, 'client_order_id') else None,
                "symbol": str(order.symbol) if hasattr(order, 'symbol') else None,
                "side": str(order.dir) if hasattr(order, 'dir') else None,
                "quantity": float(order.quantity) if hasattr(order, 'quantity') else None,
                "orderQty": float(order.quantity) if hasattr(order, 'quantity') else None,
                "filledQty": float(order.filled_quantity) if hasattr(order, 'filled_quantity') else 0,
                "orderType": str(order.type) if hasattr(order, 'type') else None,
                "status": str(order.status) if hasattr(order, 'status') else "OPEN",
                "price": float(order.price) if hasattr(order, 'price') and order.price else None,
                "fillPrice": float(order.average_fill_price) if hasattr(order, 'average_fill_price') and order.average_fill_price else None,
                "averageFillPrice": float(order.average_fill_price) if hasattr(order, 'average_fill_price') and order.average_fill_price else None,
                "timestamp": str(order.created_at) if hasattr(order, 'created_at') else datetime.now().isoformat(),
                "updatedAt": str(order.updated_at) if hasattr(order, 'updated_at') else datetime.now().isoformat(),
                "message": None
            }
            orders_list.append(order_response)

        await client.close()

        logger.info(f"✅ Found {len(orders_list)} orders")
        return orders_list  # Return as list directly

    except Exception as e:
        logger.error(f"❌ Error fetching orders list: {e}")
        raise HTTPException(status_code=500, detail=f"Failed to fetch orders: {str(e)}")

@app.post("/status")
async def get_simple_order_status(order_id: str, api_key: str = Header(...), api_secret: str = Header(...)):
    """Get the status of any order by its ID - simplified version from original Python"""
    try:
        logger.info(f"🔍 Checking status for order (simple): {order_id}")

        # Connect to Architect API
        client = await AsyncClient.connect(
            endpoint="app.architect.co",
            api_key=api_key,
            api_secret=api_secret,
            paper_trading=True
        )

        # First, check in open orders
        open_orders = await client.get_open_orders()

        for order in open_orders:
            if str(order.id) == order_id:
                result = {
                    "order_id": str(order.id),
                    "status": str(order.status) if hasattr(order, 'status') else "UNKNOWN",
                    "symbol": str(order.symbol) if hasattr(order, 'symbol') else "UNKNOWN",
                    "quantity": float(order.quantity) if hasattr(order, 'quantity') else 0.0,
                    "found_in": "open_orders"
                }
                await client.close()
                logger.info(f"✅ Found order in open orders (simple): {order_id}")
                return result

        await client.close()
        logger.info(f"❌ Order not found (simple): {order_id}")
        return None

    except Exception as e:
        logger.error(f"❌ Error checking order status (simple): {e}")
        return None

@app.get("/get-order-status/{order_id}")
async def get_order_status(order_id: str, api_key: str = Header(...), api_secret: str = Header(...)):
    """Get status of a specific order by ID"""
    try:
        logger.info(f"🔍 Checking status for order: {order_id}")
        
        # Connect to Architect API
        client = await AsyncClient.connect(
            endpoint="app.architect.co",
            api_key=api_key,
            api_secret=api_secret,
            paper_trading=True
        )
        
        # First, check in open orders
        open_orders = await client.get_open_orders()
        
        for order in open_orders:
            if str(order.id) == order_id:
                # Format response to match Java ArchitectOrderResponse DTO
                result = {
                    "orderId": str(order.id),
                    "clientOrderId": str(order.client_order_id) if hasattr(order, 'client_order_id') else None,
                    "status": str(order.status) if hasattr(order, 'status') else "UNKNOWN",
                    "symbol": str(order.symbol) if hasattr(order, 'symbol') else "UNKNOWN",
                    "side": str(order.side) if hasattr(order, 'side') else "BUY",
                    "quantity": float(order.quantity) if hasattr(order, 'quantity') else 0.0,  # BigDecimal expects numeric
                    "orderQty": float(order.quantity) if hasattr(order, 'quantity') else 0.0,
                    "filledQty": float(order.filled_quantity) if hasattr(order, 'filled_quantity') else 0.0,
                    "orderType": str(order.type) if hasattr(order, 'type') else "MARKET",
                    "timestamp": str(order.timestamp) if hasattr(order, 'timestamp') else None,
                    "updatedAt": str(order.updated_at) if hasattr(order, 'updated_at') else None,
                    "price": float(order.price) if hasattr(order, 'price') and order.price else None,
                    "fillPrice": float(order.fill_price) if hasattr(order, 'fill_price') and order.fill_price else None,
                    "averageFillPrice": float(order.average_fill_price) if hasattr(order, 'average_fill_price') and order.average_fill_price else None,
                    "message": "Order found in open orders"
                }
                await client.close()
                logger.info(f"✅ Found order in open orders: {order_id}")
                return result
        
        # If not found in open orders, try to get order directly
        try:
            order_info = await client.get_order(order_id)
            if order_info:
                result = {
                    "orderId": str(order_info.id),
                    "status": str(order_info.status) if hasattr(order_info, 'status') else "FILLED",
                    "symbol": str(order_info.symbol) if hasattr(order_info, 'symbol') else "UNKNOWN",
                    "side": str(order_info.side) if hasattr(order_info, 'side') else "BUY",
                    "quantity": int(order_info.quantity) if hasattr(order_info, 'quantity') else 0,
                    "orderType": str(order_info.type) if hasattr(order_info, 'type') else "MARKET",
                    "found_in": "order_history",
                    "timestamp": str(order_info.timestamp) if hasattr(order_info, 'timestamp') else "unknown",
                    "price": float(order_info.price) if hasattr(order_info, 'price') and order_info.price else None,
                    "filled_quantity": int(order_info.filled_quantity) if hasattr(order_info, 'filled_quantity') else 0,
                    "averageFillPrice": float(order_info.average_fill_price) if hasattr(order_info, 'average_fill_price') and order_info.average_fill_price else None
                }
                await client.close()
                logger.info(f"✅ Found order in history: {order_id}")
                return result
        except Exception as e:
            logger.warning(f"Could not get order directly: {e}")
        
        await client.close()
        logger.warning(f"⚠️ Order not found: {order_id}")
        return {"error": f"Order {order_id} not found", "found": False}
        
    except Exception as e:
        logger.error(f"❌ Error checking order status: {e}")
        raise HTTPException(status_code=500, detail=f"Failed to check order status: {str(e)}")

@app.on_event("startup")
async def startup_event():
    """Initialize monitoring recovery on startup"""
    logger.info("🚀 Starting Architect Bridge - WebSocket to Java")
    logger.info("🔄 Attempting to recover monitoring for existing clients...")
    
    try:
        # Try to start monitoring for all registered clients
        await startup_monitoring_recovery()
    except Exception as e:
        logger.error(f"❌ Error during startup monitoring recovery: {e}")
        # Don't fail startup if recovery fails

async def startup_monitoring_recovery():
    """Recover monitoring for all registered clients on startup"""
    try:
        logger.info("🔍 Checking for existing clients to monitor...")
        
        # Try to call User Service to get all registered clients
        async with aiohttp.ClientSession() as session:
            try:
                async with session.get("http://localhost:8080/api/users/all", timeout=5) as response:
                    if response.status == 200:
                        users = await response.json()
                        
                        if users:
                            logger.info(f"📋 Found {len(users)} registered clients")
                            
                            recovered_count = 0
                            for user in users:
                                client_id = user.get("clientId")
                                if client_id:
                                    try:
                                        # Get credentials for this client
                                        async with session.get(f"http://localhost:8080/api/users/{client_id}/credentials", timeout=5) as cred_response:
                                            if cred_response.status == 200:
                                                credentials = await cred_response.json()
                                                
                                                # Start monitoring for this client
                                                client_credentials[client_id] = {
                                                    "api_key": credentials.get("apiKey"),
                                                    "api_secret": credentials.get("apiSecret")
                                                }
                                                
                                                # Ensure Java WebSocket connection
                                                await ensure_java_websocket_connection()
                                                
                                                if client_id in monitoring_tasks and not monitoring_tasks[client_id].done():
                                                    monitoring_tasks[client_id].cancel()
                                                
                                                monitoring_tasks[client_id] = asyncio.create_task(monitor_client_realtime(client_id))
                                                recovered_count += 1
                                                logger.info(f"✅ Recovered monitoring for client: {client_id}")
                                    except Exception as e:
                                        logger.error(f"❌ Failed to recover monitoring for client {client_id}: {e}")
                            
                            if recovered_count > 0:
                                logger.info(f"🎉 Successfully recovered monitoring for {recovered_count} clients")
                            else:
                                logger.info("📝 No clients needed monitoring recovery")
                        else:
                            logger.info("📝 No registered clients found")
                    else:
                        logger.warning(f"⚠️ User Service not available (status: {response.status})")
            except asyncio.TimeoutError:
                logger.warning("⚠️ User Service connection timeout - skipping recovery")
            except Exception as e:
                logger.warning(f"⚠️ Could not connect to User Service: {e}")
                
    except Exception as e:
        logger.error(f"❌ Error in startup monitoring recovery: {e}")

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8090)

