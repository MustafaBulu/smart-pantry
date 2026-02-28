package com.mustafabulu.smartpantry.common.repository;

import com.mustafabulu.smartpantry.common.model.NeedListItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NeedListItemRepository extends JpaRepository<NeedListItem, Long> {

    List<NeedListItem> findAllByOrderByIdDesc();
}
