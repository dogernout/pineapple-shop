package ru.dogernout.pineapple_shop.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import ru.dogernout.pineapple_shop.domain.PointTransaction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {

    List<PointTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"purchase", "purchase.item"})
    Slice<PointTransaction> findByUserId(Long userId, Pageable pageable);
}
