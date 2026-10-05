package com.swgoh.admin.backend.service;

import com.swgoh.admin.backend.model.Guild;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Holds the most recently fetched guild in memory so /api/optimize can run
 * against it without re-pulling every member's roster. Single-guild, not
 * thread-safe-for-concurrent-guilds by design -- this is a test tool, not
 * a multi-tenant service. Replace with a real cache (Postgres, Redis) if
 * this grows beyond one officer testing at a time.
 */
@Service
public class GuildCacheService {

    private volatile Guild current;

    public void setCurrent(Guild guild) {
        this.current = guild;
    }

    public Optional<Guild> getCurrent() {
        return Optional.ofNullable(current);
    }
}
