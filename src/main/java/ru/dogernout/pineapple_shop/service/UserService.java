package ru.dogernout.pineapple_shop.service;

import lombok.RequiredArgsConstructor;
import ru.dogernout.pineapple_shop.domain.User;
import ru.dogernout.pineapple_shop.domain.enums.Role;
import ru.dogernout.pineapple_shop.exception.AccessDeniedException;
import ru.dogernout.pineapple_shop.exception.EntityNotFoundException;
import ru.dogernout.pineapple_shop.repository.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public User getByTelegramId(Long telegramId) {
        return userRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new EntityNotFoundException("User", telegramId));
    }

    @Transactional(readOnly = true)
    public Optional<User> findByTelegramId(Long telegramId) {
        return userRepository.findByTelegramId(telegramId);
    }

    /**
     * Регистрация известного пользователя. При двух пользователях записи обычно
     * создаются один раз вручную (миграцией/seed'ом), но метод полезен для
     * первого /start, чтобы подтянуть актуальный username.
     */
    @Transactional
    public User registerIfAbsent(Long telegramId, String username, Role role) {
        return userRepository.findByTelegramId(telegramId)
                .orElseGet(() -> userRepository.save(new User(telegramId, username, role)));
    }

    /**
     * В системе ровно два пользователя — этот метод возвращает единственного MEMBER,
     * избавляя admin-сценарии (начисление, список истории и т.д.) от выбора получателя.
     */
    @Transactional(readOnly = true)
    public User getTheMember() {
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.MEMBER)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No MEMBER user is registered yet"));
    }

    @Transactional(readOnly = true)
    public Optional<User> findTheMember() {
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.MEMBER)
                .findFirst();
    }

    @Transactional(readOnly = true)
    public User requireAdmin(Long telegramId) {
        User user = getByTelegramId(telegramId);
        if (!user.isAdmin()) {
            throw new AccessDeniedException(user.getId());
        }
        return user;
    }
}
