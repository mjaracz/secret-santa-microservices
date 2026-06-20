package com.secretsanta.user.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.secretsanta.user.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

  boolean existsByEmailNormalized(String emailNormalized);

  Optional<User> findByEmailNormalized(String emailNormalized);
}
