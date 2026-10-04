package ru.dogernout.pineapple_shop.repository;

import ru.dogernout.pineapple_shop.domain.Item;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemRepository extends JpaRepository<Item, Long> {

    List<Item> findByActiveTrueOrderByTitleAsc();

    /**
     * Атомарное уменьшение остатка с проверкой наличия прямо в WHERE.
     *
     * @return 1, если остаток уменьшен; 0, если товара нет в наличии
     */
    @Modifying
    @Query("update Item i set i.quantity = i.quantity - 1 where i.id = :itemId and i.quantity >= 1")
    int decrementQuantity(@Param("itemId") Long itemId);

    /**
     * Атомарный возврат остатка на 1 (используется при refund).
     */
    @Modifying
    @Query("update Item i set i.quantity = i.quantity + 1 where i.id = :itemId")
    int incrementQuantity(@Param("itemId") Long itemId);

    /**
     * Атомарное пополнение остатка на произвольное количество (используется при restock).
     */
    @Modifying
    @Query("update Item i set i.quantity = i.quantity + :amount where i.id = :itemId")
    int incrementQuantity(@Param("itemId") Long itemId, @Param("amount") int amount);
}
