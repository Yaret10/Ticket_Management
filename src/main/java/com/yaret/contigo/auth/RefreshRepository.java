package com.yaret.contigo.auth;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RefreshRepository extends JpaRepository<RefreshToken, Long> {
  @Query("select t.family from RefreshToken t where t.tokenHash=:hash")
  Optional<String> familyOfHash(@Param("hash") String hash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from RefreshToken t where t.tokenHash=:hash")
  Optional<RefreshToken> lock(@Param("hash") String hash);

  @Modifying
  @Query("update RefreshToken t set t.revoked=true where t.family=:family")
  int revokeFamily(@Param("family") String family);
}
