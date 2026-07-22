package org.starset.deltaforcestrike.live;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 导播击杀滚动：主线程写入，快照只读。
 */
public final class LiveKillFeedService {

    public static final int MAX_ENTRIES = 16;
    /** 服务端保留略长于客户端显示（5s 静止 + 淡出），供晚订阅的 overlay 也能看到 */
    public static final long TTL_MS = 8_000L;

    private final ConcurrentLinkedDeque<Entry> entries = new ConcurrentLinkedDeque<>();
    private final AtomicLong seq = new AtomicLong(0);

    public void pushPlayerKill(String killer, String weapon, String victim,
                               String killerTeam, String victimTeam) {
        push(new Entry(
                seq.incrementAndGet(),
                System.currentTimeMillis(),
                "player",
                nullToEmpty(killer),
                nullToEmpty(weapon),
                nullToEmpty(victim),
                nullToEmpty(killerTeam),
                nullToEmpty(victimTeam)
        ));
    }

    public void pushBombKill(String victim, String victimTeam) {
        push(new Entry(
                seq.incrementAndGet(),
                System.currentTimeMillis(),
                "bomb",
                "",
                "改造TNT",
                nullToEmpty(victim),
                "",
                nullToEmpty(victimTeam)
        ));
    }

    public void pushWorldKill(String cause, String victim, String victimTeam) {
        push(new Entry(
                seq.incrementAndGet(),
                System.currentTimeMillis(),
                "world",
                "",
                nullToEmpty(cause),
                nullToEmpty(victim),
                "",
                nullToEmpty(victimTeam)
        ));
    }

    private void push(Entry e) {
        entries.addFirst(e);
        while (entries.size() > MAX_ENTRIES) {
            entries.pollLast();
        }
        purgeExpired();
    }

    public void clear() {
        entries.clear();
    }

    /** 最新在前 */
    public List<Entry> snapshot() {
        purgeExpired();
        return new ArrayList<>(entries);
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        entries.removeIf(e -> now - e.time > TTL_MS);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    public record Entry(
            long id,
            long time,
            String type,
            String killer,
            String weapon,
            String victim,
            String killerTeam,
            String victimTeam
    ) {}
}
