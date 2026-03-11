package events;

import com.fasterxml.jackson.databind.JsonNode;
import akka.actor.ActorRef;
import structures.GameState;

/**
 * Handles tile clicks.
 */
public class TileClicked implements EventProcessor {

	@Override
	public void processEvent(ActorRef out, GameState gameState, JsonNode message) {

		if (gameState == null) return;
		if (!gameState.gameInitalised || gameState.domainGameManager == null || gameState.domainState == null) return;

		// ===== orphan-lock self-heal =====
		if (gameState.inputLocked) {
			boolean gateLocked = (gameState.animationGate != null && gameState.animationGate.isLocked());
			if (!gateLocked) {
				gameState.inputLocked = false;
				gameState.animationLockTicks = 0;
				gameState.aiFallbackCooldownTicks = 0;
			} else {
				game.ui.TemplateCommandDispatcher.showNotification(out, "Input locked.");
				return;
			}
		}

		if (!gameState.domainState.getActivePlayerId().equals(game.model.GameState.P1)) {
			game.ui.TemplateCommandDispatcher.showNotification(out, "Not your turn.");
			return;
		}

		int tilex = message.get("tilex").asInt();
		int tiley = message.get("tiley").asInt();
		game.model.TilePos clicked = new game.model.TilePos(tilex, tiley);

		// If a card is selected, tile click is interpreted as "card play target selection"
		if (gameState.selectedHandPos != null) {
			handleCardTargetClick(out, gameState, clicked);
			return;
		}

		// Otherwise: unit selection / move / attack depending on rules
		game.model.Unit unitOnTile = gameState.domainState.getBoard().getUnitAt(clicked).orElse(null);

		// Click friendly unit: select it and highlight moves/attacks
		if (unitOnTile != null && unitOnTile.getOwnerId().equals(game.model.GameState.P1)) {
			gameState.selectedUnitId = unitOnTile.getId();
			game.ui.TemplateCommandDispatcher.clearTileHighlights(out, gameState);
			game.ui.TemplateCommandDispatcher.setSelectedUnitTile(out, gameState, unitOnTile.getPosition());
			gameState.highlightedTargets.clear();
			highlightForSelectedUnit(out, gameState, unitOnTile);
			return;
		}

		// If a unit is selected, interpret click as move or attack
		if (gameState.selectedUnitId != null) {
			game.model.Unit selected = gameState.domainState.getBoard().getUnitById(gameState.selectedUnitId).orElse(null);
			if (selected == null) {
				gameState.selectedUnitId = null;
				game.ui.TemplateCommandDispatcher.clearSelectedUnitTile(out, gameState);
				game.ui.TemplateCommandDispatcher.clearTileHighlights(out, gameState);
				gameState.highlightedTargets.clear();
				return;
			}

			// Attack enemy unit if clicked has enemy unit
			if (unitOnTile != null && !unitOnTile.getOwnerId().equals(game.model.GameState.P1)) {
				game.model.TilePos attackerPosBefore = selected.getPosition();
				game.model.TilePos defenderPosBefore = unitOnTile.getPosition();

				game.system.action.ValidationResult vr = gameState.domainGameManager.attack(
						game.model.GameState.P1, selected.getId(), unitOnTile.getId());
				if (!vr.isOk()) {
					game.ui.TemplateCommandDispatcher.showNotification(out, vr.getMessage());
					return;
				}

				game.ui.TemplateCommandDispatcher.animateAttack(out, gameState, selected, attackerPosBefore, defenderPosBefore);

				// No reliable UI ack for ATTACK in current template flow.
				// Apply result immediately and keep input responsive.
				gameState.inputLocked = false;
				gameState.animationLockTicks = 0;
				gameState.aiFallbackCooldownTicks = 0;

				gameState.selectedUnitId = null;
				game.ui.TemplateCommandDispatcher.clearSelectedUnitTile(out, gameState);
				game.ui.TemplateCommandDispatcher.clearTileHighlights(out, gameState);
				gameState.highlightedTargets.clear();

				game.ui.TemplateCommandDispatcher.renderAllUnits(out, gameState, gameState.domainState);
				game.ui.TemplateCommandDispatcher.renderPlayerStats(out, gameState.domainState);
				return;
			}

			// Move if clicked is empty tile
			if (unitOnTile == null) {

				game.system.action.ValidationResult vr = gameState.domainGameManager.getActionValidator()
						.validateMove(gameState.domainState, game.model.GameState.P1, selected.getId(), clicked);

				if (!vr.isOk()) {
					game.ui.TemplateCommandDispatcher.showNotification(out, vr.getMessage());
					return;
				}

				gameState.domainGameManager.moveUnit(game.model.GameState.P1, selected.getId(), clicked);

				// MOVE is the only action that has a reliable completion event (unitStopped),
				// so we keep the lock for movement only.
				gameState.inputLocked = true;
				if (gameState.animationGate != null) {
					gameState.animationGate.lock(game.ui.AnimationTags.MOVE);
				}
				gameState.animationLockTicks = 0;

				gameState.selectedUnitId = null;
				game.ui.TemplateCommandDispatcher.clearSelectedUnitTile(out, gameState);
				game.ui.TemplateCommandDispatcher.clearTileHighlights(out, gameState);
				gameState.highlightedTargets.clear();

				// Send move animation only if the unit already has a visual on screen.
				// Then force a full unit sync once, so frontend visual state stays aligned
				// with domain state even if an earlier visual was missing.
				game.model.Unit moved = gameState.domainState.getBoard().getUnitById(selected.getId()).orElse(null);
				if (moved != null) {
					game.ui.TemplateCommandDispatcher.moveUnit(out, gameState, moved);
				}

				game.ui.TemplateCommandDispatcher.renderPlayerStats(out, gameState.domainState);
				return;
			}
		}
	}

