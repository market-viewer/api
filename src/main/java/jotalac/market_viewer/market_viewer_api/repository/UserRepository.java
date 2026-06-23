package jotalac.market_viewer.market_viewer_api.repository;

import jotalac.market_viewer.market_viewer_api.entity.OAuthProvider;
import jotalac.market_viewer.market_viewer_api.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    Optional<User> findByOauthProviderAndOauthProviderId(OAuthProvider oauthProvider, String oauthProviderId);

    @Query("SELECT u.tokenVersion FROM User u WHERE u.id = :userId")
    Integer findTokenVersionById(@Param("userId") Integer userId);

    @Transactional
    @Modifying
    @Query("UPDATE User u SET u.tokenVersion = u.tokenVersion + 1 WHERE u.id = :userId")
    void increaseTokenVersion(@Param("userId") Integer userId);
}
