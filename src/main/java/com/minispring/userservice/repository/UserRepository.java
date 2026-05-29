package com.minispring.userservice.repository;

import com.minispring.userservice.dto.UserParamsDto;
import com.minispring.userservice.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.cards WHERE u.id = :id")
    Optional<User> findUserWithCardsById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findUserForUpdateById(@Param("id") UUID id);

    @Modifying
    @Query(value = "UPDATE users SET deleted = false WHERE id = :userId", nativeQuery = true)
    void restoreDeletedUser(@Param("userId") UUID userId);

    @Query(value = "SELECT * FROM users u WHERE LOWER(u.email) = LOWER(:email)", nativeQuery = true)
    Optional<User> findUserByEmailIncludingDeleted(@Param("email") String email);

    @Query(value = "SELECT * FROM users u WHERE u.id IN (:ids)", nativeQuery = true)
    List<User> findAllByIdsIncludingDeleted(@Param("ids") List<UUID> ids);

    default Page<User> findByParams(UserParamsDto params, Pageable pageable) {
        if (params == null) {
            return findAll(pageable);
        }

        Specification<User> specification = Specification.where(nameLike(params.name()))
                    .and(surnameLike(params.surname()));

        return findAll(specification, pageable);
    }

    private Specification<User> nameLike(String search) {
        return getValue("name", search);
    }

    private Specification<User> surnameLike(String search) {
        return getValue("surname", search);
    }

    private Specification<User> getValue(String fieldName, String search) {
        if (!StringUtils.hasText(search)) {
            return (root, query, cb) -> cb.conjunction();
        }

        return (user, cq, cb) -> cb.like(
                cb.lower(user.get(fieldName)),
                "%" + search.toLowerCase() + "%"
        );
    }
}
