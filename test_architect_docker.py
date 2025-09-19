#!/usr/bin/env python3
"""
Test script to run inside Docker to check architect-py
"""

print("Testing architect-py imports in Docker environment...")

try:
    print("\n1. Trying to import AsyncClient...")
    from architect_py.async_client import AsyncClient
    print("   ✅ Successfully imported AsyncClient")

    print("\n2. Checking if AsyncClient.connect exists...")
    if hasattr(AsyncClient, 'connect'):
        print("   ✅ AsyncClient.connect exists")

        import inspect
        sig = inspect.signature(AsyncClient.connect)
        print(f"   Signature: {sig}")

        # Check if it's a classmethod
        print(f"   Is classmethod: {isinstance(inspect.getattr_static(AsyncClient, 'connect'), classmethod)}")
    else:
        print("   ❌ AsyncClient.connect does NOT exist")
        print("   Available methods:")
        for attr in dir(AsyncClient):
            if not attr.startswith('_'):
                print(f"     - {attr}")

    print("\n3. Testing AsyncClient instantiation...")
    # Test the __init__ method
    init_sig = inspect.signature(AsyncClient.__init__)
    print(f"   __init__ signature: {init_sig}")

except ImportError as e:
    print(f"   ❌ Import error: {e}")

    print("\n   Trying alternative imports...")
    try:
        import architect_py
        print("   ✅ architect_py module imported")
        print("   Module attributes:")
        for attr in dir(architect_py):
            if 'Client' in attr:
                print(f"     - {attr}")
    except Exception as e2:
        print(f"   ❌ Failed: {e2}")

except Exception as e:
    print(f"   ❌ Error: {e}")
    import traceback
    traceback.print_exc()

print("\n" + "="*50)
print("Testing asyncio compatibility...")
print("="*50)

import asyncio

async def test_connect():
    """Test if we can call AsyncClient.connect"""
    try:
        from architect_py.async_client import AsyncClient

        print("\nTrying to call AsyncClient.connect with dummy credentials...")
        # We'll use invalid credentials just to test the method exists
        try:
            client = await AsyncClient.connect(
                endpoint="https://app.architect.co",
                api_key="test_key",
                api_secret="test_secret",
                paper_trading=True
            )
            print("   ✅ AsyncClient.connect() was called successfully (though may fail auth)")
        except AttributeError as ae:
            print(f"   ❌ AttributeError: {ae}")
        except Exception as e:
            # Any other error (like auth) means the method exists
            print(f"   ✅ Method exists but got expected error: {type(e).__name__}")

    except Exception as e:
        print(f"   ❌ Failed: {e}")

# Run the async test
try:
    asyncio.run(test_connect())
except Exception as e:
    print(f"Async test failed: {e}")

print("\nTest complete!")