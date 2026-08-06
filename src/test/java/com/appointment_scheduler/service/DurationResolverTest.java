package com.appointment_scheduler.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.appointment_scheduler.domain.ServiceType;
import com.appointment_scheduler.domain.ServiceTypeDurationOverride;
import com.appointment_scheduler.domain.ServiceTypeDurationOverrideId;
import com.appointment_scheduler.domain.VehicleCategory;
import com.appointment_scheduler.repository.ServiceTypeDurationOverrideRepository;
import com.appointment_scheduler.repository.ServiceTypeRepository;
import com.appointment_scheduler.service.exception.ResourceNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DurationResolverTest {

    @Mock private ServiceTypeRepository serviceTypeRepository;
    @Mock private ServiceTypeDurationOverrideRepository overrideRepository;

    private DurationResolver resolver;

    @Test
    void usesOverrideWhenOneExistsForVehicleCategory() {
        resolver = new DurationResolver(serviceTypeRepository, overrideRepository);
        var id = new ServiceTypeDurationOverrideId(1L, VehicleCategory.TRUCK);
        var override = ServiceTypeDurationOverride.builder().id(id).durationMinutes(90).build();
        when(overrideRepository.findById(id)).thenReturn(Optional.of(override));

        int result = resolver.resolveDurationMinutes(1L, VehicleCategory.TRUCK);

        assertThat(result).isEqualTo(90);
    }

    @Test
    void fallsBackToBaseDurationWhenNoOverrideExists() {
        resolver = new DurationResolver(serviceTypeRepository, overrideRepository);
        var id = new ServiceTypeDurationOverrideId(1L, VehicleCategory.SEDAN);
        when(overrideRepository.findById(id)).thenReturn(Optional.empty());
        when(serviceTypeRepository.findById(1L))
                .thenReturn(Optional.of(ServiceType.builder().id(1L).durationMinutes(30).build()));

        int result = resolver.resolveDurationMinutes(1L, VehicleCategory.SEDAN);

        assertThat(result).isEqualTo(30);
    }

    @Test
    void fallsBackToBaseDurationWhenVehicleCategoryIsNull() {
        resolver = new DurationResolver(serviceTypeRepository, overrideRepository);
        when(serviceTypeRepository.findById(1L))
                .thenReturn(Optional.of(ServiceType.builder().id(1L).durationMinutes(45).build()));

        int result = resolver.resolveDurationMinutes(1L, null);

        assertThat(result).isEqualTo(45);
    }

    @Test
    void throwsWhenServiceTypeDoesNotExist() {
        resolver = new DurationResolver(serviceTypeRepository, overrideRepository);
        when(serviceTypeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolveDurationMinutes(99L, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
