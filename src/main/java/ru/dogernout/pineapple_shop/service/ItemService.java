package ru.dogernout.pineapple_shop.service;

import lombok.RequiredArgsConstructor;
import ru.dogernout.pineapple_shop.domain.Item;
import ru.dogernout.pineapple_shop.exception.EntityNotFoundException;
import ru.dogernout.pineapple_shop.repository.ItemRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ItemService {

    private final ItemRepository itemRepository;
    private final UserService userService;

    @Transactional
    public Item addItem(Long adminTelegramId, String title, String description, int price, int quantity) {
        if (price <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity must not be negative");
        }
        var admin = userService.requireAdmin(adminTelegramId);
        var item = new Item(title, description, price, quantity, admin);
        return itemRepository.save(item);
    }

    @Transactional(readOnly = true)
    public List<Item> listAvailable() {
        return itemRepository.findByActiveTrueOrderByTitleAsc();
    }

    @Transactional(readOnly = true)
    public Item getById(Long itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("Item", itemId));
    }

    @Transactional
    public void deactivate(Long adminTelegramId, Long itemId) {
        userService.requireAdmin(adminTelegramId);
        getById(itemId).deactivate();
    }

    /**
     * Пополнение остатка без создания новой записи в каталоге
     * (например, когда админ "довёз" ещё экземпляров существующей вещи).
     */
    @Transactional
    public void restock(Long adminTelegramId, Long itemId, int additionalQuantity) {
        if (additionalQuantity <= 0) {
            throw new IllegalArgumentException("Additional quantity must be positive");
        }
        userService.requireAdmin(adminTelegramId);
        if (!itemRepository.existsById(itemId)) {
            throw new EntityNotFoundException("Item", itemId);
        }
        itemRepository.incrementQuantity(itemId, additionalQuantity);
    }
}
