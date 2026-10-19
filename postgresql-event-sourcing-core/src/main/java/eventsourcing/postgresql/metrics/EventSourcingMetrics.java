package eventsourcing.postgresql.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class EventSourcingMetrics {

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, AtomicLong> subscriptionLags = new ConcurrentHashMap<>();

    public EventSourcingMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistry = meterRegistryProvider.getIfAvailable();
    }

    public void recordOccConflict(String aggregateType) {
        if (meterRegistry != null) {
            meterRegistry.counter("event_sourcing_occ_conflicts_total", "aggregate_type", aggregateType).increment();
        }
    }

    public void recordDlqEvent(String subscriptionName, String aggregateType) {
        if (meterRegistry != null) {
            meterRegistry.counter("event_sourcing_dlq_events_total",
                    "subscription", subscriptionName,
                    "aggregate_type", aggregateType
            ).increment();
        }
    }

    public void recordAppendDuration(String aggregateType, long durationNanos) {
        if (meterRegistry != null) {
            meterRegistry.timer("event_sourcing_append_duration_seconds", "aggregate_type", aggregateType)
                    .record(durationNanos, TimeUnit.NANOSECONDS);
        }
    }

    public void recordHandlerDuration(String subscriptionName, String aggregateType, long durationNanos) {
        if (meterRegistry != null) {
            meterRegistry.timer("event_sourcing_handler_duration_seconds",
                    "subscription", subscriptionName,
                    "aggregate_type", aggregateType
            ).record(durationNanos, TimeUnit.NANOSECONDS);
        }
    }

    public void recordSubscriptionLag(String subscriptionName, String aggregateType, long lag) {
        String key = subscriptionName + ":" + aggregateType;
        AtomicLong val = subscriptionLags.computeIfAbsent(key, k -> {
            AtomicLong a = new AtomicLong(lag);
            if (meterRegistry != null) {
                meterRegistry.gauge("event_sourcing_subscription_lag",
                        List.of(
                                Tag.of("subscription", subscriptionName),
                                Tag.of("aggregate_type", aggregateType)
                        ),
                        a,
                        AtomicLong::get
                );
            }
            return a;
        });
        val.set(lag);
    }
}

