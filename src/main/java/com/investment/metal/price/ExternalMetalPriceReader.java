package com.investment.metal.price;

import com.investment.metal.common.CurrencyType;
import com.investment.metal.common.MetalType;

public interface ExternalMetalPriceReader {

    /**
     * @param metalType type of metal
     * @return Returns the price of one kg of metal
     */
    double fetchPrice(MetalType metalType);

    /**
     * @return Returns the currency type of the metal price
     */
    CurrencyType getCurrencyType();
}
