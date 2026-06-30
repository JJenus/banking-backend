package com.jjenus.banking.accounts.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link AccountJpaEntity}.
 * Internal to the accounts module — do not use outside this package.
 */
interface AccountJpaRepository extends JpaRepository<AccountJpaEntity, String> {

    List<AccountJpaEntity> findByOwnerId(String ownerId);

    @Query("SELECT a FROM AccountJpaEntity a WHERE a.status = :status")
    List<AccountJpaEntity> findByStatus(@Param("status") String status);

    boolean existsByIdAndOwnerId(String id, String ownerId);

    @Query("SELECT a.ownerName FROM AccountJpaEntity a WHERE a.id = :accountId")
    Optional<String> findOwnerName(@Param("accountId") String accountId);

    @Modifying
    @Transactional
    @Query("UPDATE AccountJpaEntity a SET a.ownerName = :ownerName WHERE a.id = :accountId")
    void updateOwnerName(@Param("accountId") String accountId, @Param("ownerName") String ownerName);
}
