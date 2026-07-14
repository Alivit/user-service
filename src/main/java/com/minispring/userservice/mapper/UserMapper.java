package com.minispring.userservice.mapper;

import com.minispring.userservice.dto.request.AdminUserUpdateRequest;
import com.minispring.userservice.dto.request.UserCreateRequest;
import com.minispring.userservice.dto.request.UserUpdateRequest;
import com.minispring.userservice.dto.response.UserView;
import com.minispring.userservice.model.User;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {

    User toEntity(UserCreateRequest request);

    UserView toView(User user);

    @Mapping(target = "cards", ignore = true)
    UserView toViewWithoutCards(User user);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "birthDate", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "cards", ignore = true)
    @Mapping(target = "version", ignore = true)
    void update(UserUpdateRequest request, @MappingTarget User user);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "cards", ignore = true)
    @Mapping(target = "version", ignore = true)
    void update(AdminUserUpdateRequest request, @MappingTarget User user);
}
