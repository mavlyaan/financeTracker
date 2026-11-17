package org.example.budget;

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

class BudgetServiceTest {
    private final BudgetRepository budgets = mock(BudgetRepository.class);
    private final ExpenseRepository expenses = mock(ExpenseRepository.class);
    private final AuthService auth = mock(AuthService.class);
    private final BudgetService service = new BudgetService(budgets, expenses, auth);

    @BeforeEach
    void authenticate() {
        AppUser user = new AppUser();
        user.setId(10L);
        when(auth.currentUser()).thenReturn(user);
    }

    @Test
    void calculatesSpentAndRemainingBudget() {
        Budget budget = budget();
        Expense expense = new Expense();
        expense.setAmount(new BigDecimal("25.50"));
        when(budgets.findByIdAndUserId(1L, 10L)).thenReturn(Optional.of(budget));
        when(expenses.findByBudgetIdAndUserId(1L, 10L)).thenReturn(List.of(expense));

        BudgetResponse response = service.get(1L);
        assertEquals(new BigDecimal("25.50"), response.getSpentAmount());
        assertEquals(new BigDecimal("74.50"), response.getRemainingAmount());
    }

    @Test
    void rejectsReversedDates() {
        Budget budget = budget();
        budget.setEndDate(budget.getStartDate().minusDays(1));
        assertThrows(ResponseStatusException.class, () -> service.create(budget));
        verify(budgets, never()).save(any(Budget.class));
    }

    @Test
    void preventsDeletingBudgetWithExpenses() {
        when(budgets.findByIdAndUserId(1L, 10L)).thenReturn(Optional.of(budget()));
        when(expenses.existsByBudgetId(1L)).thenReturn(true);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.delete(1L));
        assertEquals(409, exception.getStatusCode().value());
        verify(budgets, never()).delete(any(Budget.class));
    }

    @Test
    void preventsExcludingExistingExpenseFromDateRange() {
        Expense expense = new Expense();
        expense.setDate(LocalDate.of(2026, 11, 5));
        when(budgets.findByIdAndUserId(1L, 10L)).thenReturn(Optional.of(budget()));
        when(expenses.findByBudgetIdAndUserId(1L, 10L)).thenReturn(List.of(expense));
        Budget request = budget();
        request.setStartDate(LocalDate.of(2026, 11, 10));
        assertThrows(ResponseStatusException.class, () -> service.update(1L, request));
        verify(budgets, never()).save(any(Budget.class));
    }

    private Budget budget() {
        Budget budget = new Budget();
        budget.setId(1L);
        budget.setName("November");
        budget.setAmount(new BigDecimal("100.00"));
        budget.setStartDate(LocalDate.of(2026, 11, 1));
        budget.setEndDate(LocalDate.of(2026, 11, 30));
        return budget;
    }
}
