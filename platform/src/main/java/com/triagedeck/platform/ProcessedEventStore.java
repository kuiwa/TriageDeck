package com.triagedeck.platform.kafka;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka 消费幂等台账（02-design §5.4）。
 * 主键 `(consumer_group, topic, partition_no, record_offset)`；插入冲突即重复。
 *
 * <p>**不自己开事务**：必须由消费者 service 的事务方法调用，保证「标记 + 业务写入」一起提交/回滚。
 */
@Component
public class ProcessedEventStore {

    private static final String TRY_MARK_SQL = """
            insert into processed_event (consumer_group, topic, partition_no, record_offset, processed_at)
            values (:group, :topic, :partition, :offset, now())
            on conflict (consumer_group, topic, partition_no, record_offset) do nothing
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ProcessedEventStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @return {@code true} 首次处理；{@code false} 该位点已处理过（调用方直接 ack 跳过）
     */
    public boolean tryMark(String group, String topic, int partitionNo, long offset) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("group", group)
                .addValue("topic", topic)
                .addValue("partition", partitionNo)
                .addValue("offset", offset);
        return jdbc.update(TRY_MARK_SQL, parameters) > 0;
    }
}