	private void handleCardTargetClick(ActorRef out, GameState gameState, game.model.TilePos clicked) {
		int handPos = gameState.selectedHandPos;
		int handIndex = handPos - 1;

		game.model.Hand<game.card.Card> hand = gameState.domainState.getPlayer(game.model.GameState.P1).getHand();
		if (handIndex < 0 || handIndex >= hand.size()) {
			gameState.selectedHandPos = null;
			game.ui.TemplateCommandDispatcher.clearSelectedUnitTile(out, gameState);
			game.ui.TemplateCommandDispatcher.clearTileHighlights(out, gameState);
			gameState.highlightedTargets.clear();
			return;
		}
		game.card.Card selectedCard = hand.get(handIndex);

		// Step 3: Portal Step two-stage targeting
		if (selectedCard instanceof game.card.PortalStepSpellCard) {
			gameState.pendingCompositeHandPos = handPos;

			// Step (1): choose source unit
			if (gameState.portalStepSourceUnitId == null) {
				game.model.Unit unit = gameState.domainState.getBoard().getUnitAt(clicked).orElse(null);
				if (unit == null) {
					game.ui.TemplateCommandDispatcher.showNotification(out, "Portal Step: click a friendly unit first.");
					return;
				}
				if (!unit.getOwnerId().equals(game.model.GameState.P1) || unit instanceof game.model.Avatar) {
					game.ui.TemplateCommandDispatcher.showNotification(out, "Portal Step: source must be a friendly (non-avatar) unit.");
					return;
				}

				gameState.portalStepSourceUnitId = unit.getId();

				java.util.Set<game.model.TilePos> dests = new java.util.HashSet<>();
				game.system.action.ActionValidator validator =
						new game.system.action.ActionValidator(new game.system.action.ReachabilityService());

				for (int x = 0; x < gameState.domainState.getRules().getBoardWidth(); x++) {
					for (int y = 0; y < gameState.domainState.getRules().getBoardHeight(); y++) {
						game.model.TilePos p = new game.model.TilePos(x, y);
						game.system.action.ValidationResult vr = validator.validateSpellTarget(
								gameState.domainState,
								game.model.GameState.P1,
								(game.card.SpellCard) selectedCard,
								game.card.CardTarget.unitToTile(gameState.portalStepSourceUnitId, p)
						);
						if (vr.isOk()) dests.add(p);
					}
				}

				game.ui.TemplateCommandDispatcher.clearTileHighlights(out, gameState);
				gameState.highlightedTargets.clear();

				if (!dests.isEmpty()) {
					game.ui.TemplateCommandDispatcher.highlightTiles(out, gameState, dests, 1);
					game.ui.TemplateCommandDispatcher.showNotification(out, "Portal Step: now click a destination tile.");
				} else {
					game.ui.TemplateCommandDispatcher.showNotification(out, "Portal Step: no valid destination tiles.");
					gameState.portalStepSourceUnitId = null;
					gameState.pendingCompositeHandPos = null;
				}
				return;
			}

			// Step (2): choose destination tile
			game.card.CardTarget target = game.card.CardTarget.unitToTile(gameState.portalStepSourceUnitId, clicked);
			game.system.action.ValidationResult vr = gameState.domainGameManager.playCardFromHand(
					game.model.GameState.P1, handIndex, target);
			if (!vr.isOk()) {
				game.ui.TemplateCommandDispatcher.showNotification(out, vr.getMessage());
				return;
			}

			gameState.inputLocked = false;
			gameState.animationLockTicks = 0;
			gameState.aiFallbackCooldownTicks = 0;

			gameState.selectedHandPos = null;
			gameState.portalStepSourceUnitId = null;
			gameState.pendingCompositeHandPos = null;

			game.ui.TemplateCommandDispatcher.clearTileHighlights(out, gameState);
			gameState.highlightedTargets.clear();
			game.ui.TemplateCommandDispatcher.playBuffEffectAt(out, clicked);

			game.ui.TemplateCommandDispatcher.renderAllUnits(out, gameState, gameState.domainState);
			game.ui.TemplateCommandDispatcher.renderPlayerStats(out, gameState.domainState);
			game.ui.TemplateCommandDispatcher.renderHand(out, gameState, gameState.domainState, game.model.GameState.P1);
			return;
		}

		// Normal targeting
		game.model.Unit unit = gameState.domainState.getBoard().getUnitAt(clicked).orElse(null);
		game.card.CardTarget target = (unit != null) ? game.card.CardTarget.unit(unit.getId()) : game.card.CardTarget.tile(clicked);

		game.system.action.ValidationResult vr = gameState.domainGameManager.playCardFromHand(
				game.model.GameState.P1, handIndex, target);
		if (!vr.isOk()) {
			game.ui.TemplateCommandDispatcher.showNotification(out, vr.getMessage());
			return;
		}

		if (selectedCard instanceof game.card.UnitCard) {
		    gameState.inputLocked = true;
		    gameState.animationLockTicks = 0;
		    gameState.aiFallbackCooldownTicks = 0;
		} else {
		    gameState.inputLocked = false;
		    gameState.animationLockTicks = 0;
		    gameState.aiFallbackCooldownTicks = 0;
		}

		gameState.selectedHandPos = null;
		gameState.portalStepSourceUnitId = null;
		gameState.pendingCompositeHandPos = null;

		game.ui.TemplateCommandDispatcher.clearTileHighlights(out, gameState);
		gameState.highlightedTargets.clear();

		if (selectedCard instanceof game.card.UnitCard) {
			game.ui.TemplateCommandDispatcher.playSummonEffectAt(out, clicked);
		} else {
			game.ui.TemplateCommandDispatcher.playEffectAt(out, clicked, utils.StaticConfFiles.f1_inmolation);
		}

		game.ui.TemplateCommandDispatcher.renderAllUnits(out, gameState, gameState.domainState);
		game.ui.TemplateCommandDispatcher.renderPlayerStats(out, gameState.domainState);
		game.ui.TemplateCommandDispatcher.renderHand(out, gameState, gameState.domainState, game.model.GameState.P1);
	}

