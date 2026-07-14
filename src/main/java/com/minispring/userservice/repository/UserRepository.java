package com.minispring.userservice.repository;

import com.minispring.userservice.dto.request.UserSearchCriteria;
import com.minispring.userservice.model.User;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.cards WHERE u.id = :id")
    Optional<User> findUserWithCardsById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findUserForUpdateById(@Param("id") UUID id);

    @Query(value = "SELECT * FROM users u WHERE u.id = :id", nativeQuery = true)
    Optional<User> findHistoricalUserById(@Param("id") UUID id);

    @Query(value = "SELECT * FROM users u WHERE LOWER(u.email) = LOWER(:email)", nativeQuery = true)
    Optional<User> findHistoricalUserByEmail(@Param("email") String email);

    @Query(value = "SELECT * FROM users u WHERE u.id IN (:ids)", nativeQuery = true)
    List<User> findHistoricalUsersByIds(@Param("ids") List<UUID> ids);

    default Page<User> findByParams(UserSearchCriteria params, Pageable pageable) {
        if (params == null) {
            return findAll(pageable);
        }

        Specification<User> specification =
                Specification.where(nameLike(params.name())).and(surnameLike(params.surname()));

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
            return (_, _, cb) -> cb.conjunction();
        }

        return (user, _, cb) -> cb.like(cb.lower(user.get(fieldName)), "%" + search.toLowerCase() + "%");
    }
}
