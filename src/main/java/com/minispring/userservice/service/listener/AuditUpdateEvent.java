package com.minispring.userservice.service.listener;

import java.util.UUID;

public record AuditUpdateEvent(
        UUID id,
        Object stateBefore,
        Object stateAfter
) {
}
