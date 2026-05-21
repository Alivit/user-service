package com.minispring.userservice.service.impl;

import ch.qos.logback.classic.Logger;
import com.minispring.userservice.dto.AdminUserUpdateDto;
import com.minispring.userservice.dto.UserCreateDto;
import com.minispring.userservice.dto.UserParamsDto;
import com.minispring.userservice.dto.UserProfileDto;
import com.minispring.userservice.dto.UserUpdateDto;
import com.minispring.userservice.exception.ResourceAlreadyExistsException;
import com.minispring.userservice.exception.ResourceNotFoundException;
import com.minispring.userservice.mapper.UserMapper;
import com.minispring.userservice.model.User;
import com.minispring.userservice.repository.UserRepository;
import org.instancio.Instancio;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.minispring.userservice.exception.ExceptionAnswer.EMAIL_EXIST;
import static com.minispring.userservice.exception.ExceptionAnswer.USER_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Spy
    private final Javers javers = JaversBuilder.javers().build();

    @InjectMocks
    private UserServiceImpl userService;

    @Nested
    class CreateTest {

        private UserCreateDto createDto;

        @BeforeEach
        public void init() {
            createDto = Instancio.create(UserCreateDto.class);
        }

        @Test
        void createShouldReturnUserProfileDto() {
            User userBeforeSaving = Instancio.create(User.class);
            User savedUser = Instancio.create(User.class);
            UUID userId = savedUser.getId();
            UserProfileDto expectedDto = Instancio.of(UserProfileDto.class)
                    .set(field(UserProfileDto::id), userId)
                    .create();

            given(userRepository.existsByEmail(createDto.email())).willReturn(false);
            given(userMapper.userCreateDtoToUser(createDto)).willReturn(userBeforeSaving);
            given(userRepository.saveAndFlush(userBeforeSaving)).willReturn(savedUser);
            given(userMapper.userToUserProfileDto(savedUser)).willReturn(expectedDto);

            UserProfileDto result = userService.create(createDto);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
            verify(userRepository).saveAndFlush(userBeforeSaving);
        }

        @Test
        void createShouldThrowResourceAlreadyExistsExceptionWhenEmailExists() {
            given(userRepository.existsByEmail(createDto.email())).willReturn(true);

            assertThatThrownBy(() -> userService.create(createDto))
                    .isInstanceOf(ResourceAlreadyExistsException.class)
                    .hasMessage(String.format(EMAIL_EXIST, createDto.email()));

            verifyNoInteractions(userMapper);
            verify(userRepository, never()).saveAndFlush(any());
        }

        @Test
        void createShouldThrowDataIntegrityViolationExceptionOnSave() {
            User userBeforeSaving = Instancio.create(User.class);

            given(userRepository.existsByEmail(createDto.email())).willReturn(false);
            given(userMapper.userCreateDtoToUser(createDto)).willReturn(userBeforeSaving);
            given(userRepository.saveAndFlush(userBeforeSaving))
                    .willThrow(new DataIntegrityViolationException("Could not execute statement"));

            assertThatThrownBy(() -> userService.create(createDto))
                    .isInstanceOf(DataIntegrityViolationException.class);

            verify(userMapper, never()).userToUserProfileDto(any());
        }
    }

    @Nested
    class GetByIdTest {

        @Test
        void getByIdShouldReturnUserProfileDto() {
            User user = Instancio.create(User.class);
            UUID userId = user.getId();
            UserProfileDto expectedDto = Instancio.of(UserProfileDto.class)
                    .set(field(UserProfileDto::id), userId)
                    .create();

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(userMapper.userToUserProfileDto(user)).willReturn(expectedDto);

            UserProfileDto result = userService.getById(userId);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
        }

        @Test
        void getByIdShouldThrowResourceNotFoundException() {
            UUID userId = Instancio.create(UUID.class);

            given(userRepository.findById(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getById(userId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage(String.format(USER_NOT_FOUND, userId));

            verifyNoInteractions(userMapper);
        }
    }

    @Nested
    class GetAllByTest {

        private Pageable pageable;
        private List<UserProfileDto> expectedList;
        private List<User> users;
        Page<User> usersPage;

        @BeforeEach
        public void init() {
            pageable = PageRequest.of(0, 10);
            expectedList = Instancio.ofList(UserProfileDto.class).size(2).create();
            users = Instancio.ofList(User.class).size(2).create();
            usersPage = new PageImpl<>(users, pageable, users.size());
        }

        @Test
        void getAllByShouldReturnPageOfUserProfileDtoByName() {
            UserParamsDto userParams = Instancio.ofBlank(UserParamsDto.class)
                    .set(field(UserParamsDto::name), "TestName")
                    .create();

            given(userRepository.findByParams(userParams, pageable)).willReturn(usersPage);
            given(userMapper.userToUserProfileDto(users.get(0))).willReturn(expectedList.get(0));
            given(userMapper.userToUserProfileDto(users.get(1))).willReturn(expectedList.get(1));

            Page<UserProfileDto> result = userService.getAllBy(userParams, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent()).containsExactlyElementsOf(expectedList);
        }

        @Test
        void getAllByShouldReturnPageOfUserProfileDtoBySurname() {
            UserParamsDto userParams = Instancio.ofBlank(UserParamsDto.class)
                    .set(field(UserParamsDto::surname), "TestSurname")
                    .create();

            given(userRepository.findByParams(userParams, pageable)).willReturn(usersPage);
            given(userMapper.userToUserProfileDto(users.get(0))).willReturn(expectedList.get(0));
            given(userMapper.userToUserProfileDto(users.get(1))).willReturn(expectedList.get(1));

            Page<UserProfileDto> result = userService.getAllBy(userParams, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent()).containsExactlyElementsOf(expectedList);
        }

        @Test
        void getAllByShouldReturnPageOfUserProfileDto() {
            UserParamsDto userParams = Instancio.ofBlank(UserParamsDto.class).create();

            given(userRepository.findByParams(userParams, pageable)).willReturn(usersPage);
            given(userMapper.userToUserProfileDto(users.get(0))).willReturn(expectedList.get(0));
            given(userMapper.userToUserProfileDto(users.get(1))).willReturn(expectedList.get(1));

            Page<UserProfileDto> result = userService.getAllBy(userParams, pageable);

            assertThat(result).isNotNull().isNotEmpty();
            assertThat(result.getContent()).containsExactlyElementsOf(expectedList);
        }

        @Test
        void getAllByShouldReturnEmptyPage() {
            UserParamsDto userParams = Instancio.ofBlank(UserParamsDto.class)
                    .set(field(UserParamsDto::name), "")
                    .create();
            Page<User> emptyPage = Page.empty(pageable);

            given(userRepository.findByParams(userParams, pageable)).willReturn(emptyPage);

            Page<UserProfileDto> result = userService.getAllBy(userParams, pageable);

            assertThat(result).isNotNull().isEmpty();
            assertThat(result.getTotalElements()).isZero();
            verifyNoInteractions(userMapper);
        }
    }

    @Nested
    @ExtendWith(OutputCaptureExtension.class)
    class UserUpdateTest {

        private UUID userId;
        private User existingUser;
        private UserProfileDto expectedDto;
        private UserUpdateDto userStateBefore;

        @BeforeEach
        void init() {
            Logger logger = (Logger) LoggerFactory.getLogger(UserServiceImpl.class);
            logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
            existingUser = Instancio.create(User.class);
            userId = existingUser.getId();
            userStateBefore = Instancio.create(UserUpdateDto.class);
            expectedDto = Instancio.of(UserProfileDto.class)
                    .set(field(UserProfileDto::id), userId)
                    .create();
        }

        @Test
        void updateShouldReturnUserProfileDtoWithUpdatedName(CapturedOutput output) {
            String testName = "TestName";
            UserUpdateDto updateDto = Instancio.of(UserUpdateDto.class)
                    .set(field(UserUpdateDto::name), testName)
                    .create();

            setupForUpdateUser(updateDto);

            UserProfileDto result = userService.update(userId, updateDto);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
            assertThat(output.getOut())
                    .contains("have changes:")
                    .contains("- 'name' changed:")
                    .contains("-> '" + testName + "'");
            verify(userMapper).updateUserFromDto(updateDto, existingUser);
            verify(userRepository).flush();
        }

        @Test
        void updateShouldReturnUserProfileDtoWithUpdatedSurname(CapturedOutput output) {
            String testSurname = "TestSurname";
            UserUpdateDto updateDto = Instancio.of(UserUpdateDto.class)
                    .set(field(UserUpdateDto::surname), testSurname)
                    .create();

            setupForUpdateUser(updateDto);

            UserProfileDto result = userService.update(userId, updateDto);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
            assertThat(output.getOut())
                    .contains("have changes:")
                    .contains("- 'surname' changed:")
                    .contains("-> '" + testSurname + "'");
            verify(userMapper).updateUserFromDto(updateDto, existingUser);
            verify(userRepository).flush();
        }

        @Test
        void updateShouldReturnUserProfileDtoHasNoChanges(CapturedOutput output) {
            UserUpdateDto updateDto = Instancio.create(UserUpdateDto.class);

            setupForUpdateUser(userStateBefore);

            UserProfileDto result = userService.update(userId, updateDto);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
            assertThat(output.getOut()).doesNotContain("have changes:");
            verify(userMapper).updateUserFromDto(updateDto, existingUser);
            verify(userRepository).flush();
        }

        @Test
        void updateShouldThrowResourceNotFoundException() {
            UserUpdateDto updateDto = Instancio.create(UserUpdateDto.class);

            given(userRepository.findById(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.update(userId, updateDto))
                    .isInstanceOf(ResourceNotFoundException.class);

            verifyNoInteractions(javers);
            verify(userRepository, never()).flush();
        }

        private void setupForUpdateUser(UserUpdateDto userStateAfter) {
            given(userRepository.findById(userId)).willReturn(Optional.of(existingUser));
            given(userMapper.userToUserUpdateDto(existingUser)).willReturn(userStateBefore, userStateAfter);
            given(userMapper.userToUserProfileDto(existingUser)).willReturn(expectedDto);
        }
    }

    @Nested
    @ExtendWith(OutputCaptureExtension.class)
    class AdminUpdateTest {

        private UUID userId;
        private User existingUser;
        private UserProfileDto expectedDto;
        private AdminUserUpdateDto userStateBefore;

        @BeforeEach
        void init() {
            Logger logger = (Logger) LoggerFactory.getLogger(UserServiceImpl.class);
            logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
            existingUser = Instancio.create(User.class);
            userId = existingUser.getId();
            userStateBefore = Instancio.of(AdminUserUpdateDto.class)
                    .set(field(AdminUserUpdateDto::active), true)
                    .create();
            expectedDto = Instancio.of(UserProfileDto.class)
                    .set(field(UserProfileDto::id), userId)
                    .create();
        }

        private void setupMocks(AdminUserUpdateDto userStateAfter) {
            given(userRepository.findById(userId)).willReturn(Optional.of(existingUser));
            given(userMapper.userToAdminUserUpdateDto(existingUser)).willReturn(userStateBefore, userStateAfter);
            given(userMapper.userToUserProfileDto(existingUser)).willReturn(expectedDto);
        }

        @Test
        void updateShouldReturnUserProfileDtoWithUpdatedName(CapturedOutput output) {
            String testName = "TestName";
            AdminUserUpdateDto updateDto = Instancio.of(AdminUserUpdateDto.class)
                    .set(field(AdminUserUpdateDto::name), testName)
                    .create();

            setupMocks(updateDto);

            UserProfileDto result = userService.update(userId, updateDto);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
            assertThat(output.getOut())
                    .contains("have changes:")
                    .contains("- 'name' changed:")
                    .contains("-> '" + testName + "'");
            verify(userMapper).updateUserFromDto(updateDto, existingUser);
            verify(userRepository).flush();
        }

        @Test
        void updateShouldReturnUserProfileDtoWithUpdatedSurname(CapturedOutput output) {
            String testSurname = "TestSurname";
            AdminUserUpdateDto updateDto = Instancio.of(AdminUserUpdateDto.class)
                    .set(field(AdminUserUpdateDto::surname), testSurname)
                    .create();

            setupMocks(updateDto);

            UserProfileDto result = userService.update(userId, updateDto);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
            assertThat(output.getOut())
                    .contains("have changes:")
                    .contains("- 'surname' changed:")
                    .contains("-> '" + testSurname + "'");
            verify(userMapper).updateUserFromDto(updateDto, existingUser);
            verify(userRepository).flush();
        }

        @Test
        void updateShouldReturnUserProfileDtoWithUpdatedBirthDate(CapturedOutput output) {
            LocalDate testBirthDate = LocalDate.EPOCH;
            AdminUserUpdateDto updateDto = Instancio.of(AdminUserUpdateDto.class)
                    .set(field(AdminUserUpdateDto::birthDate), testBirthDate)
                    .create();

            setupMocks(updateDto);
            UserProfileDto result = userService.update(userId, updateDto);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
            assertThat(output.getOut())
                    .contains("have changes:")
                    .contains("- 'birthDate' changed:");
            verify(userRepository).flush();
        }

        @Test
        void updateShouldReturnUserProfileDtoWithUpdatedActiveStatus(CapturedOutput output) {
            boolean testActive = false;
            AdminUserUpdateDto updateDto = Instancio.of(AdminUserUpdateDto.class)
                    .set(field(AdminUserUpdateDto::active), testActive)
                    .create();

            setupMocks(updateDto);

            UserProfileDto result = userService.update(userId, updateDto);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
            assertThat(output.getOut())
                    .contains("have changes:")
                    .contains("- 'active' changed:")
                    .contains("-> '" + testActive + "'");
            verify(userRepository).flush();
        }

        @Test
        void updateShouldReturnUserProfileDtoAndNotLogWhenNoChangesDetected(CapturedOutput output) {
            AdminUserUpdateDto updateDto = Instancio.create(AdminUserUpdateDto.class);

            setupMocks(userStateBefore);

            UserProfileDto result = userService.update(userId, updateDto);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
            assertThat(output.getOut()).doesNotContain("have changes:");
            verify(userRepository).flush();
        }

        @Test
        void updateShouldThrowResourceNotFoundException() {
            AdminUserUpdateDto updateDto = Instancio.create(AdminUserUpdateDto.class);

            given(userRepository.findById(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.update(userId, updateDto))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(userRepository, never()).flush();
        }
    }

    @Nested
    class DeactivateTest {

        @Test
        void deactivateShouldReturnUserProfileDtoWithActiveFalseWhenUserExists() {
            User existingUser = Instancio.of(User.class)
                    .set(field(User::getActive), true)
                    .create();
            UUID userId = existingUser.getId();
            UserProfileDto expectedDto = Instancio.of(UserProfileDto.class)
                    .set(field(UserProfileDto::id), userId)
                    .set(field(UserProfileDto::active), false)
                    .create();

            given(userRepository.findById(userId)).willReturn(Optional.of(existingUser));
            given(userMapper.userToUserProfileDto(existingUser)).willReturn(expectedDto);

            UserProfileDto result = userService.deactivate(userId);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
            assertThat(result.active()).isFalse();
            assertThat(existingUser.getActive()).isFalse();
            verify(userRepository).flush();
        }

        @Test
        void deactivateShouldThrowResourceNotFoundExceptionWhenUserDoesNotExist() {
            UUID userId = Instancio.create(UUID.class);

            given(userRepository.findById(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.deactivate(userId))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(userRepository, never()).flush();
            verifyNoInteractions(userMapper);
        }
    }

    @Nested
    class ActivateTest {

        @Test
        void activateShouldReturnUserProfileDtoWithActiveTrueWhenUserExists() {
            User existingUser = Instancio.of(User.class)
                    .set(field(User::getActive), false)
                    .create();
            UUID userId = existingUser.getId();
            UserProfileDto expectedDto = Instancio.of(UserProfileDto.class)
                    .set(field(UserProfileDto::id), userId)
                    .set(field(UserProfileDto::active), true)
                    .create();

            given(userRepository.findById(userId)).willReturn(Optional.of(existingUser));
            given(userMapper.userToUserProfileDto(existingUser)).willReturn(expectedDto);

            UserProfileDto result = userService.activate(userId);

            assertThat(result).isNotNull().isEqualTo(expectedDto);
            assertThat(result.active()).isTrue();
            assertThat(existingUser.getActive()).isTrue();
            verify(userRepository).flush();
        }

        @Test
        void activateShouldThrowResourceNotFoundExceptionWhenUserDoesNotExist() {
            UUID userId = Instancio.create(UUID.class);

            given(userRepository.findById(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.activate(userId))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(userRepository, never()).flush();
            verifyNoInteractions(userMapper);
        }
    }
}