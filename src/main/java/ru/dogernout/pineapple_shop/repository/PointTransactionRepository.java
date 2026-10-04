package ru.dogernout.pineapple_shop.repository;

import ru.dogernout.pineapple_shop.domain.PointTransaction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {

    List<PointTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);
}
