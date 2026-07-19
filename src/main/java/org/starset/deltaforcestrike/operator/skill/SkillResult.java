package org.starset.deltaforcestrike.operator.skill;

public record SkillResult(boolean success, String message) {
    public static SkillResult ok() {
        return new SkillResult(true, null);
    }
    public static SkillResult ok(String msg) {
        return new SkillResult(true, msg);
    }
    public static SkillResult fail(String msg) {
        return new SkillResult(false, msg);
    }
}
