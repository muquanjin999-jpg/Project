package game.card.effect;

import game.card.CardPlayContext;
import game.card.ability.Ability;
import game.card.ability.AbilityTrigger;
import game.model.Unit;

public interface OnHitEffect extends Ability {
    void onHitEnemyUnit(CardPlayContext ctx, String ownerId, Unit source, Unit enemyTarget, int damageDealt);

    @Override
    default AbilityTrigger trigger() {
        return AbilityTrigger.ON_HIT;
    }
}
