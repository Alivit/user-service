package com.minispring.userservice.service.listener;

import com.minispring.userservice.config.AuditConfig;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PreUpdateEvent;
import org.hibernate.event.spi.PreUpdateEventListener;
import org.hibernate.internal.SessionFactoryImpl;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HibernateAuditListener implements PreUpdateEventListener {

    private final EntityManagerFactory entityManagerFactory;
    private final AuditConfig auditConfig;

    @PostConstruct
    public void registerListeners() {
        SessionFactoryImpl sessionFactory = entityManagerFactory.unwrap(SessionFactoryImpl.class);
        EventListenerRegistry registry = sessionFactory.getServiceRegistry().getService(EventListenerRegistry.class);
        registry.getEventListenerGroup(EventType.PRE_UPDATE).appendListener(this);
    }

    @Override
    public boolean onPreUpdate(PreUpdateEvent preUpdateEvent) {
        if (!auditConfig.isEnabled()) {
            return false;
        }

        String[] propertyNames = preUpdateEvent.getPersister().getPropertyNames();
        Object[] oldState = preUpdateEvent.getOldState();
        Object[] newState = preUpdateEvent.getState();

        StringBuilder diff = new StringBuilder();
        boolean hasChanges = false;

        for (int i = 0; i < propertyNames.length; i++) {
            if ("updatedAt".equals(propertyNames[i]) || "version".equals(propertyNames[i])) {
                continue;
            }

            if (!Objects.equals(oldState[i], newState[i])) {
                diff.append(propertyNames[i])
                        .append(" {")
                        .append(oldState[i] == null ? "null" : oldState[i])
                        .append(" -> ")
                        .append(newState[i] == null ? "null" : newState[i])
                        .append("} ");
                hasChanges = true;
            }
        }

        if (hasChanges) {
            log.info(
                    "AUDIT LOG [{}] ID={} | Changes: {}",
                    preUpdateEvent.getEntity().getClass().getSimpleName(),
                    preUpdateEvent.getId(),
                    diff.toString().trim());
        }

        return false;
    }
}
