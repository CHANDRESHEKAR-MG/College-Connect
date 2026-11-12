package com.collegeconnect.repository;

import com.collegeconnect.entity.NotificationPreference;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    Optional<NotificationPreference> findByParent_IdAndCategory(Long parentId, String category);

    List<NotificationPreference> findByParent_IdOrderByCategoryAsc(Long parentId);
}
