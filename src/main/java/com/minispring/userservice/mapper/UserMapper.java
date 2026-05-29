package com.minispring.userservice.mapper;

import com.google.protobuf.Timestamp;
import com.minispring.grpc.service.UserDto;
import com.minispring.userservice.dto.AdminUserUpdateDto;
import com.minispring.userservice.dto.UserCreateDto;
import com.minispring.userservice.dto.UserProfileDto;
import com.minispring.userservice.dto.UserUpdateDto;
import com.minispring.userservice.model.User;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE,
        componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {

    User userCreateDtoToUser(UserCreateDto userCreateDto);

    UserUpdateDto userToUserUpdateDto(User user);

    AdminUserUpdateDto userToAdminUserUpdateDto(User user);

    UserProfileDto userToUserProfileDto(User user);

    @Mapping(target = "cards", ignore = true)
    UserProfileDto userToUserProfileDtoWithoutCards(User user);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateUserFromDto(UserUpdateDto dto, @MappingTarget User user);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateUserFromDto(AdminUserUpdateDto dto, @MappingTarget User user);

    @Mapping(target = "id", expression = "java(user.getId().toString())")
    @Mapping(target = "birthDate", expression = "java(user.getBirthDate() != null ? user.getBirthDate().toString() : \"\")")
    UserDto userToGrpcUserDto(User user);

    default Timestamp map(Instant instant) {
        if (instant == null) {
            return null;
        }

        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
