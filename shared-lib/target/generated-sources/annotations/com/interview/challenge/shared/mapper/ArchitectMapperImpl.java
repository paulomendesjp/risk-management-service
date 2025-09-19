package com.interview.challenge.shared.mapper;

import com.interview.challenge.shared.dto.ArchitectOrderRequest;
import com.interview.challenge.shared.model.OrderData;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2025-09-19T16:49:56-0300",
    comments = "version: 1.5.5.Final, compiler: Eclipse JDT (IDE) 3.43.0.v20250819-1513, environment: Java 21.0.8 (Eclipse Adoptium)"
)
@Component
public class ArchitectMapperImpl implements ArchitectMapper {

    @Override
    public ArchitectOrderRequest toArchitectOrderRequest(OrderData orderData) {
        if ( orderData == null ) {
            return null;
        }

        ArchitectOrderRequest architectOrderRequest = new ArchitectOrderRequest();

        architectOrderRequest.setClientId( orderData.getClientId() );
        architectOrderRequest.setSymbol( orderData.getSymbol() );
        architectOrderRequest.setSide( orderData.getAction() );
        architectOrderRequest.setQuantity( orderData.getOrderQty() );
        architectOrderRequest.setType( orderData.getOrderType() );
        architectOrderRequest.setPrice( orderData.getLimitPrice() );

        architectOrderRequest.setTimeInForce( "GTC" );

        return architectOrderRequest;
    }
}
