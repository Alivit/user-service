package com.minispring.userservice.mapper;

import com.minispring.userservice.dto.AdminUserUpdateDto;
import com.minispring.userservice.dto.UserCreateDto;
import com.minispring.userservice.dto.UserProfileDto;
import com.minispring.userservice.dto.UserUpdateDto;
import com.minispring.userservice.model.User;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE,
        componentModel = MappingConstants.ComponentModel.SPRING,
        builder = @Builder(disableBuilder = true))
public interface UserMapper {

    User userCreateDtoToUser(UserCreateDto userCreateDto);

    UserUpdateDto userToUserUpdateDto(User user);

    AdminUserUpdateDto userToAdminUserUpdateDto(User user);

    UserProfileDto userToUserProfileDto(User user);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateUserFromDto(UserUpdateDto dto, @MappingTarget User user);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateUserFromDto(AdminUserUpdateDto dto, @MappingTarget User user);
}
