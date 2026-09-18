package com.yaret.contigo.users;

import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface UserRepository extends JpaRepository<User, Long> {
  boolean existsByManageUsersTrue();

  Optional<User> findByDni(String dni);
}
