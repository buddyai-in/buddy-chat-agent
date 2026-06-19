package com.buddyai.agent.repository;

import com.buddyai.agent.entity.ClientAuth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Data access layer for {@link ClientAuth} entities.
 */
@Repository
public interface ClientAuthRepository extends JpaRepository<ClientAuth, Long> {

    /**
     * Find an active (or inactive) client by its identifier and enabled flag.
     * Used by {@code AuthInterceptor} to validate Bearer tokens.
     *
     * @param clientId the tenant identifier extracted from the request path
     * @param enabled  {@code true} to restrict to active clients only
     * @return the matching record, or empty if none exists
     */
    Optional<ClientAuth> findByClientIdAndEnabled(String clientId, boolean enabled);

    /**
     * Find a client regardless of its enabled state.
     * Useful for admin operations (inspect / toggle a disabled client).
     *
     * @param clientId the tenant identifier
     * @return the matching record, or empty if none exists
     */
    Optional<ClientAuth> findByClientId(String clientId);
}
