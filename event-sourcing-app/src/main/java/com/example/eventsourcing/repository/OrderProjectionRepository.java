package com.example.eventsourcing.repository;

import com.example.eventsourcing.projection.OrderProjection;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional(propagation = Propagation.MANDATORY)
public interface OrderProjectionRepository extends JpaRepository<OrderProjection, UUID> {
}

