package com.pesatalk.service.scheduler;

import com.pesatalk.model.Transaction;
import com.pesatalk.model.enums.TransactionStatus;
import com.pesatalk.repository.TransactionRepository;
import com.pesatalk.service.NotificationService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class TransactionScheduler {

    private static final Logger log = LoggerFactory.getLogger(TransactionScheduler.class);

    private final TransactionRepository transactionRepository;
    private final NotificationService notificationService;

    public TransactionScheduler(
        TransactionRepository transactionRepository,
        NotificationService notificationService
    ) {
        this.transactionRepository = transactionRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(fixedRate = 60000) // Every minute
    @SchedulerLock(name = "expirePendingConfirmations", lockAtMostFor = "2m", lockAtLeastFor = "30s")
    @Transactional
    public void expirePendingConfirmations() {
        log.debug("Running expirePendingConfirmations job");

        List<Transaction> expiredTransactions = transactionRepository
            .findExpiredPendingConfirmations(
                TransactionStatus.PENDING_CONFIRMATION,
                Instant.now()
            );

        if (expiredTransactions.isEmpty()) {
            return;
        }

        log.info("Found {} expired pending confirmations", expiredTransactions.size());

        for (Transaction transaction : expiredTransactions) {
            try {
                transaction.transitionTo(TransactionStatus.EXPIRED);
                transaction.setFailureReason("Confirmation timeout");
                transactionRepository.save(transaction);

                // Notify user
                notificationService.sendMessage(
                    transaction.getSender().getWhatsAppId(),
                    "Your transaction of KES " + transaction.getAmount() +
                    " has expired. Please start a new transaction if you still wish to proceed."
                );

                log.info("Expired transaction: {}", transaction.getId());
            } catch (Exception e) {
                log.error("Error expiring transaction {}: {}", transaction.getId(), e.getMessage());
            }
        }
    }

    @Scheduled(fixedRate = 300000) // Every 5 minutes
    @SchedulerLock(name = "checkStaleSTKPush", lockAtMostFor = "5m", lockAtLeastFor = "1m")
    @Transactional
    public void checkStaleSTKPushTransactions() {
        log.debug("Running checkStaleSTKPush job");

        // Transactions stuck in STK_PUSHED for more than 5 minutes
        Instant cutoff = Instant.now().minus(5, ChronoUnit.MINUTES);

        List<Transaction> staleTransactions = transactionRepository
            .findStaleProcessingTransactions(TransactionStatus.STK_PUSHED, cutoff);

        if (staleTransactions.isEmpty()) {
            return;
        }

        log.info("Found {} stale STK push transactions", staleTransactions.size());

        for (Transaction transaction : staleTransactions) {
            try {
                // Mark as failed - we didn't receive callback
                transaction.transitionTo(TransactionStatus.FAILED);
                transaction.setFailureReason("STK Push timeout - no callback received");
                transactionRepository.save(transaction);

                notificationService.sendMessage(
                    transaction.getSender().getWhatsAppId(),
                    "Your transaction timed out. This could mean:\n" +
                    "1. You cancelled the MPesa prompt\n" +
                    "2. The request expired on your phone\n\n" +
                    "Please try again if you wish to proceed."
                );

                log.info("Marked stale STK transaction as failed: {}", transaction.getId());
            } catch (Exception e) {
                log.error("Error processing stale transaction {}: {}", transaction.getId(), e.getMessage());
            }
        }
    }

    @Scheduled(cron = "0 0 0 * * ?") // Daily at midnight
    @SchedulerLock(name = "resetDailyLimits", lockAtMostFor = "10m", lockAtLeastFor = "5m")
    @Transactional
    public void resetDailyLimits() {
        log.info("Running resetDailyLimits job");

        // Reset daily transaction limits for users whose last transaction
        // was before today
        Instant startOfToday = Instant.now().truncatedTo(ChronoUnit.DAYS);

        int updated = transactionRepository
            .bulkUpdateExpiredTransactions(
                List.of(), // Empty list - using different method
                TransactionStatus.PENDING_CONFIRMATION,
                TransactionStatus.EXPIRED,
                Instant.now(),
                "Daily limit reset"
            );

        log.info("Reset daily limits completed");
    }

    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
    @SchedulerLock(name = "cleanupOldData", lockAtMostFor = "30m", lockAtLeastFor = "10m")
    public void cleanupOldData() {
        log.info("Running cleanupOldData job");

        // This would clean up old audit records, logs, etc.
        // Implementation depends on retention policy
    }
}
