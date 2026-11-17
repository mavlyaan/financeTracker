package org.example.expense;

import org.example.budget.Budget;
import org.example.budget.BudgetService;
import org.example.category.CategoryService;
import org.springframework.http.HttpStatus;
import org.example.auth.AuthService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
@Transactional
public class ExpenseService {
    private final AuthService authService;
    private final ExpenseRepository expenseRepository;
    private final BudgetService budgetService;
    private final CategoryService categoryService;

    public ExpenseService(ExpenseRepository expenseRepository, BudgetService budgetService, CategoryService categoryService, AuthService authService) {
        this.authService = authService;
        this.expenseRepository = expenseRepository;
        this.budgetService = budgetService;
        this.categoryService = categoryService;
    }

    public List<Expense> findAll(Long budgetId) {
        if (budgetId != null) {
            budgetService.findById(budgetId);
            return expenseRepository.findByBudgetIdAndUserId(budgetId, authService.currentUser().getId());
        }
        return expenseRepository.findByUserId(authService.currentUser().getId());
    }

    public Expense findById(Long id) {
        return expenseRepository.findByIdAndUserId(id, authService.currentUser().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));
    }

    public Expense create(ExpenseRequest request) {
        Expense expense = new Expense();
        expense.setUser(authService.currentUser());
        fillExpense(expense, request);
        return expenseRepository.save(expense);
    }

    public Expense update(Long id, ExpenseRequest request) {
        Expense expense = findById(id);
        fillExpense(expense, request);
        return expenseRepository.save(expense);
    }

    public void delete(Long id) {
        expenseRepository.delete(findById(id));
    }

    private void fillExpense(Expense expense, ExpenseRequest request) {
        Budget budget = budgetService.findById(request.getBudgetId());
        if (request.getDate().isBefore(budget.getStartDate()) || request.getDate().isAfter(budget.getEndDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expense date must be within budget dates");
        }
        expense.setCategory(categoryService.findById(request.getCategoryId()));
        expense.setBudget(budget);
        expense.setAmount(request.getAmount());
        expense.setDate(request.getDate());
        expense.setDescription(request.getDescription());
    }
}
