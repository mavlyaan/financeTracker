package org.example.statistics;

import java.math.BigDecimal;
import java.time.LocalDate;

public class DailyStatistics {
    private LocalDate date;

    private BigDecimal totalExpenses;

    public DailyStatistics() {
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public BigDecimal getTotalExpenses() {
        return totalExpenses;
    }

    public void setTotalExpenses(BigDecimal totalExpenses) {
        this.totalExpenses = totalExpenses;
    }
}
