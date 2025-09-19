#!/usr/bin/env python3
"""
Test the correct way to use architect-py v2.1.0
"""
import asyncio
import inspect

try:
    from architect_py import AsyncClient

    print("AsyncClient imported successfully")
    print(f"\nAsyncClient __init__ signature:")
    sig = inspect.signature(AsyncClient.__init__)
    print(f"  {sig}")

    # Check if there's a start_session method
    if hasattr(AsyncClient, 'start_session'):
        print("\n✅ AsyncClient.start_session exists")
        sig = inspect.signature(AsyncClient.start_session)
        print(f"   Signature: {sig}")

    # Try to instantiate it
    print("\n\nTrying to instantiate AsyncClient...")

    async def test():
        try:
            # Based on the methods available, it looks like we need to instantiate it differently
            client = AsyncClient(
                api_key="test_key",
                api_secret="test_secret",
                endpoint="https://app.architect.co",
                paper_trading=True
            )
            print("✅ AsyncClient instantiated")
            return client
        except TypeError as e:
            print(f"❌ TypeError: {e}")
            print("\nTrying without endpoint...")
            try:
                client = AsyncClient(
                    api_key="test_key",
                    api_secret="test_secret",
                    paper_trading=True
                )
                print("✅ AsyncClient instantiated without endpoint")
                return client
            except Exception as e2:
                print(f"❌ Still failed: {e2}")

                print("\nLet's check what parameters __init__ actually expects...")
                # Get the __init__ parameters
                params = inspect.signature(AsyncClient.__init__).parameters
                print("Parameters:")
                for name, param in params.items():
                    if name != 'self':
                        print(f"  - {name}: {param}")

    # Run test in async context
    asyncio.run(test())

    # Check if there's a documentation or example
    print("\n\nDocumentation:")
    if AsyncClient.__doc__:
        print(AsyncClient.__doc__)

    if hasattr(AsyncClient, '__init__') and AsyncClient.__init__.__doc__:
        print("\n__init__ documentation:")
        print(AsyncClient.__init__.__doc__)

except ImportError as e:
    print(f"Failed to import: {e}")