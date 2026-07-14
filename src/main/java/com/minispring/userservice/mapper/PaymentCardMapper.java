package com.minispring.userservice.mapper;

import com.minispring.userservice.dto.request.PaymentCardCreateRequest;
import com.minispring.userservice.dto.request.PaymentCardUpdateRequest;
import com.minispring.userservice.dto.response.PaymentCardView;
import com.minispring.userservice.model.PaymentCard;
import com.minispring.userservice.util.CardSecurityUtils;
import java.util.Locale;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        componentModel = MappingConstants.ComponentModel.SPRING,
        uses = CardSecurityUtils.class)
public interface PaymentCardMapper {

    @Mapping(target = "holder", qualifiedByName = "UpperCaseModifier")
    @Mapping(target = "number", source = "number", qualifiedByName = "MaskCard")
    @Mapping(target = "cardHash", source = "number", qualifiedByName = "HashCard")
    PaymentCard toEntity(PaymentCardCreateRequest request);

    PaymentCardView toView(PaymentCard card);

    @Mapping(target = "user", ignore = true)
    PaymentCardView toViewWithoutUser(PaymentCard card);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "holder", source = "holder", qualifiedByName = "UpperCaseModifier")
    @Mapping(target = "number", source = "number", qualifiedByName = "MaskCard")
    @Mapping(target = "cardHash", source = "number", qualifiedByName = "HashCard")
    void update(PaymentCardUpdateRequest dto, @MappingTarget PaymentCard paymentCard);

    @Named("UpperCaseModifier")
    default String toUpperCase(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ENGLISH);
    }
}
