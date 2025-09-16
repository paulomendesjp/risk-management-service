package com.interview.challenge.user.service;

import com.interview.challenge.shared.event.UserRegistrationEvent;
import com.interview.challenge.shared.model.ClientConfiguration;
import com.interview.challenge.shared.model.RiskLimit;
import com.interview.challenge.user.constants.UserConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class EventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(EventPublisher.class);

    @Autowired(required = false)
    @Qualifier("rabbitTemplate")
    private RabbitTemplate rabbitTemplate;

    /**
     * Publish user registration event
     */
    public void publishUserRegistrationEvent(ClientConfiguration user) {
        try {
            if (!isRabbitMQConfigured()) {
                logger.warn(UserConstants.LOG_RABBITMQ_NOT_CONFIGURED);
                return;
            }

            UserRegistrationEvent event = createRegistrationEvent(user);
            rabbitTemplate.convertAndSend(UserConstants.EXCHANGE_USER_REGISTRATIONS, event);

            logger.info(UserConstants.LOG_EVENT_PUBLISHED, "user registration", user.getClientId());

        } catch (Exception e) {
            logger.error(UserConstants.LOG_EVENT_PUBLISH_FAILED, "user registration", e.getMessage(), e);
        }
    }

    /**
     * Publish risk limit update event
     */
    public void publishRiskLimitUpdateEvent(ClientConfiguration user) {
        try {
            if (!isRabbitMQConfigured()) {
                return;
            }

            UserRegistrationEvent event = createUpdateEvent(user);
            rabbitTemplate.convertAndSend(UserConstants.EXCHANGE_USER_UPDATES, event);

            logger.info(UserConstants.LOG_EVENT_PUBLISHED, "risk limit update", user.getClientId());

        } catch (Exception e) {
            logger.error(UserConstants.LOG_EVENT_PUBLISH_FAILED, "risk limit update", e.getMessage());
        }
    }

    /**
     * Create registration event
     */
    private UserRegistrationEvent createRegistrationEvent(ClientConfiguration user) {
        UserRegistrationEvent event = UserRegistrationEvent.registration(
            user.getClientId(),
            user.getInitialBalance()
        );

        setRiskLimitsOnEvent(event, user.getMaxRisk(), user.getDailyRisk());
        return event;
    }

    /**
     * Create update event
     */
    private UserRegistrationEvent createUpdateEvent(ClientConfiguration user) {
        UserRegistrationEvent event = UserRegistrationEvent.update(user.getClientId());
        setRiskLimitsOnEvent(event, user.getMaxRisk(), user.getDailyRisk());
        return event;
    }

    /**
     * Set risk limits on event
     */
    private void setRiskLimitsOnEvent(UserRegistrationEvent event, RiskLimit maxRisk, RiskLimit dailyRisk) {
        if (maxRisk != null) {
            event.setMaxRiskValue(maxRisk.getValue());
            event.setMaxRiskType(maxRisk.getType());
        }
        if (dailyRisk != null) {
            event.setDailyRiskValue(dailyRisk.getValue());
            event.setDailyRiskType(dailyRisk.getType());
        }
    }

    /**
     * Check if RabbitMQ is configured
     */
    private boolean isRabbitMQConfigured() {
        return rabbitTemplate != null;
    }
}