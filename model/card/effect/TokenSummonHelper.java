package game.card.effect;

import game.card.CardPlayContext;
import game.card.UnitTemplate;
import game.model.Board;
import game.model.TilePos;
import game.model.Unit;

final class TokenSummonHelper {
    private TokenSummonHelper() {}

    static Unit summonTokenIfEmpty(
            CardPlayContext ctx,
            UnitTemplate tokenTemplate,
            String ownerId,
            TilePos target
    ) {
        Board board = ctx.getState().getBoard();
        if (target == null || !board.isInside(target) || !board.isEmpty(target)) {
            return null;
        }

        Unit summoned = tokenTemplate.create(ctx.getIdGenerator().next("U"), ownerId, target);
        summoned.markSummonedThisTurn();
        board.placeUnit(summoned, target);
        return summoned;
    }
}
