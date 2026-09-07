package com.billbuddy.backend.features.expenses.schedular;

import com.billbuddy.backend.features.expenses.service.RecurringExpenseService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RecurringExpenseScheduler {

    private final RecurringExpenseService recurringExpenseService;

    public RecurringExpenseScheduler(RecurringExpenseService recurringExpenseService) {
        this.recurringExpenseService = recurringExpenseService;
    }

    // Run once a day at 1am -- day-granularity recurrences (weekly/monthly) don't need finer
    // resolution, and a daily sweep naturally catches any date the app was down for, comparing
    // against CURRENT_DATE rather than retrying on a fixed schedule of its own.
    @Scheduled(cron = "0 0 1 * * ?")
    public void generateDueExpenses() {
        recurringExpenseService.runDueGenerations();
    }
}
