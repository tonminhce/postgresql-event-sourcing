package com.example.eventsourcing.service.projection;

import com.example.eventsourcing.domain.AggregateType;
import com.example.eventsourcing.domain.OrderAggregate;
import com.example.eventsourcing.mapper.OrderMapper;
import com.example.eventsourcing.projection.OrderProjection;
import eventsourcing.postgresql.repository.AggregateRepository;
import eventsourcing.postgresql.service.AggregateStore;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderProjectionRebuildService {

    private final AggregateStore aggregateStore;
    private final AggregateRepository aggregateRepository;
    private final OrderMapper orderMapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public OrderProjection rebuildOrder(UUID orderId) {
        log.info("Rebuilding projection for order {}", orderId);
        OrderAggregate aggregate = (OrderAggregate) aggregateStore.readAggregate(AggregateType.ORDER.name(), orderId);
        OrderProjection projection = orderMapper.toProjection(aggregate);
        OrderProjection merged = entityManager.merge(projection);
        entityManager.flush();
        return merged;
    }

    @Transactional
    public int rebuildAllOrders() {
        log.info("Starting complete rebuild of Order projections...");
        List<UUID> orderIds = aggregateRepository.findAggregateIdsByType(AggregateType.ORDER.name());
        int batchSize = 50;
        for (int i = 0; i < orderIds.size(); i++) {
            UUID orderId = orderIds.get(i);
            OrderAggregate aggregate = (OrderAggregate) aggregateStore.readAggregate(AggregateType.ORDER.name(), orderId);
            OrderProjection projection = orderMapper.toProjection(aggregate);
            entityManager.merge(projection);
            if ((i + 1) % batchSize == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }
        entityManager.flush();
        entityManager.clear();
        log.info("Successfully rebuilt {} Order projection(s).", orderIds.size());
        return orderIds.size();
    }
}

