package org.starset.deltaforcestrike.operator.skill;

public interface SkillHandler {
    String id();
    SkillResult execute(SkillContext ctx);
}
