package com.minispring.userservice.service.impl;

import static com.minispring.userservice.exception.ExceptionAnswer.EMAIL_EXIST;
import static com.minispring.userservice.exception.ExceptionAnswer.EMAIL_NOT_FOUND;
import static com.minispring.userservice.exception.ExceptionAnswer.USER_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.minispring.userservice.client.AuthGrpcClient;
import com.minispring.userservice.dto.request.AdminUserUpdateRequest;
import com.minispring.userservice.dto.request.UserCreateRequest;
import com.minispring.userservice.dto.request.UserSearchCriteria;
import com.minispring.userservice.dto.request.UserUpdateRequest;
import com.minispring.userservice.dto.response.UserView;
import com.minispring.userservice.exception.BadRequestException;
import com.minispring.userservice.exception.ResourceAlreadyExistsException;
import com.minispring.userservice.exception.ResourceNotFoundException;
import com.minispring.userservice.mapper.UserMapper;
import com.minispring.userservice.model.User;
import com.minispring.userservice.repository.UserRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.instancio.Instancio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private AuthGrpcClient authGrpcClient;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private UserServiceImpl userService;

    private User createActiveUser(UUID userId) {
        return Instancio.of(User.class)
                .set(field(User::getId), userId)
                .set(field(User::getActive), true)
                .set(field(User::isDeleted), false)
                .create();
    }

    @Nested
    class CreateTest {

        @Test
        void shouldCreateUserSuccessfully() {
            UserCreateRequest request = Instancio.create(UserCreateRequest.class);
            User entity = Instancio.create(User.class);
            User savedEntity = Instancio.create(User.class);
            UserView expectedView = Instancio.create(UserView.class);

            given(userMapper.toEntity(request)).willReturn(entity);
            given(userRepository.saveAndFlush(entity)).willReturn(savedEntity);
            given(userMapper.toViewWithoutCards(savedEntity)).willReturn(expectedView);

            UserView result = userService.create(request);

            assertThat(result).isNotNull().isEqualTo(expectedView);
            then(userRepository).should().saveAndFlush(entity);
        }

        @Test
        void shouldThrowWhenEmailAlreadyExists() {
            UserCreateRequest request = Instancio.create(UserCreateRequest.class);
            User entity = Instancio.create(User.class);

            given(userMapper.toEntity(request)).willReturn(entity);
            given(userRepository.saveAndFlush(entity))
                    .willThrow(new DataIntegrityViolationException("Unique constraint violated"));

            assertThatThrownBy(() -> userService.create(request))
                    .isInstanceOf(ResourceAlreadyExistsException.class)
                    .hasMessage(String.format(EMAIL_EXIST, request.email()));

            then(userMapper).should(never()).toViewWithoutCards(any());
        }
    }

    @Nested
    class GetByIdTest {

        @Test
        void shouldReturnUserView() {
            UUID userId = UUID.randomUUID();
            User user = createActiveUser(userId);
            UserView expectedView = Instancio.create(UserView.class);

            given(userRepository.findUserWithCardsById(userId)).willReturn(Optional.of(user));
            given(userMapper.toView(user)).willReturn(expectedView);

            UserView result = userService.getById(userId);

            assertThat(result).isNotNull().isEqualTo(expectedView);
            then(userMapper).should().toView(user);
        }

        @Test
        void shouldThrowBadRequestWhenUserIsBlocked() {
            UUID userId = UUID.randomUUID();
            User user = createActiveUser(userId);
            user.setActive(false);

            given(userRepository.findUserWithCardsById(userId)).willReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.getById(userId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Action denied: User is blocked or deleted");

            then(userMapper).shouldHaveNoInteractions();
        }

        @Test
        void shouldThrowBadRequestWhenUserIsDeleted() {
            UUID userId = UUID.randomUUID();
            User user = createActiveUser(userId);
            user.setDeleted(true);

            given(userRepository.findUserWithCardsById(userId)).willReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.getById(userId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Action denied: User is blocked or deleted");

            then(userMapper).shouldHaveNoInteractions();
        }

        @Test
        void shouldThrowResourceNotFoundException() {
            UUID userId = UUID.randomUUID();

            given(userRepository.findUserWithCardsById(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getById(userId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage(String.format(USER_NOT_FOUND, userId));

            then(userMapper).shouldHaveNoInteractions();
        }
    }

    @Nested
    class HistoricalTest {

        @Test
        void shouldReturnViewById() {
            User user = Instancio.create(User.class);
            UUID userId = user.getId();
            UserView expectedView = Instancio.create(UserView.class);

            given(userRepository.findHistoricalUserById(userId)).willReturn(Optional.of(user));
            given(userMapper.toView(user)).willReturn(expectedView);

            UserView result = userService.getHistoricalUserById(userId);

            assertThat(result).isNotNull().isEqualTo(expectedView);
            then(userMapper).should().toView(user);
        }

        @Test
        void shouldThrowResourceNotFoundExceptionById() {
            UUID userId = UUID.randomUUID();

            given(userRepository.findHistoricalUserById(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getHistoricalUserById(userId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage(String.format(USER_NOT_FOUND, userId));

            then(userMapper).shouldHaveNoInteractions();
        }

        @Test
        void shouldReturnViewByEmail() {
            User user = Instancio.create(User.class);
            String email = user.getEmail();
            UserView expectedView = Instancio.create(UserView.class);

            given(userRepository.findHistoricalUserByEmail(email)).willReturn(Optional.of(user));
            given(userMapper.toView(user)).willReturn(expectedView);

            UserView result = userService.getHistoricalUserByEmail(email);

            assertThat(result).isNotNull().isEqualTo(expectedView);
            then(userMapper).should().toView(user);
        }

        @Test
        void shouldThrowResourceNotFoundExceptionByEmail() {
            String email = "test@example.com";

            given(userRepository.findHistoricalUserByEmail(email)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getHistoricalUserByEmail(email))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage(String.format(EMAIL_NOT_FOUND, email));

            then(userMapper).shouldHaveNoInteractions();
        }

        @Test
        void shouldReturnListOfViews() {
            List<User> users = Instancio.ofList(User.class).size(2).create();
            List<UUID> ids = users.stream().map(User::getId).toList();
            UserView view1 = Instancio.create(UserView.class);
            UserView view2 = Instancio.create(UserView.class);

            given(userRepository.findHistoricalUsersByIds(ids)).willReturn(users);
            given(userMapper.toView(users.get(0))).willReturn(view1);
            given(userMapper.toView(users.get(1))).willReturn(view2);

            List<UserView> result = userService.getHistoricalUsersByIds(ids);

            assertThat(result).hasSize(2).containsExactly(view1, view2);
            then(userMapper).should().toView(users.get(0));
            then(userMapper).should().toView(users.get(1));
        }

        @Test
        void shouldReturnEmptyList() {
            List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());

            given(userRepository.findHistoricalUsersByIds(ids)).willReturn(List.of());

            List<UserView> result = userService.getHistoricalUsersByIds(ids);

            assertThat(result).isEmpty();
            then(userMapper).shouldHaveNoInteractions();
        }
    }

    @Nested
    class GetAllByTest {

        static Stream<UserSearchCriteria> provideSearchCriteria() {
            return Stream.of(
                    new UserSearchCriteria("NewName", "NewSurname"),
                    new UserSearchCriteria("NewName", null),
                    new UserSearchCriteria(null, "NewSurname"),
                    new UserSearchCriteria(null, null),
                    null);
        }

        @ParameterizedTest
        @MethodSource("provideSearchCriteria")
        void shouldReturnPagedViews(UserSearchCriteria criteria) {
            Pageable pageable = PageRequest.of(0, 10);
            List<User> users = Instancio.ofList(User.class).size(2).create();
            Page<User> userPage = new PageImpl<>(users, pageable, 2);
            UserView expectedView = Instancio.create(UserView.class);

            given(userRepository.findByParams(criteria, pageable)).willReturn(userPage);
            given(userMapper.toView(any(User.class))).willReturn(expectedView);

            Page<UserView> result = userService.getAllBy(criteria, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent()).containsExactly(expectedView, expectedView);

            then(userMapper).should().toView(users.get(0));
            then(userMapper).should().toView(users.get(1));
        }

        @ParameterizedTest
        @MethodSource("provideSearchCriteria")
        void shouldReturnEmptyPage(UserSearchCriteria criteria) {
            Pageable pageable = PageRequest.of(0, 10);
            Page<User> emptyPage = Page.empty(pageable);

            given(userRepository.findByParams(criteria, pageable)).willReturn(emptyPage);

            Page<UserView> result = userService.getAllBy(criteria, pageable);

            assertThat(result).isNotNull();
            assertThat(result.isEmpty()).isTrue();
            assertThat(result.getTotalElements()).isZero();

            then(userMapper).shouldHaveNoInteractions();
        }
    }

    @Nested
    class UserUpdateTest {

        static Stream<UserUpdateRequest> provideUserUpdateRequests() {
            return Stream.of(
                    new UserUpdateRequest("NewName", "NewSurname"),
                    new UserUpdateRequest("NewName", null),
                    new UserUpdateRequest(null, "NewSurname"),
                    new UserUpdateRequest(null, null),
                    null);
        }

        @ParameterizedTest
        @MethodSource("provideUserUpdateRequests")
        void shouldCallMapperAndReturnView(UserUpdateRequest request) {
            UUID userId = UUID.randomUUID();
            User existingUser = createActiveUser(userId);
            UserView expectedView = Instancio.create(UserView.class);

            given(userRepository.findById(userId)).willReturn(Optional.of(existingUser));
            given(userMapper.toViewWithoutCards(existingUser)).willReturn(expectedView);

            UserView result = userService.update(userId, request);

            assertThat(result).isEqualTo(expectedView);
            then(userMapper).should().update(request, existingUser);
        }

        @Test
        void shouldThrowBadRequestWhenUserIsBlocked() {
            UUID userId = UUID.randomUUID();
            User existingUser = createActiveUser(userId);
            existingUser.setActive(false);
            UserUpdateRequest request = Instancio.create(UserUpdateRequest.class);

            given(userRepository.findById(userId)).willReturn(Optional.of(existingUser));

            assertThatThrownBy(() -> userService.update(userId, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Action denied: User is blocked or deleted");

            then(userMapper).shouldHaveNoInteractions();
        }

        @Test
        void shouldThrowBadRequestWhenUserIsDeleted() {
            UUID userId = UUID.randomUUID();
            User existingUser = createActiveUser(userId);
            existingUser.setDeleted(true);
            UserUpdateRequest request = Instancio.create(UserUpdateRequest.class);

            given(userRepository.findById(userId)).willReturn(Optional.of(existingUser));

            assertThatThrownBy(() -> userService.update(userId, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Action denied: User is blocked or deleted");

            then(userMapper).shouldHaveNoInteractions();
        }

        @Test
        void shouldThrowResourceNotFoundException() {
            UUID userId = UUID.randomUUID();
            UserUpdateRequest request = Instancio.create(UserUpdateRequest.class);

            given(userRepository.findById(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.update(userId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage(String.format(USER_NOT_FOUND, userId));

            then(userMapper).shouldHaveNoInteractions();
        }
    }

    @Nested
    class GetValidUserEntityTest {

        @AfterEach
        void clearSecurityContext() {
            SecurityContextHolder.clearContext();
        }

        @Test
        void shouldReturnUserWhenValid() {
            UUID userId = UUID.randomUUID();
            User user = createActiveUser(userId);

            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            User result = userService.getValidUserEntity(userId);

            assertThat(result).isEqualTo(user);
            then(userRepository).should().findById(userId);
        }

        @Test
        void shouldThrowNotFoundWhenUserDoesNotExist() {
            UUID userId = UUID.randomUUID();
            given(userRepository.findById(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getValidUserEntity(userId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage(String.format(USER_NOT_FOUND, userId));
        }

        @Test
        void shouldThrowBadRequestWhenUserIsBlocked() {
            UUID userId = UUID.randomUUID();
            User user = createActiveUser(userId);
            user.setActive(false);

            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.getValidUserEntity(userId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Action denied: User is blocked or deleted");
        }

        @Test
        void shouldThrowBadRequestWhenUserIsDeleted() {
            UUID userId = UUID.randomUUID();
            User user = createActiveUser(userId);
            user.setDeleted(true);

            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.getValidUserEntity(userId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Action denied: User is blocked or deleted");
        }
    }

    @Nested
    class GetValidUserEntityForUpdateTest {

        @AfterEach
        void clearSecurityContext() {
            SecurityContextHolder.clearContext();
        }

        @Test
        void shouldReturnUserWhenValid() {
            UUID userId = UUID.randomUUID();
            User user = createActiveUser(userId);

            given(userRepository.findUserForUpdateById(userId)).willReturn(Optional.of(user));

            User result = userService.getValidUserEntityForUpdate(userId);

            assertThat(result).isEqualTo(user);
            then(userRepository).should().findUserForUpdateById(userId);
        }

        @Test
        void shouldThrowNotFoundWhenUserDoesNotExist() {
            UUID userId = UUID.randomUUID();
            given(userRepository.findUserForUpdateById(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getValidUserEntityForUpdate(userId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage(String.format(USER_NOT_FOUND, userId));
        }

        @Test
        void shouldThrowBadRequestWhenUserIsBlocked() {
            UUID userId = UUID.randomUUID();
            User user = createActiveUser(userId);
            user.setActive(false);

            given(userRepository.findUserForUpdateById(userId)).willReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.getValidUserEntityForUpdate(userId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Action denied: User is blocked or deleted");
        }
    }

    @Nested
    class GetUserReferenceTest {

        @Test
        void shouldReturnProxyUser() {
            UUID userId = UUID.randomUUID();
            User proxyUser = Instancio.create(User.class);

            given(userRepository.getReferenceById(userId)).willReturn(proxyUser);

            User result = userService.getUserReference(userId);

            assertThat(result).isEqualTo(proxyUser);
            then(userRepository).should().getReferenceById(userId);
        }
    }

    @Nested
    class ValidateUserAllowedTest {

        @AfterEach
        void clearSecurityContext() {
            SecurityContextHolder.clearContext();
        }

        @Test
        void shouldPassWhenUserIsActiveAndNotDeleted() {
            User user = createActiveUser(UUID.randomUUID());
            userService.validateUserAllowed(user);
        }

        @Test
        void shouldPassWhenUserIsBlockedButAdminContextExists() {
            User blockedUser = createActiveUser(UUID.randomUUID());
            blockedUser.setActive(false);

            var securityContext = mock(SecurityContext.class);
            var authentication = mock(Authentication.class);

            given(securityContext.getAuthentication()).willReturn(authentication);
            doReturn(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
                    .when(authentication)
                    .getAuthorities();

            SecurityContextHolder.setContext(securityContext);

            userService.validateUserAllowed(blockedUser);
        }

        @Test
        void shouldThrowWhenRegularUserIsBlocked() {
            User blockedUser = createActiveUser(UUID.randomUUID());
            blockedUser.setActive(false);

            assertThatThrownBy(() -> userService.validateUserAllowed(blockedUser))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Action denied: User is blocked or deleted");
        }

        @Test
        void shouldThrowWhenRegularUserIsDeleted() {
            User deletedUser = createActiveUser(UUID.randomUUID());
            deletedUser.setDeleted(true);

            assertThatThrownBy(() -> userService.validateUserAllowed(deletedUser))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Action denied: User is blocked or deleted");
        }
    }

    @Nested
    class AdminUpdateTest {

        static Stream<AdminUserUpdateRequest> provideAdminUpdateRequests() {
            return Stream.of(
                    new AdminUserUpdateRequest("NewName", "NewSurname", LocalDate.of(1990, 1, 1)),
                    new AdminUserUpdateRequest("NewName", "NewSurname", null),
                    new AdminUserUpdateRequest("NewName", null, LocalDate.of(1995, 5, 20)),
                    new AdminUserUpdateRequest(null, "NewSurname", LocalDate.of(1995, 5, 20)),
                    new AdminUserUpdateRequest("NewName", null, null),
                    new AdminUserUpdateRequest(null, "NewSurname", null),
                    new AdminUserUpdateRequest(null, null, LocalDate.of(1995, 5, 20)),
                    new AdminUserUpdateRequest(null, null, null),
                    null);
        }

        @ParameterizedTest
        @MethodSource("provideAdminUpdateRequests")
        void shouldCallMapperAndReturnView(AdminUserUpdateRequest request) {
            UUID userId = UUID.randomUUID();
            User existingUser = Instancio.create(User.class);
            UserView expectedView = Instancio.create(UserView.class);

            given(userRepository.findById(userId)).willReturn(Optional.of(existingUser));
            given(userMapper.toViewWithoutCards(existingUser)).willReturn(expectedView);

            UserView result = userService.update(userId, request);

            assertThat(result).isEqualTo(expectedView);
            then(userMapper).should().update(request, existingUser);
        }

        @Test
        void shouldThrowResourceNotFoundException() {
            UUID userId = UUID.randomUUID();
            AdminUserUpdateRequest request = Instancio.create(AdminUserUpdateRequest.class);

            given(userRepository.findById(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.update(userId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage(String.format(USER_NOT_FOUND, userId));

            then(userMapper).shouldHaveNoInteractions();
        }
    }

    @Nested
    class DeleteTest {

        @BeforeEach
        void setUp() {
            ReflectionTestUtils.setField(userService, "blacklistPrefix", "banned_users");
            ReflectionTestUtils.setField(userService, "blacklistTtl", Duration.ofSeconds(300));
        }

        @Test
        void shouldRemoveUserAndNotifyGrpc() {
            UUID userId = UUID.randomUUID();
            User user = createActiveUser(userId);

            given(userRepository.findUserWithCardsById(userId)).willReturn(Optional.of(user));
            given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);

            userService.delete(userId);

            then(valueOperations).should().set("banned_users:" + userId, "true", Duration.ofSeconds(300));
            then(userRepository).should().delete(user);
            then(authGrpcClient).should().deleteUser(userId);
        }

        @Test
        void shouldThrowBadRequestWhenUserIsBlocked() {
            UUID userId = UUID.randomUUID();
            User user = createActiveUser(userId);
            user.setActive(false);

            given(userRepository.findUserWithCardsById(userId)).willReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.delete(userId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Action denied: User is blocked or deleted");

            then(userRepository).should(never()).delete(any(User.class));
            then(stringRedisTemplate).shouldHaveNoInteractions();
            then(authGrpcClient).shouldHaveNoInteractions();
        }

        @Test
        void shouldThrowBadRequestWhenUserIsDeleted() {
            UUID userId = UUID.randomUUID();
            User user = createActiveUser(userId);
            user.setDeleted(true);

            given(userRepository.findUserWithCardsById(userId)).willReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.delete(userId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Action denied: User is blocked or deleted");

            then(userRepository).should(never()).delete(any(User.class));
            then(stringRedisTemplate).shouldHaveNoInteractions();
            then(authGrpcClient).shouldHaveNoInteractions();
        }

        @Test
        void shouldThrowResourceNotFoundException() {
            UUID userId = UUID.randomUUID();

            given(userRepository.findUserWithCardsById(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.delete(userId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage(String.format(USER_NOT_FOUND, userId));

            then(userRepository).should(never()).delete(any(User.class));
            then(stringRedisTemplate).shouldHaveNoInteractions();
            then(authGrpcClient).shouldHaveNoInteractions();
        }
    }

    @Nested
    class DeactivateTest {

        @BeforeEach
        void setUp() {
            ReflectionTestUtils.setField(userService, "blacklistPrefix", "banned_users");
            ReflectionTestUtils.setField(userService, "blacklistTtl", Duration.ofSeconds(300));
        }

        @Test
        void shouldSetInactiveAndNotifyGrpc() {
            User user =
                    Instancio.of(User.class).set(field(User::getActive), true).create();
            UUID userId = user.getId();
            UserView expectedView = Instancio.create(UserView.class);

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(userMapper.toViewWithoutCards(user)).willReturn(expectedView);
            given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);

            UserView result = userService.deactivate(userId);

            assertThat(result).isEqualTo(expectedView);
            assertThat(user.getActive()).isFalse();
            assertThat(user.getUpdatedAt()).isNotNull();
            then(valueOperations).should().set("banned_users:" + userId, "true", Duration.ofSeconds(300));
            then(authGrpcClient).should().setStatus(userId, false);
        }

        @Test
        void shouldDoNothingWithGrpcWhenUserIsAlreadyInactive() {
            User user =
                    Instancio.of(User.class).set(field(User::getActive), false).create();
            UUID userId = user.getId();
            UserView expectedView = Instancio.create(UserView.class);

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(userMapper.toViewWithoutCards(user)).willReturn(expectedView);

            UserView result = userService.deactivate(userId);

            assertThat(result).isEqualTo(expectedView);
            assertThat(user.getActive()).isFalse();
            then(stringRedisTemplate).shouldHaveNoInteractions();
            then(authGrpcClient).should(never()).setStatus(any(), any(Boolean.class));
        }

        @Test
        void shouldThrowResourceNotFoundException() {
            UUID userId = UUID.randomUUID();

            given(userRepository.findById(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.deactivate(userId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage(String.format(USER_NOT_FOUND, userId));

            then(userMapper).shouldHaveNoInteractions();
            then(stringRedisTemplate).shouldHaveNoInteractions();
            then(authGrpcClient).shouldHaveNoInteractions();
        }
    }

    @Nested
    class ActivateTest {

        @BeforeEach
        void setUp() {
            ReflectionTestUtils.setField(userService, "blacklistPrefix", "banned_users");
        }

        @Test
        void shouldSetActiveAndNotifyGrpc() {
            User user =
                    Instancio.of(User.class).set(field(User::getActive), false).create();
            UUID userId = user.getId();
            UserView expectedView = Instancio.create(UserView.class);

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(userMapper.toViewWithoutCards(user)).willReturn(expectedView);

            UserView result = userService.activate(userId);

            assertThat(result).isEqualTo(expectedView);
            assertThat(user.getActive()).isTrue();
            assertThat(user.getUpdatedAt()).isNotNull();
            then(stringRedisTemplate).should().delete("banned_users:" + userId);
            then(authGrpcClient).should().setStatus(userId, true);
        }

        @Test
        void shouldDoNothingWithGrpcWhenUserIsAlreadyActive() {
            User user =
                    Instancio.of(User.class).set(field(User::getActive), true).create();
            UUID userId = user.getId();
            UserView expectedView = Instancio.create(UserView.class);

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(userMapper.toViewWithoutCards(user)).willReturn(expectedView);

            UserView result = userService.activate(userId);

            assertThat(result).isEqualTo(expectedView);
            assertThat(user.getActive()).isTrue();
            then(stringRedisTemplate).shouldHaveNoInteractions();
            then(authGrpcClient).should(never()).setStatus(any(), any(Boolean.class));
        }

        @Test
        void shouldThrowResourceNotFoundException() {
            UUID userId = UUID.randomUUID();

            given(userRepository.findById(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.activate(userId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage(String.format(USER_NOT_FOUND, userId));

            then(userMapper).shouldHaveNoInteractions();
            then(stringRedisTemplate).shouldHaveNoInteractions();
            then(authGrpcClient).shouldHaveNoInteractions();
        }
    }
}
