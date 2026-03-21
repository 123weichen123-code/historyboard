package com.historyboard.dto;

import java.util.ArrayList;
import java.util.List;

public class TimelineResponse {
    private int count;
    private List<EventView> events = new ArrayList<>();

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public List<EventView> getEvents() {
        return events;
    }

    public void setEvents(List<EventView> events) {
        this.events = events == null ? new ArrayList<>() : events;
    }
}
