package com.nextai.document.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Development adapter. Replace with authenticated JWT/session principal before production.
 */
@Component
public class OwnerIdResolver {
    private final String demoOwnerId;

    public OwnerIdResolver(@Value("${app.security.demo-owner-id}") String demoOwnerId) {
        this.demoOwnerId = demoOwnerId;
    }

    public String resolve(String ownerIdHeader) {
        String ownerId = StringUtils.hasText(ownerIdHeader) ? ownerIdHeader.trim() : demoOwnerId;
        if (ownerId.length() > 128) {
            throw new IllegalArgumentException("X-Owner-Id must be at most 128 characters");
        }
        return ownerId;
    }
}
