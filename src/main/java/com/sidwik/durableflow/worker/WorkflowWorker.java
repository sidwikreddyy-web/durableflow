package com.sidwik.durableflow.worker;

import com.sidwik.durableflow.repository.WorkflowTaskRepository;
import com.sidwik.durableflow.service.WorkflowTaskProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "durableflow.worker.enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowWorker {
    private final WorkflowTaskRepository tasks;
    private final WorkflowTaskProcessor processor;
    private final Clock clock;
    private final int leaseSeconds;
    private final int batchSize;
    private final String workerId = "worker-" + UUID.randomUUID();

    public WorkflowWorker(WorkflowTaskRepository tasks, WorkflowTaskProcessor processor, Clock clock,
                          @Value("${durableflow.worker.lease-seconds:30}") int leaseSeconds,
                          @Value("${durableflow.worker.batch-size:10}") int batchSize) {
        this.tasks = tasks;
        this.processor = processor;
        this.clock = clock;
        this.leaseSeconds = leaseSeconds;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${durableflow.worker.poll-delay-ms:1000}")
    public void drain() {
        tasks.releaseExpiredLeases(clock.instant());
        for (int i = 0; i < batchSize; i++) {
            var task = tasks.claimNext(workerId, leaseSeconds);
            if (task.isEmpty()) return;
            processor.process(task.get(), workerId);
        }
    }
}
