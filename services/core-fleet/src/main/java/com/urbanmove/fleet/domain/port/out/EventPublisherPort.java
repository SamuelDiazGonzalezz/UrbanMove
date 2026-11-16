package com.urbanmove.fleet.domain.port.out;

import com.urbanmove.fleet.domain.event.ReservationCreatedEvent;
import com.urbanmove.fleet.domain.event.TripRecordedEvent;

public interface EventPublisherPort {
    void publish(ReservationCreatedEvent event);
    void publish(TripRecordedEvent event);
}
