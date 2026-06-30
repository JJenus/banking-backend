package com.jjenus.banking.transactions.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

interface TransactionJpaRepository extends JpaRepository<TransactionJpaEntity, String> {

    @Query("""
        SELECT t FROM TransactionJpaEntity t
        WHERE t.accountId = :accountId
        ORDER BY t.timestamp DESC
        """)
    Page<TransactionJpaEntity> findByAccountIdPaged(
        @Param("accountId") String accountId, Pageable pageable);

    @Query("""
        SELECT t FROM TransactionJpaEntity t
        WHERE t.accountId = :accountId
        ORDER BY t.timestamp DESC
        """)
    List<TransactionJpaEntity> findByAccountId(@Param("accountId") String accountId);

    @Query("""
        SELECT t FROM TransactionJpaEntity t
        WHERE t.accountId = :accountId
          AND t.timestamp BETWEEN :from AND :to
        ORDER BY t.timestamp ASC
        """)
    List<TransactionJpaEntity> findByAccountIdAndTimestampBetween(
        @Param("accountId") String accountId,
        @Param("from") Instant from,
        @Param("to") Instant to);

    List<TransactionJpaEntity> findByReference(String reference);
}
