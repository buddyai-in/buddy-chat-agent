package com.buddyai.agent.repository;

import com.buddyai.agent.entity.Slot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SlotRepository extends JpaRepository<Slot, Long> {

    List<Slot> findByRequestId(String requestId);

    void deleteByRequestId(String requestId);
}
