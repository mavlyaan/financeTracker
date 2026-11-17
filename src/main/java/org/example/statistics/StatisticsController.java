package org.example.statistics;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {
    private final StatisticsService service;

    public StatisticsController(StatisticsService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public OverviewResponse overview(@RequestParam(required = false) Long budgetId) {
        return service.overview(budgetId);
    }

    @GetMapping("/by-category")
    public List<CategoryStatistics> byCategory(@RequestParam(required = false) Long budgetId) {
        return service.byCategory(budgetId);
    }

    @GetMapping("/daily")
    public List<DailyStatistics> daily(@RequestParam(required = false) Long budgetId) {
        return service.daily(budgetId);
    }
}
