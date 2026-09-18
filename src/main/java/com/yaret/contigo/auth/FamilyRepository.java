package com.yaret.contigo.auth;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface FamilyRepository extends JpaRepository<RefreshFamily, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select f from RefreshFamily f where f.id=:id")
  Optional<RefreshFamily> lock(@Param("id") String id);
}
