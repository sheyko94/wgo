package com.example.wgo.event;

@FunctionalInterface
public interface EventCategorizer {
    EventCategory categorize(String report);
}
