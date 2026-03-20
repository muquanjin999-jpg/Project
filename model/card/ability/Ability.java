package game.card.ability;

/**
 * Shared abstraction for unit/spell abilities so class relationships can be
 * expressed consistently in one model.
 */
public interface Ability {
    AbilityTrigger trigger();

    default String abilityName() {
        return getClass().getSimpleName();
    }
}
