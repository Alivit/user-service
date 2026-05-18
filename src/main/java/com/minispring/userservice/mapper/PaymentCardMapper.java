package com.minispring.userservice.mapper;

import com.minispring.userservice.dto.PaymentCardCreateDto;
import com.minispring.userservice.dto.PaymentCardProfileDto;
import com.minispring.userservice.dto.PaymentCardUpdateDto;
import com.minispring.userservice.model.PaymentCard;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.Locale;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE,
        componentModel = MappingConstants.ComponentModel.SPRING,
        builder = @Builder(disableBuilder = true))
public interface PaymentCardMapper {

    @Mapping(target = "holder", expression = "java(toUpperCase(paymentCard.holder()))")
    @Mapping(target = "number", expression = "java(toCleanCardNumber(paymentCard.number()))")
    PaymentCard paymentCardCreateDtoToPaymentCard(PaymentCardCreateDto paymentCard);

    PaymentCardProfileDto paymentCardToPaymentCardProfileDto(PaymentCard paymentCard);

    PaymentCardUpdateDto paymentCardToPaymentCardUpdateDto(PaymentCard paymentCard);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "holder", source = "holder", qualifiedByName = "toUpperCaseModifier")
    @Mapping(target = "number", ignore = true)
    void updateCardFromDto(PaymentCardUpdateDto dto, @MappingTarget PaymentCard paymentCard);

    @Named("toUpperCaseModifier")
    default String toUpperCase(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ENGLISH);
    }

    default String toCleanCardNumber(String cardNumber) {
        return cardNumber == null ? null : cardNumber.replaceAll("\\D", "");
    }
}
