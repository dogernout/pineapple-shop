package ru.dogernout.pineapple_shop.repository;

import ru.dogernout.pineapple_shop.domain.Purchase;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    List<Purchase> findByUserIdOrderByPurchasedAtDesc(Long userId);
}
