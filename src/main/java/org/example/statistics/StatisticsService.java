package org.example.statistics;

import org.example.budget.Budget;
import org.example.budget.BudgetRepository;
import org.example.expense.Expense;
import org.example.expense.ExpenseRepository;
import org.springframework.http.HttpStatus;
import org.example.auth.AuthService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@Transactional(readOnly = true)
public class StatisticsService {
    private final AuthService authService;
    private final BudgetRepository budgetRepository;
    private final ExpenseRepository expenseRepository;

    public StatisticsService(BudgetRepository budgetRepository, ExpenseRepository expenseRepository, AuthService authService) {
        this.authService = authService;
        this.budgetRepository = budgetRepository;
        this.expenseRepository = expenseRepository;
    }

    public OverviewResponse overview(Long budgetId) {
        BigDecimal totalBudget = BigDecimal.ZERO;
        if (budgetId != null) {
            totalBudget = findBudget(budgetId).getAmount();
        } else {
            for (Budget budget : budgetRepository.findByUserId(authService.currentUser().getId())) {
                totalBudget = totalBudget.add(budget.getAmount());
            }
        }
        BigDecimal totalExpenses = BigDecimal.ZERO;
        for (Expense expense : findExpenses(budgetId)) {
            totalExpenses = totalExpenses.add(expense.getAmount());
        }
        BigDecimal percentage = BigDecimal.ZERO;
        if (totalBudget.compareTo(BigDecimal.ZERO) > 0) {
            percentage = totalExpenses.multiply(new BigDecimal("100"))
                    .divide(totalBudget, 2, RoundingMode.HALF_UP);
        }
        OverviewResponse response = new OverviewResponse();
        response.setTotalBudget(totalBudget);
        response.setTotalExpenses(totalExpenses);
        response.setRemainingBudget(totalBudget.subtract(totalExpenses));
        response.setUsagePercentage(percentage);
        return response;
    }

    public List<CategoryStatistics> byCategory(Long budgetId) {
        Map<Long, CategoryStatistics> totals = new LinkedHashMap<>();
        for (Expense expense : findExpenses(budgetId)) {
            Long categoryId = expense.getCategory().getId();
            CategoryStatistics statistics = totals.get(categoryId);
            if (statistics == null) {
                statistics = new CategoryStatistics();
                statistics.setCategoryId(categoryId);
                statistics.setCategoryName(expense.getCategory().getName());
                statistics.setColor(expense.getCategory().getColor());
                statistics.setTotalExpenses(BigDecimal.ZERO);
                totals.put(categoryId, statistics);
            }
            statistics.setTotalExpenses(statistics.getTotalExpenses().add(expense.getAmount()));
        }
        return new ArrayList<>(totals.values());
    }

    public List<DailyStatistics> daily(Long budgetId) {
        Map<LocalDate, BigDecimal> totals = new TreeMap<>();
        for (Expense expense : findExpenses(budgetId)) {
            BigDecimal total = totals.get(expense.getDate());
            if (total == null) {
                total = BigDecimal.ZERO;
            }
            totals.put(expense.getDate(), total.add(expense.getAmount()));
        }
        List<DailyStatistics> result = new ArrayList<>();
        for (Map.Entry<LocalDate, BigDecimal> entry : totals.entrySet()) {
            DailyStatistics statistics = new DailyStatistics();
            statistics.setDate(entry.getKey());
            statistics.setTotalExpenses(entry.getValue());
            result.add(statistics);
        }
        return result;
    }

    private Budget findBudget(Long id) {
        return budgetRepository.findByIdAndUserId(id, authService.currentUser().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget not found"));
    }

    private List<Expense> findExpenses(Long budgetId) {
        if (budgetId != null) {
            findBudget(budgetId);
            return expenseRepository.findByBudgetIdAndUserId(budgetId, authService.currentUser().getId());
        }
        return expenseRepository.findByUserId(authService.currentUser().getId());
    }
}
