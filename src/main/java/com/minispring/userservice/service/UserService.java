package com.minispring.userservice.service;

import com.minispring.userservice.dto.request.AdminUserUpdateRequest;
import com.minispring.userservice.dto.request.UserCreateRequest;
import com.minispring.userservice.dto.request.UserSearchCriteria;
import com.minispring.userservice.dto.request.UserUpdateRequest;
import com.minispring.userservice.dto.response.UserView;
import com.minispring.userservice.model.User;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {

    UserView create(UserCreateRequest request);

    UserView getById(UUID userId);

    UserView getHistoricalUserById(UUID userId);

    UserView getHistoricalUserByEmail(String email);

    List<UserView> getHistoricalUsersByIds(List<UUID> userIds);

    Page<UserView> getAllBy(UserSearchCriteria userSearchCriteria, Pageable pageable);

    User getValidUserEntity(UUID userId);

    User getValidUserEntityForUpdate(UUID userId);

    User getUserReference(UUID userId);

    void validateUserAllowed(User user);

    UserView update(UUID userId, AdminUserUpdateRequest request);

    UserView update(UUID userId, UserUpdateRequest request);

    void delete(UUID userId);

    UserView deactivate(UUID userId);

    UserView activate(UUID userId);
}
