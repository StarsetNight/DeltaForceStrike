package org.starset.deltaforcestrike.operator.skill;

/** Passive hooks run at the existing application and end-of-tick phases, without active-skill costs. */
public interface PassiveSkillHandler {
    String id();

    /** Return true when this handler replaces the generic configured potion effects. */
    default boolean apply(SkillContext ctx) {
        return false;
    }

    default void tick(SkillContext ctx) {
    }
}
