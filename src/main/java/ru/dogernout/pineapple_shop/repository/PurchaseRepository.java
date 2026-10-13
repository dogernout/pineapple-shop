package ru.dogernout.pineapple_shop.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import ru.dogernout.pineapple_shop.domain.Purchase;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    @EntityGraph(attributePaths = "item")
    List<Purchase> findByUserIdOrderByPurchasedAtDesc(Long userId);

    @EntityGraph(attributePaths = "item")
    Optional<Purchase> findWithItemById(Long id);
}
