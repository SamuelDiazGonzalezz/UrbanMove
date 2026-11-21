package com.urbanmove.fleet.domain.port.out;

import com.urbanmove.fleet.domain.event.ReservationCancelledEvent;
import com.urbanmove.fleet.domain.event.ReservationCreatedEvent;
import com.urbanmove.fleet.domain.event.ReservationExpiredEvent;
import com.urbanmove.fleet.domain.event.TripRecordedEvent;

public interface EventPublisherPort {
    void publish(ReservationCreatedEvent event);
    void publish(ReservationCancelledEvent event);
    void publish(ReservationExpiredEvent event);
    void publish(TripRecordedEvent event);
}
