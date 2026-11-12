package com.collegeconnect.repository;

import com.collegeconnect.entity.ParentStudentLink;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParentStudentLinkRepository extends JpaRepository<ParentStudentLink, Long> {

    List<ParentStudentLink> findByParent_Id(Long parentId);

    List<ParentStudentLink> findByStudent_Id(Long studentId);

    List<ParentStudentLink> findByParent_IdAndStudent_Id(Long parentId, Long studentId);
}
