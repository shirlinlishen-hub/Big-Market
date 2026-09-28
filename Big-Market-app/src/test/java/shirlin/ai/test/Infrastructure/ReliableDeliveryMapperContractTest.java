package shirlin.ai.test.Infrastructure;

import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReliableDeliveryMapperContractTest {

    @Test
    void reliableDeliveryMappersAreWellFormedAndEnforceConcurrencyContracts() throws Exception {
        List<String> resources = List.of(
                "mybatis/mapper/OutboxEventMapper.xml",
                "mybatis/mapper/InboxEventMapper.xml",
                "mybatis/mapper/UserPointsMapper.xml",
                "mybatis/mapper/AwardDeliveryTaskMapper.xml");
        for (String resource : resources) {
            assertNotNull(resourceStream(resource), resource + " must exist");
            parse(resource);
        }

        String outbox = normalized("mybatis/mapper/OutboxEventMapper.xml");
        String inbox = normalized("mybatis/mapper/InboxEventMapper.xml");
        String points = normalized("mybatis/mapper/UserPointsMapper.xml");
        String tasks = normalized("mybatis/mapper/AwardDeliveryTaskMapper.xml");

        assertAll(
                () -> assertTrue(outbox.contains("event_type = 'award_delivery_requested'")),
                () -> assertTrue(outbox.contains("for update skip locked")),
                () -> assertTrue(outbox.contains("event_id = #{eventid}")),
                () -> assertTrue(outbox.contains("status = 0")),
                () -> assertTrue(outbox.contains("lock_token = #{locktoken}")),
                () -> assertTrue(inbox.contains("duplicate_count = duplicate_count + 1")),
                () -> assertTrue(inbox.contains("where consumer_name = #{consumername} and event_id = #{eventid}")),
                () -> assertFalse(points.contains("insert ignore")),
                () -> assertTrue(points.contains("id=\"selectledgerforupdate\"")),
                () -> assertTrue(tasks.contains("column=\"dispatch_version\" property=\"dispatchversion\""))
        );
    }

    @Test
    void daoAndPersistenceObjectsExposeReliableDeliveryFields() throws Exception {
        Class<?> outboxDao = Class.forName("shirlin.ai.infrastructure.dao.IOutboxEventDao");
        Class<?> inboxDao = Class.forName("shirlin.ai.infrastructure.dao.IInboxEventDao");
        Class<?> pointsDao = Class.forName("shirlin.ai.infrastructure.dao.IUserPointsDao");

        assertMethod(outboxDao, "selectClaimable", Integer.class);
        assertMethod(outboxDao, "acquireLease", String.class, String.class, String.class, Date.class);
        assertMethod(outboxDao, "markPublished", String.class, String.class);
        assertMethod(outboxDao, "recordPublishFailure", String.class, String.class, String.class);
        assertMethod(inboxDao, "insertOrTouch", Class.forName("shirlin.ai.infrastructure.dao.po.InboxEvent"));
        assertMethod(inboxDao, "selectForUpdate", String.class, String.class);
        assertMethod(inboxDao, "markSuccess", String.class, String.class);
        assertMethod(pointsDao, "selectLedgerForUpdate", String.class);

        assertField("shirlin.ai.infrastructure.dao.po.OutboxEvent", "createTime");
        assertField("shirlin.ai.infrastructure.dao.po.OutboxEvent", "lockToken");
        assertField("shirlin.ai.infrastructure.dao.po.AwardDeliveryTask", "dispatchVersion");
    }

    private static void assertMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        assertDoesNotThrow(() -> {
            Method ignored = type.getDeclaredMethod(name, parameterTypes);
        }, type.getSimpleName() + "." + name + " must exist");
    }

    private static void assertField(String className, String fieldName) {
        assertDoesNotThrow(() -> Class.forName(className).getDeclaredField(fieldName),
                className + "." + fieldName + " must exist");
    }

    private static void parse(String resource) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        try (InputStream input = resourceStream(resource)) {
            factory.newDocumentBuilder().parse(input);
        }
    }

    private static String normalized(String resource) throws Exception {
        try (InputStream input = resourceStream(resource)) {
            assertNotNull(input, resource + " must exist");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .toLowerCase().replaceAll("\\s+", " ");
        }
    }

    private static InputStream resourceStream(String resource) {
        return ReliableDeliveryMapperContractTest.class.getClassLoader().getResourceAsStream(resource);
    }
}
