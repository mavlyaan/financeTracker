package org.example.budget;

import org.example.expense.Expense;
import org.example.expense.ExpenseRepository;
import org.springframework.http.HttpStatus;
import org.example.auth.AuthService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class BudgetService {
    private final AuthService authService;
    private final BudgetRepository budgetRepository;
    private final ExpenseRepository expenseRepository;

    public BudgetService(BudgetRepository budgetRepository, ExpenseRepository expenseRepository, AuthService authService) {
        this.authService = authService;
        this.budgetRepository = budgetRepository;
        this.expenseRepository = expenseRepository;
    }

    public Budget findById(Long id) {
        return budgetRepository.findByIdAndUserId(id, authService.currentUser().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget not found"));
    }

    public List<BudgetResponse> findAll() {
        List<BudgetResponse> budgets = new ArrayList<>();
        for (Budget budget : budgetRepository.findByUserId(authService.currentUser().getId())) {
            budgets.add(toResponse(budget));
        }
        return budgets;
    }

    public BudgetResponse get(Long id) {
        return toResponse(findById(id));
    }

    public BudgetResponse create(Budget budget) {
        validateDates(budget);
        budget.setId(null);
        budget.setUser(authService.currentUser());
        return toResponse(budgetRepository.save(budget));
    }

    public BudgetResponse update(Long id, Budget request) {
        Budget budget = findById(id);
        validateDates(request);
        for (Expense expense : expenseRepository.findByBudgetIdAndUserId(id, authService.currentUser().getId())) {
            if (expense.getDate().isBefore(request.getStartDate()) || expense.getDate().isAfter(request.getEndDate())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Budget dates must include existing expenses");
            }
        }
        budget.setName(request.getName());
        budget.setAmount(request.getAmount());
        budget.setStartDate(request.getStartDate());
        budget.setEndDate(request.getEndDate());
        return toResponse(budgetRepository.save(budget));
    }

    public void delete(Long id) {
        Budget budget = findById(id);
        if (expenseRepository.existsByBudgetId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Budget contains expenses");
        }
        budgetRepository.delete(budget);
    }

    private void validateDates(Budget budget) {
        if (budget.getEndDate().isBefore(budget.getStartDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date must not be before start date");
        }
    }

    private BudgetResponse toResponse(Budget budget) {
        BigDecimal spent = BigDecimal.ZERO;
        for (Expense expense : expenseRepository.findByBudgetIdAndUserId(budget.getId(), authService.currentUser().getId())) {
            spent = spent.add(expense.getAmount());
        }
        BudgetResponse response = new BudgetResponse();
        response.setId(budget.getId());
        response.setName(budget.getName());
        response.setAmount(budget.getAmount());
        response.setStartDate(budget.getStartDate());
        response.setEndDate(budget.getEndDate());
        response.setSpentAmount(spent);
        response.setRemainingAmount(budget.getAmount().subtract(spent));
        return response;
    }
}