	private void highlightForSelectedUnit(ActorRef out, GameState gameState, game.model.Unit unit) {
		java.util.Map<game.model.TilePos, Integer> tileModes = new java.util.HashMap<>();
		game.system.action.ReachabilityService rs = new game.system.action.ReachabilityService();

		for (game.model.TilePos p : rs.reachableTiles(gameState.domainState.getBoard(), unit)) {
			tileModes.put(p, 5); 
		}

		game.system.action.ActionValidator validator =
				new game.system.action.ActionValidator(new game.system.action.ReachabilityService());
		for (game.model.Unit enemy : gameState.domainState.getBoard().getAllUnits()) {
			if (enemy.getOwnerId().equals(game.model.GameState.P1)) continue;
			game.system.action.ValidationResult vr = validator.validateAttack(
					gameState.domainState, game.model.GameState.P1, unit.getId(), enemy.getId());
			if (vr.isOk() && enemy.getPosition() != null) {
				tileModes.put(enemy.getPosition(), 2); // red = attackable
			}
		}

		game.ui.TemplateCommandDispatcher.clearTileHighlights(out, gameState);
		game.ui.TemplateCommandDispatcher.setSelectedUnitTile(out, gameState, unit.getPosition());
		gameState.highlightedTargets.clear();

		if (!tileModes.isEmpty()) {
			game.ui.TemplateCommandDispatcher.highlightTilesWithModes(out, gameState, tileModes);
		}
		game.ui.TemplateCommandDispatcher.showNotification(out,
		        "Selected " + unit.getName() + ". Green = move. Red = attack.",
		        game.model.GameState.P1, 4);
	}
}