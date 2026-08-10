package org.starset.deltaforcestrike.spectator;

/**
 * 玩家在对局里的角色：
 * <ul>
 *   <li>{@link #PLAYING}  — 参赛选手（占用 T/CT 名额，影响回合胜负）</li>
 *   <li>{@link #SPECTATOR} — 旁观者，占用房间总名额（ spectator + T + CT = max ）</li>
 *   <li>{@link #OBSERVER} — 导播，自由飞行观战，不占用任何名额</li>
 * </ul>
 * 默认值为 {@link #PLAYING}，兼容历史代码：{@code role == null} 视作 PLAYING。
 */
public enum SpectatorRole {
    PLAYING,
    SPECTATOR,
    OBSERVER
}
