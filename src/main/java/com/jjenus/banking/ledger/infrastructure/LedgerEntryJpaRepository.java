package com.jjenus.banking.ledger.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link LedgerEntryJpaEntity}.
 * Internal to the ledger module — do not use outside this package.
 */
interface LedgerEntryJpaRepository extends JpaRepository<LedgerEntryJpaEntity, String> {

    @Query("""
        SELECT e FROM LedgerEntryJpaEntity e
        WHERE e.debitAccountId = :accountId OR e.creditAccountId = :accountId
        ORDER BY e.postedAt ASC
        """)
    List<LedgerEntryJpaEntity> findByAccountId(@Param("accountId") String accountId);

    @Query("""
        SELECT e FROM LedgerEntryJpaEntity e
        WHERE (e.debitAccountId = :accountId OR e.creditAccountId = :accountId)
          AND e.postedAt <= :asOf
        ORDER BY e.postedAt ASC
        """)
    List<LedgerEntryJpaEntity> findByAccountIdAsOf(@Param("accountId") String accountId,
                                                    @Param("asOf") Instant asOf);

    List<LedgerEntryJpaEntity> findByReference(String reference);

    @Query("""
        SELECT e FROM LedgerEntryJpaEntity e
        WHERE e.currency = :currency
          AND e.postedAt <= :asOf
        ORDER BY e.postedAt ASC
        """)
    List<LedgerEntryJpaEntity> findAllByCurrencyAndPostedAtBefore(
        @Param("currency") String currency,
        @Param("asOf") Instant asOf);

    @Query("""
        SELECT e FROM LedgerEntryJpaEntity e
        WHERE (e.debitAccountId = :accountId OR e.creditAccountId = :accountId)
          AND e.postedAt BETWEEN :from AND :to
        ORDER BY e.postedAt ASC
        """)
    List<LedgerEntryJpaEntity> findByAccountIdAndPostedAtBetween(
        @Param("accountId") String accountId,
        @Param("from") Instant from,
        @Param("to") Instant to);

    @Query("""
        SELECT COALESCE(SUM(
            CASE WHEN e.creditAccountId = :accountId THEN e.amount
                 WHEN e.debitAccountId  = :accountId THEN -e.amount
                 ELSE 0
            END
        ), 0)
        FROM LedgerEntryJpaEntity e
        WHERE (e.debitAccountId = :accountId OR e.creditAccountId = :accountId)
          AND e.currency = :currency
        """)
    java.math.BigDecimal computeBalance(@Param("accountId") String accountId,
                                        @Param("currency") String currency);

    @Query("""
        SELECT COALESCE(SUM(
            CASE WHEN e.creditAccountId = :accountId THEN e.amount
                 WHEN e.debitAccountId  = :accountId THEN -e.amount
                 ELSE 0
            END
        ), 0)
        FROM LedgerEntryJpaEntity e
        WHERE (e.debitAccountId = :accountId OR e.creditAccountId = :accountId)
          AND e.currency = :currency
          AND e.postedAt <= :asOf
        """)
    java.math.BigDecimal computeBalanceAsOf(@Param("accountId") String accountId,
                                            @Param("currency") String currency,
                                            @Param("asOf") Instant asOf);

    @Query("""
        SELECT e.creditAccountId AS accountId,
               COALESCE(SUM(e.amount), 0) AS total
        FROM LedgerEntryJpaEntity e
        WHERE e.currency = :currency
        GROUP BY e.creditAccountId
        """)
    List<Object[]> sumCreditsByAccount(@Param("currency") String currency);

    @Query("""
        SELECT e.debitAccountId AS accountId,
               COALESCE(SUM(e.amount), 0) AS total
        FROM LedgerEntryJpaEntity e
        WHERE e.currency = :currency
        GROUP BY e.debitAccountId
        """)
    List<Object[]> sumDebitsByAccount(@Param("currency") String currency);
}
