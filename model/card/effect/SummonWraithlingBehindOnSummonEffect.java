package game.card.effect;

import game.card.CardPlayContext;
import game.card.UnitTemplate;
import game.model.Board;
import game.model.TilePos;
import game.model.Unit;

public class SummonWraithlingBehindOnSummonEffect implements OnSummonEffect {

    private final UnitTemplate wraithlingTemplate;

    public SummonWraithlingBehindOnSummonEffect(UnitTemplate wraithlingTemplate) {
        this.wraithlingTemplate = wraithlingTemplate;
    }

    @Override
    public void apply(CardPlayContext ctx, String ownerId, Unit summonedUnit) {
        if (summonedUnit == null) return;
        Board board = ctx.getState().getBoard();
        TilePos pos = summonedUnit.getPosition();

        int dx = ownerId.equals(game.model.GameState.P1) ? -1 : 1;
        TilePos behind = new TilePos(pos.getX() + dx, pos.getY());
        TokenSummonHelper.summonTokenIfEmpty(ctx, wraithlingTemplate, ownerId, behind);
    }
}
