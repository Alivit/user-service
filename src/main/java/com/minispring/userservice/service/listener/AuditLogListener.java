package com.minispring.userservice.service.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javers.core.Javers;
import org.javers.core.diff.Diff;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogListener {

    private final Javers javers;

    @Value("${app.audit.enabled}")
    private boolean auditEnabled;

    @Async
    @EventListener
    public void handleAuditUpdate(AuditUpdateEvent event) {
        Diff diff = javers.compare(event.stateBefore(), event.stateAfter());

        if (diff.hasChanges() && auditEnabled) {
            log.info("Logging update for ID [{}]. Changes:\n{}", event.id(), diff.prettyPrint());
        }
    }
}
