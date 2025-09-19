#!/usr/bin/env python3
"""
Test architect-py imports to understand the correct API
"""

import sys
import inspect

try:
    # Try importing AsyncClient
    from architect_py.async_client import AsyncClient
    print("✅ Successfully imported AsyncClient")

    # List all methods of AsyncClient
    print("\n📋 AsyncClient methods and attributes:")
    for name, method in inspect.getmembers(AsyncClient):
        if not name.startswith('_'):
            print(f"  - {name}")

    # Check if connect exists
    if hasattr(AsyncClient, 'connect'):
        print("\n✅ AsyncClient.connect exists")
        sig = inspect.signature(AsyncClient.connect)
        print(f"   Signature: {sig}")
    else:
        print("\n❌ AsyncClient.connect does NOT exist")

    # Check if it's a regular class that needs instantiation
    print("\n🔍 Checking AsyncClient class info:")
    print(f"   Is class: {inspect.isclass(AsyncClient)}")

    # Try to check __init__ signature
    if hasattr(AsyncClient, '__init__'):
        init_sig = inspect.signature(AsyncClient.__init__)
        print(f"   __init__ signature: {init_sig}")

except ImportError as e:
    print(f"❌ Failed to import AsyncClient: {e}")

except Exception as e:
    print(f"❌ Error: {e}")
    import traceback
    traceback.print_exc()

print("\n" + "="*50)
print("Testing alternative import methods...")
print("="*50)

try:
    import architect_py
    print(f"\n📦 architect_py module attributes:")
    for attr in dir(architect_py):
        if not attr.startswith('_'):
            print(f"  - {attr}")

except Exception as e:
    print(f"❌ Error importing architect_py: {e}")