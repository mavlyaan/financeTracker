package org.example.statistics;

import org.example.budget.Budget;
import org.example.budget.BudgetRepository;
import org.example.category.Category;
import org.example.expense.Expense;
import org.example.expense.ExpenseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.example.auth.AuthService;
import org.example.auth.AppUser;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StatisticsServiceTest {
    private final BudgetRepository budgets = mock(BudgetRepository.class);
    private final ExpenseRepository expenses = mock(ExpenseRepository.class);
    private final AuthService auth = mock(AuthService.class);
    private final StatisticsService service = new StatisticsService(budgets, expenses, auth);

    @BeforeEach
    void authenticate() {
        AppUser user = new AppUser();
        user.setId(10L);
        when(auth.currentUser()).thenReturn(user);
    }

    @Test
    void calculatesTotalsAndAllowsOverspending() {
        Budget budget = new Budget();
        budget.setAmount(new BigDecimal("100.00"));
        when(budgets.findByUserId(10L)).thenReturn(List.of(budget));
        when(expenses.findByUserId(10L)).thenReturn(List.of(expense("120.25", "2026-11-12", 1L)));

        OverviewResponse result = service.overview(null);

        assertEquals(new BigDecimal("100.00"), result.getTotalBudget());
        assertEquals(new BigDecimal("120.25"), result.getTotalExpenses());
        assertEquals(new BigDecimal("-20.25"), result.getRemainingBudget());
        assertEquals(new BigDecimal("120.25"), result.getUsagePercentage());
    }

    @Test
    void handlesEmptyDatabaseWithoutDivisionByZero() {
        when(budgets.findByUserId(10L)).thenReturn(List.of());
        when(expenses.findByUserId(10L)).thenReturn(List.of());

        assertEquals(BigDecimal.ZERO, service.overview(null).getUsagePercentage());
        assertTrue(service.byCategory(null).isEmpty());
        assertTrue(service.daily(null).isEmpty());
    }

    @Test
    void groupsExpensesByCategoryAndSortsDays() {
        when(expenses.findByUserId(10L)).thenReturn(List.of(
                expense("10.10", "2026-11-12", 1L),
                expense("20.20", "2026-11-10", 1L),
                expense("5.00", "2026-11-12", 2L)));

        List<CategoryStatistics> categories = service.byCategory(null);
        assertEquals(2, categories.size());
        assertEquals(new BigDecimal("30.30"), categories.get(0).getTotalExpenses());
        List<DailyStatistics> days = service.daily(null);
        assertEquals(LocalDate.of(2026, 11, 10), days.get(0).getDate());
        assertEquals(new BigDecimal("15.10"), days.get(1).getTotalExpenses());
    }

    @Test
    void filtersStatisticsByBudget() {
        Budget budget = new Budget();
        budget.setAmount(new BigDecimal("300.00"));
        when(budgets.findByIdAndUserId(7L, 10L)).thenReturn(Optional.of(budget));
        when(expenses.findByBudgetIdAndUserId(7L, 10L)).thenReturn(List.of(expense("10.00", "2026-11-12", 1L)));

        assertEquals(new BigDecimal("3.33"), service.overview(7L).getUsagePercentage());
        verify(expenses, never()).findAll();
    }

    @Test
    void rejectsUnknownBudget() {
        when(budgets.findByIdAndUserId(99L, 10L)).thenReturn(Optional.empty());
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.daily(99L));
        assertEquals(404, exception.getStatusCode().value());
    }

    private Expense expense(String amount, String date, Long categoryId) {
        Category category = new Category();
        category.setId(categoryId);
        category.setName("Category " + categoryId);
        category.setColor("#123456");
        Expense expense = new Expense();
        expense.setAmount(new BigDecimal(amount));
        expense.setDate(LocalDate.parse(date));
        expense.setCategory(category);
        return expense;
    }
}
