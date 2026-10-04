package ru.dogernout.pineapple_shop.repository;

import ru.dogernout.pineapple_shop.domain.EarnReason;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EarnReasonRepository extends JpaRepository<EarnReason, Long> {

    List<EarnReason> findByActiveTrueOrderByTitleAsc();
}
