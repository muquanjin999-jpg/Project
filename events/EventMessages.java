package events;

import akka.actor.ActorRef;
import game.model.GameStatus;
import structures.GameState;

public final class EventMessages {
    private EventMessages() {}

    public static String normalizeValidationMessage(GameState gameState, String rawMessage) {
        if (rawMessage == null) return "";
        String msg = rawMessage.trim();
        if ("game is over".equalsIgnoreCase(msg)) {
            return winnerMessage(gameState);
        }
        if ("unit has no move action remaining".equalsIgnoreCase(msg)) {
            return "This unit already moved this turn.";
        }
        if ("unit is provoked and cannot move".equalsIgnoreCase(msg)) {
            return "This unit is blocked by Provoke and cannot move.";
        }
        if ("provoked: must attack a Provoke unit".equalsIgnoreCase(msg)) {
            return "You are provoked: attack the Provoke unit first.";
        }
        if ("not active player".equalsIgnoreCase(msg)) {
            return "Not your turn.";
        }
        return msg;
    }

    public static boolean showGameResultIfOver(ActorRef out, GameState gameState) {
        if (gameState == null || gameState.domainState == null) return false;
        if (!gameState.domainState.isGameOver()) return false;
        if (gameState.gameResultAnnounced) return true;

        gameState.gameResultAnnounced = true;
        game.ui.TemplateCommandDispatcher.showNotification(
                out,
                winnerMessage(gameState),
                game.model.GameState.P1,
                6
        );
        return true;
    }

    private static String winnerMessage(GameState gameState) {
        if (gameState != null && gameState.domainState != null) {
            GameStatus status = gameState.domainState.getStatus();
            if (status == GameStatus.P1_WIN) return "YOU WIN";
            if (status == GameStatus.P2_WIN) return "YOU LOSE";
        }
        return "GAME OVER";
    }
}
