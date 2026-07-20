package org.starset.deltaforcestrike.operator.skill;

import org.starset.deltaforcestrike.operator.skill.impl.*;

import java.util.HashMap;
import java.util.Map;

public final class SkillHandlerRegistry {

    private final Map<String, SkillHandler> handlers = new HashMap<>();

    public SkillHandlerRegistry() {
        register(new DashHandler());
        register(new EnderPearlHandler()); // 兼容旧配置 handler: ender_pearl → 转发到 dash
        register(new EmergencyBeaconHandler());
        register(new NikoBerserkHandler());
        register(new SplashHarmingHandler());
        register(new LingeringSlownessHandler());
        register(new PrimedTntHandler());
        register(new SplashRegenHandler());
        register(new RoundSmokeHandler());
        register(new SummonVexHandler());
        register(new ReconArrowHandler());
        register(new WindChargeHandler());
        register(new GlobalSilenceHandler());
        register(new NoopHandler());
    }

    public void register(SkillHandler handler) {
        handlers.put(handler.id().toLowerCase(), handler);
    }

    public SkillHandler get(String id) {
        if (id == null) return handlers.get("none");
        return handlers.getOrDefault(id.toLowerCase(), handlers.get("none"));
    }
}
