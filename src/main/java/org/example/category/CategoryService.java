package org.example.category;

import org.example.expense.ExpenseRepository;
import org.springframework.http.HttpStatus;
import org.example.auth.AuthService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
@Transactional
public class CategoryService {
    private final AuthService authService;
    private final CategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;

    public CategoryService(CategoryRepository categoryRepository, ExpenseRepository expenseRepository, AuthService authService) {
        this.authService = authService;
        this.categoryRepository = categoryRepository;
        this.expenseRepository = expenseRepository;
    }

    public List<Category> findAll() {
        return categoryRepository.findByUserId(authService.currentUser().getId());
    }

    public Category findById(Long id) {
        return categoryRepository.findByIdAndUserId(id, authService.currentUser().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
    }

    public Category create(Category category) {
        category.setId(null);
        category.setUser(authService.currentUser());
        return categoryRepository.save(category);
    }

    public Category update(Long id, Category request) {
        Category category = findById(id);
        category.setName(request.getName());
        category.setColor(request.getColor());
        return categoryRepository.save(category);
    }

    public void delete(Long id) {
        Category category = findById(id);
        if (expenseRepository.existsByCategoryId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category contains expenses");
        }
        categoryRepository.delete(category);
    }
}
