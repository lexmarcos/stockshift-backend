package br.com.stockshift.service;

/**
 * Service for managing JWT token denylist.
 * Used to invalidate access tokens on logout.
 */
public interface TokenDenylistService {

    /**
     * Add a token to the denylist.
     *
     * @param jti JWT ID to blacklist
     * @param ttlMillis time-to-live in milliseconds (should match token remaining lifetime)
     */
    void addToDenylist(String jti, long ttlMillis);

    /**
     * Check if a token is in the denylist.
     *
     * @param jti JWT ID to check
     * @return true if token is denylisted (revoked), false otherwise
     */
    boolean isDenylisted(String jti);
}
