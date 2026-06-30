package com.jjenus.banking.identity.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface UserProfileJpaRepository extends JpaRepository<UserProfileJpaEntity, String> {

    Optional<UserProfileJpaEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    List<UserProfileJpaEntity> findByKycStatus(String kycStatus);

    @Query("SELECT u.email FROM UserProfileJpaEntity u WHERE u.id = :id")
    Optional<String> findEmailById(@Param("id") String id);
}
