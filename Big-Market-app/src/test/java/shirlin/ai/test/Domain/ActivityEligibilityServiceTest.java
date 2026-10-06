package shirlin.ai.test.Domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import shirlin.ai.domain.Activity.adapter.repository.IActivityRepository;
import shirlin.ai.domain.Activity.model.entity.ActivityEntity;
import shirlin.ai.domain.Activity.service.ActivityEligibilityService;
import shirlin.ai.types.enums.ResponseCode;
import shirlin.ai.types.exception.AppException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ActivityEligibilityServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-06T04:00:00Z");

    private IActivityRepository repository;
    private ActivityEligibilityService service;

    @BeforeEach
    void setUp() {
        repository = mock(IActivityRepository.class);
        service = new ActivityEligibilityService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void nullActivityIdIsRejectedBeforeRepositoryAccess() {
        assertCode(ResponseCode.ILLEGAL_PARAMETER, () -> service.requireActive(null));
        verifyNoInteractions(repository);
    }

    @Test
    void missingAndDisabledActivitiesAreRejected() {
        when(repository.queryActivityById(1L)).thenReturn(null);
        assertCode(ResponseCode.ACTIVITY_NOT_EXISTS, () -> service.requireActive(1L));

        when(repository.queryActivityById(2L)).thenReturn(activity(2L, 0, null, null));
        assertCode(ResponseCode.ACTIVITY_NOT_EXISTS, () -> service.requireActive(2L));
    }

    @Test
    void exactStartAndEndBoundariesAreAllowed() {
        Date boundary = Date.from(NOW);
        ActivityEntity activity = activity(1L, 1, boundary, boundary);
        when(repository.queryActivityById(1L)).thenReturn(activity);

        assertSame(activity, service.requireActive(1L));
    }

    @Test
    void oneMillisecondBeforeStartIsRejected() {
        when(repository.queryActivityById(1L)).thenReturn(
                activity(1L, 1, Date.from(NOW.plusMillis(1)), null));

        assertCode(ResponseCode.STRATEGY_NOT_ACTIVE, () -> service.requireActive(1L));
    }

    @Test
    void oneMillisecondAfterEndIsRejected() {
        when(repository.queryActivityById(1L)).thenReturn(
                activity(1L, 1, null, Date.from(NOW.minusMillis(1))));

        assertCode(ResponseCode.ACTIVITY_EXPIRED, () -> service.requireActive(1L));
    }

    @Test
    void openEndedActiveActivityIsAllowed() {
        ActivityEntity activity = activity(1L, 1, null, null);
        when(repository.queryActivityById(1L)).thenReturn(activity);

        assertSame(activity, service.requireActive(1L));
    }

    private ActivityEntity activity(Long activityId, Integer status,
                                    Date beginTime, Date endTime) {
        return ActivityEntity.builder()
                .activityId(activityId)
                .strategyId(100001L)
                .status(status)
                .beginTime(beginTime)
                .endTime(endTime)
                .build();
    }

    private void assertCode(ResponseCode expected, Executable executable) {
        AppException exception = assertThrows(AppException.class, executable);
        assertEquals(expected.getCode(), exception.getCode());
    }
}
