package ru.dogernout.pineapple_shop.service;

import lombok.RequiredArgsConstructor;
import ru.dogernout.pineapple_shop.domain.EarnReason;
import ru.dogernout.pineapple_shop.exception.EntityNotFoundException;
import ru.dogernout.pineapple_shop.repository.EarnReasonRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Справочник "за что начисляются баллы" — чисто информационный, без связи
 * с point_transactions. Начисление всё так же делается вручную через PointsService.credit().
 */
@Service
@RequiredArgsConstructor
public class EarnReasonService {

    private final EarnReasonRepository earnReasonRepository;
    private final UserService userService;

    // points may be null
    @Transactional
    public EarnReason addReason(Long adminTelegramId, String title, String description, Integer points) {
        if (points != null && points <= 0) {
            throw new IllegalArgumentException("Points must be positive when specified");
        }
        var admin = userService.requireAdmin(adminTelegramId);
        var reason = new EarnReason(title, description, points, admin);
        return earnReasonRepository.save(reason);
    }

    @Transactional(readOnly = true)
    public List<EarnReason> listAvailable() {
        return earnReasonRepository.findByActiveTrueOrderByTitleAsc();
    }

    @Transactional
    public void deactivate(Long adminTelegramId, Long reasonId) {
        userService.requireAdmin(adminTelegramId);
        var reason = earnReasonRepository.findById(reasonId)
                .orElseThrow(() -> new EntityNotFoundException("EarnReason", reasonId));
        reason.deactivate();
    }

    // TODO: add activate ?
}
