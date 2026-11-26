package com.triagedeck.audit;

import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * 内存中的采集器状态，对外暴露采集是否成功、是否禁用、最后运行时间。
 * 供审计列表接口 #6 返回 `eventsCollected/eventsDisabled/collectorLastRunAt`。
 */
@Component
public class CollectorState {

    private boolean eventsCollected = false;
    private boolean eventsDisabled = false;
    private Instant collectorLastRunAt = null;
    private int lastTwoCounts[] = new int[2]; // 最近两轮新增数，用于判断连续空轮
    private int countIndex = 0;

    public boolean isEventsCollected() {
        return eventsCollected;
    }

    public void setEventsCollected(boolean eventsCollected) {
        this.eventsCollected = eventsCollected;
    }

    public boolean isEventsDisabled() {
        return eventsDisabled;
    }

    public void setEventsDisabled(boolean eventsDisabled) {
        this.eventsDisabled = eventsDisabled;
    }

    public Instant getCollectorLastRunAt() {
        return collectorLastRunAt;
    }

    public void setCollectorLastRunAt(Instant collectorLastRunAt) {
        this.collectorLastRunAt = collectorLastRunAt;
    }

    /**
     * 记录本轮新增数，保留最近两轮。连续两轮 0 新增 → eventsCollected=false。
     */
    public void recordRound(int newCount) {
        lastTwoCounts[countIndex % 2] = newCount;
        countIndex++;
        eventsCollected = newCount > 0;
        if (countIndex >= 2 && lastTwoCounts[0] == 0 && lastTwoCounts[1] == 0) {
            eventsCollected = false;
        }
    }

    public int getLastRoundCount() {
        return lastTwoCounts[(countIndex - 1 + 2) % 2];
    }
}