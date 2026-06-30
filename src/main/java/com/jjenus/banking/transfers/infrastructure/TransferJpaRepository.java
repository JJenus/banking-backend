package com.jjenus.banking.transfers.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

interface TransferJpaRepository extends JpaRepository<TransferJpaEntity, String> {

    List<TransferJpaEntity> findByFromAccountIdOrderByCreatedAtDesc(String fromAccountId);

    List<TransferJpaEntity> findByToAccountIdOrderByCreatedAtDesc(String toAccountId);

    @Query("""
        SELECT t FROM TransferJpaEntity t
        WHERE (t.fromAccountId = :accountId OR t.toAccountId = :accountId)
        ORDER BY t.createdAt DESC
        """)
    List<TransferJpaEntity> findByAccountId(@Param("accountId") String accountId);

    @Query("""
        SELECT t FROM TransferJpaEntity t
        WHERE (t.fromAccountId = :accountId OR t.toAccountId = :accountId)
          AND t.createdAt BETWEEN :from AND :to
        ORDER BY t.createdAt DESC
        """)
    List<TransferJpaEntity> findByAccountIdAndDateRange(@Param("accountId") String accountId,
                                                        @Param("from") Instant from,
                                                        @Param("to") Instant to);

    List<TransferJpaEntity> findByStatusOrderByCreatedAtDesc(String status);

    boolean existsByReference(String reference);

    @Query("""
        SELECT t FROM TransferJpaEntity t
        WHERE (t.fromAccountId = :accountId OR t.toAccountId = :accountId)
        ORDER BY t.createdAt DESC
        """)
    Page<TransferJpaEntity> findByAccountIdPaged(@Param("accountId") String accountId, Pageable pageable);
}
