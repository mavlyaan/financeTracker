package org.example.expense;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByUserId(Long userId);
    Optional<Expense> findByIdAndUserId(Long id, Long userId);
    List<Expense> findByBudgetIdAndUserId(Long budgetId, Long userId);
    boolean existsByBudgetId(Long budgetId);
    boolean existsByCategoryId(Long categoryId);
}
