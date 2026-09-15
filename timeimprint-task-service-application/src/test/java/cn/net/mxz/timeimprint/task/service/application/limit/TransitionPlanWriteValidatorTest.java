package cn.net.mxz.timeimprint.task.service.application.limit;

import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.ActionJobIntent;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionPlan;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionResourceType;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionTarget;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransitionPlanWriteValidatorTest {

    @Test
    void estimateMutatedRowsCountsNotificationSideEffect() {
        TransitionPlan plan = new TransitionPlan(
                new TransitionTarget(TransitionResourceType.INSTANCE, 1L, 0L),
                null,
                null,
                List.of(),
                List.of(new ActionJobIntent(
                        "in_app_notification",
                        1,
                        "INITIAL:abc",
                        "LOCAL_TRANSACTIONAL",
                        "USER",
                        "u1",
                        Instant.EPOCH,
                        null,
                        null)),
                List.of(),
                List.of(),
                List.of(),
                "test");
        int rows = TransitionPlanWriteValidator.estimateMutatedRows(plan);
        assertTrue(rows >= 3, "action + notification + transition estimate");
    }
}
