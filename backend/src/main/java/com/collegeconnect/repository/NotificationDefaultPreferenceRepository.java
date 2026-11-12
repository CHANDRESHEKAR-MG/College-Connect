package com.collegeconnect.repository;

import com.collegeconnect.entity.NotificationDefaultPreference;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDefaultPreferenceRepository
        extends JpaRepository<NotificationDefaultPreference, Long> {

    Optional<NotificationDefaultPreference> findByCategory(String category);

    List<NotificationDefaultPreference> findAllByOrderByCategoryAsc();
}
