package ru.dogernout.pineapple_shop.repository;

import ru.dogernout.pineapple_shop.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByTelegramId(Long telegramId);

    /**
     * Атомарное начисление. Отдельного условия не нужно — начисление
     * всегда допустимо (ограничение снизу balance >= 0 проверяется на debit).
     *
     * @return количество обновлённых строк (0 или 1)
     */
    @Modifying
    @Query("update User u set u.balance = u.balance + :amount where u.id = :userId")
    int creditBalance(@Param("userId") Long userId, @Param("amount") int amount);

    /**
     * Атомарное списание с проверкой достаточности баланса прямо в WHERE —
     * это и есть защита от гонки и ухода в минус без отдельного SELECT ... FOR UPDATE.
     *
     * @return 1, если списание прошло; 0, если баланса недостаточно (строка не обновилась)
     */
    @Modifying
    @Query("update User u set u.balance = u.balance - :amount where u.id = :userId and u.balance >= :amount")
    int debitBalance(@Param("userId") Long userId, @Param("amount") int amount);
}
