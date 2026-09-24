package com.example.wgo.event;

import com.example.wgo.observation.Observation;
import java.util.List;

public interface EventSummarizer {
    EventSummaryResponse summarize(List<Observation> observations);
}
