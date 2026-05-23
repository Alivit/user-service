package com.minispring.userservice.repository;

import com.minispring.userservice.dto.UserParamsDto;
import com.minispring.userservice.model.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import liquibase.util.StringUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.util.StringUtils;

import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    default Page<User> findByParams(UserParamsDto params, Pageable pageable) {
        if (params == null) {
            return findAll(pageable);
        }

        Specification<User> specification = Specification.where(nameLike(params.name()))
                .and(surnameLike(params.surname()));

        return findAll(specification, pageable);
    }

    default Specification<User> nameLike(String search) {
        return getValue("name", search);
    }

    default Specification<User> surnameLike(String search) {
        return getValue("surname", search);
    }

    default Specification<User> getValue(String fieldName, String search) {
        if (!StringUtils.hasText(search)) {
            return (root, query, cb) -> cb.conjunction();
        }

        return (user, cq, cb) -> cb.like(
                cb.lower(user.get(fieldName)),
                "%" + search.toLowerCase() + "%"
        );
    }

    boolean existsByEmail(String email);
}
