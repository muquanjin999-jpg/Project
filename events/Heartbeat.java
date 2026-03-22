package events;

import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import structures.GameState;

/**
 * Heartbeat event, fired roughly once per second by the UI loop.
 *
 * Drives the AI turn-loop:
 * P1 EndTurn -> aiTurnActive=true -> Heartbeat steps AI actions ->
 * (AnimationGate.lock(tag) ... AnimationEnded(tag) unlocks) -> AI EndTurn -> back to P1
 *
 * @author Dr. Richard McCreadie
 */
public class Heartbeat implements EventProcessor {

	@Override
	public void processEvent(ActorRef out, GameState gameState, JsonNode message) {

		if (gameState == null) return;
		if (!gameState.gameInitalised) return;
		if (gameState.domainGameManager == null || gameState.domainState == null) return;
		if (gameState.aiController == null) return;
		if (gameState.domainState.isGameOver()) {
			game.ui.TemplateCommandDispatcher.renderPlayerStats(out, gameState.domainState);
			events.EventMessages.showGameResultIfOver(out, gameState);
			return;
		}
		
        // ==========================================================
        // Initial render release: wait one heartbeat so UI can create
        // unit sprites / labels before allowing interaction
        // ==========================================================
        if (gameState.initialRenderPendingUnlock) {
            game.ui.TemplateCommandDispatcher.flushPendingUnitStats(out, gameState);
            game.ui.TemplateCommandDispatcher.renderPlayerStats(out, gameState.domainState);
            game.ui.TemplateCommandDispatcher.renderHand(out, gameState, gameState.domainState, game.model.GameState.P1);

            gameState.initialRenderPendingUnlock = false;
            gameState.inputLocked = false;
            gameState.animationLockTicks = 0;

            if (gameState.animationGate != null) {
                gameState.animationGate.unlock("initial_render");
            }
            return;
        }

		// ==========================================================
		// Fallback unlock for inputLocked (covers "orphan inputLocked")
		// ==========================================================
		if (gameState.inputLocked) {

			boolean gateLocked = (gameState.animationGate != null && gameState.animationGate.isLocked());

			// If inputLocked is true but the gate is NOT locked, we are stuck incorrectly.
			// Recover immediately.
			if (!gateLocked) {
				gameState.inputLocked = false;
				gameState.aiFallbackCooldownTicks = 0;
				gameState.animationLockTicks = 0;

				game.ui.TemplateCommandDispatcher.flushPendingUnitStats(out, gameState);
				game.ui.TemplateCommandDispatcher.renderAllUnits(out, gameState, gameState.domainState);
				game.ui.TemplateCommandDispatcher.renderPlayerStats(out, gameState.domainState);
				return;
			}

			// Gate is locked -> wait; if UI never sends AnimationEnded, force-unlock on timeout.
			gameState.animationLockTicks++;
			if (gameState.animationLockTicks >= GameState.ANIMATION_LOCK_TIMEOUT_TICKS) {
				if (gameState.animationGate != null) gameState.animationGate.unlockAll();
				gameState.inputLocked = false;
				gameState.aiFallbackCooldownTicks = 3;
				gameState.animationLockTicks = 0;

				game.ui.TemplateCommandDispatcher.flushPendingUnitStats(out, gameState);
				game.ui.TemplateCommandDispatcher.renderPlayerStats(out, gameState.domainState);
			}
			return;
		} else {
			// Not locked -> reset lock tick counter
			gameState.animationLockTicks = 0;
		}

		// Fallback cooldown (only if UI never sends AnimationEnded)
		if (gameState.aiFallbackCooldownTicks > 0) {
			gameState.aiFallbackCooldownTicks--;
			return;
		}

		// Flush any delayed unit stats once animations are not locked
		game.ui.TemplateCommandDispatcher.flushPendingUnitStats(out, gameState);

		// Only run when AI loop is enabled AND it is P2's turn
		if (!gameState.aiTurnActive) return;
		if (!gameState.domainState.getActivePlayerId().equals(game.model.GameState.P2)) return;

		// Per-turn cap to avoid infinite loops
		if (gameState.aiActionsThisTurn >= 8) {
			gameState.domainGameManager.endTurn(game.model.GameState.P2);
			gameState.aiTurnActive = false;

			game.ui.TemplateCommandDispatcher.renderAllUnits(out, gameState, gameState.domainState);
			game.ui.TemplateCommandDispatcher.renderPlayerStats(out, gameState.domainState);
			game.ui.TemplateCommandDispatcher.renderHand(out, gameState, gameState.domainState, game.model.GameState.P1);
			game.ui.TemplateCommandDispatcher.showNotification(out, "Your turn.", game.model.GameState.P1);
			return;
		}

		// Execute exactly ONE AI step
		java.util.Map<String, game.model.TilePos> preStepPositions = new java.util.HashMap<>();
		java.util.Map<String, game.model.Unit> preStepUnits = new java.util.HashMap<>();
		for (game.model.Unit u : gameState.domainState.getBoard().getAllUnits()) {
			preStepPositions.put(u.getId(), u.getPosition());
			preStepUnits.put(u.getId(), u);
		}
		java.util.List<game.card.Card> preAiHand = gameState.domainState.getPlayer(game.model.GameState.P2).getHand().snapshot();
		game.ai.AIController.StepResult r = gameState.aiController.step(gameState.domainState);
		if (r == null || r.type == null) return;

		switch (r.type) {

			case NONE:
				// No legal actions -> end turn
				gameState.domainGameManager.endTurn(game.model.GameState.P2);
				gameState.aiTurnActive = false;

				game.ui.TemplateCommandDispatcher.renderAllUnits(out, gameState, gameState.domainState);
				game.ui.TemplateCommandDispatcher.renderPlayerStats(out, gameState.domainState);
				game.ui.TemplateCommandDispatcher.renderHand(out, gameState, gameState.domainState, game.model.GameState.P1);
				game.ui.TemplateCommandDispatcher.showNotification(out, "Your turn.", game.model.GameState.P1);
				break;

			case MOVE:
				gameState.aiActionsThisTurn++;
				gameState.inputLocked = true;

				if (gameState.animationGate != null) gameState.animationGate.lock(game.ui.AnimationTags.MOVE);

				game.model.Unit moved = gameState.domainState.getBoard().getUnitById(r.unitId).orElse(null);
				if (moved != null) {
					game.ui.TemplateCommandDispatcher.moveUnit(out, gameState, moved);
				}

				// If UI never sends AnimationEnded(tag), allow timeout/soft recovery to kick in
				gameState.aiFallbackCooldownTicks = 5;
				break;

			case ATTACK:
				gameState.aiActionsThisTurn++;

				// No reliable UI ack for ATTACK, so do not lock the input/gate here.
				gameState.inputLocked = false;
				gameState.animationLockTicks = 0;

				game.model.Unit aiAttacker = preStepUnits.get(r.unitId);
				game.model.TilePos aiAttackerPos = preStepPositions.get(r.unitId);
				game.model.TilePos aiDefenderPos = preStepPositions.get(r.targetUnitId);
				game.ui.TemplateCommandDispatcher.animateAttack(out, gameState, aiAttacker, aiAttackerPos, aiDefenderPos);

				game.ui.TemplateCommandDispatcher.renderAllUnits(out, gameState, gameState.domainState);
				game.ui.TemplateCommandDispatcher.renderPlayerStats(out, gameState.domainState);

				// Small pacing gap so AI actions do not happen in the same heartbeat burst
				gameState.aiFallbackCooldownTicks = 0;
				break;

			case PLAY_CARD:
				gameState.aiActionsThisTurn++;

				// No reliable UI ack for PLAY_CARD, so do not lock the input/gate here.
				gameState.inputLocked = false;
				gameState.animationLockTicks = 0;

				game.card.Card aiPlayedCard = (r.handIndex != null && r.handIndex >= 0 && r.handIndex < preAiHand.size())
						? preAiHand.get(r.handIndex)
						: null;
				game.model.TilePos effectPos = null;
				if (r.cardTarget != null) {
					if (r.cardTarget.getTilePos() != null) {
						effectPos = r.cardTarget.getTilePos();
					} else if (r.cardTarget.getUnitId() != null) {
						effectPos = preStepPositions.get(r.cardTarget.getUnitId());
					}
				}
				if (effectPos == null) {
					effectPos = gameState.domainState.getPlayer(game.model.GameState.P2).getAvatar().getPosition();
				}
				if (aiPlayedCard instanceof game.card.UnitCard) {
					game.ui.TemplateCommandDispatcher.playSummonEffectAt(out, effectPos);
				} else {
					game.ui.TemplateCommandDispatcher.playEffectAt(out, effectPos, utils.StaticConfFiles.f1_inmolation);
				}

				game.ui.TemplateCommandDispatcher.renderAllUnits(out, gameState, gameState.domainState);
				game.ui.TemplateCommandDispatcher.renderPlayerStats(out, gameState.domainState);
				game.ui.TemplateCommandDispatcher.renderHand(out, gameState, gameState.domainState, game.model.GameState.P1);

				gameState.aiFallbackCooldownTicks = 0;
				break;

			case END_TURN:
				gameState.aiTurnActive = false;

				game.ui.TemplateCommandDispatcher.renderAllUnits(out, gameState, gameState.domainState);
				game.ui.TemplateCommandDispatcher.renderPlayerStats(out, gameState.domainState);
				game.ui.TemplateCommandDispatcher.renderHand(out, gameState, gameState.domainState, game.model.GameState.P1);
				game.ui.TemplateCommandDispatcher.showNotification(out, "Your turn.", game.model.GameState.P1);
				break;

			default:
				break;
		}
	}
}
