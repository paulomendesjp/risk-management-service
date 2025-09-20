package com.interview.challenge.kraken.util;

import org.springframework.stereotype.Component;

/**
 * Utility to detect if a symbol is for Spot or Futures market
 */
@Component
public class SymbolDetector {

    /**
     * Determine if a symbol is for Futures market
     *
     * Futures symbols start with:
     * - PF_ (Perpetual Futures)
     * - PI_ (Perpetual Inverse)
     * - FF_ (Fixed Futures)
     * - FI_ (Fixed Inverse)
     *
     * @param symbol The trading symbol
     * @return true if it's a futures symbol, false for spot
     */
    public boolean isFuturesSymbol(String symbol) {
        if (symbol == null) {
            return false;
        }

        String upperSymbol = symbol.toUpperCase();

        return upperSymbol.startsWith("PF_") ||  // Perpetual Futures
               upperSymbol.startsWith("PI_") ||  // Perpetual Inverse
               upperSymbol.startsWith("FF_") ||  // Fixed Futures
               upperSymbol.startsWith("FI_");    // Fixed Inverse
    }

    /**
     * Convert spot symbol to standard format
     * E.g., "BTC/USD" stays as "BTC/USD"
     */
    public String normalizeSpotSymbol(String symbol) {
        return symbol.toUpperCase();
    }

    /**
     * Get API type for a symbol
     */
    public String getApiType(String symbol) {
        return isFuturesSymbol(symbol) ? "futures" : "spot";
    }
}