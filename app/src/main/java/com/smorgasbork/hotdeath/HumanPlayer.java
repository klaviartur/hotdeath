package com.smorgasbork.hotdeath;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Human-controlled player. All "decisions" are made by the UI thread setting
 * volatile flags; this player's game thread then spins on those flags.
 */
public class HumanPlayer extends Player {

	// Decision flags – written by UI thread, read by game thread.
	private volatile boolean m_turnDecision           = false;
	private volatile boolean m_colorDecision          = false;
	private volatile boolean m_victimDecision         = false;
	private volatile boolean m_numCardsToDealDecision = false;

	public HumanPlayer(Game g, GameOptions go) {
		super(g, go);
	}

	public HumanPlayer(JSONObject o, Game g, GameOptions go) throws JSONException {
		super(o, g, go);
	}

	// -------------------------------------------------------------------------
	// Turn lifecycle
	// -------------------------------------------------------------------------

	/**
	 * Blocks the game thread until the player makes a move (play, draw, or pass).
	 */
	@Override
	public boolean startTurn() {
		m_turnDecision = false;

		if (!super.startTurn()) {
			return false;
		}

		waitForDecision(() -> m_turnDecision);
		return true;
	}

	// -------------------------------------------------------------------------
	// UI callbacks – called from UI thread
	// -------------------------------------------------------------------------

	public void turnDecisionPass() {
		m_wantsToPass  = true;
		m_turnDecision = true;
	}

	public void turnDecisionDrawCard() {
		m_wantsToDraw  = true;
		m_turnDecision = true;
	}

	/**
	 * Attempts to play {@code c}. If the card is not valid, a toast is shown
	 * and the decision flag is left false so the spin-wait continues.
	 */
	public void turnDecisionPlayCard(Card c) {
		if (!m_hand.isInHand(c)) return;

		if (m_game.checkCard(m_hand, c)) {
			m_playingCard      = c;
			m_wantsToPlayCard  = true;
			m_turnDecision     = true;
		} else {
			m_game.promptUser(m_game.getString(R.string.msg_card_no_good), false);
		}
	}

	// -------------------------------------------------------------------------
	// Decisions that require dialog prompts
	// -------------------------------------------------------------------------

	@Override
	public int getNumCardsToDeal() {
		m_numCardsToDealDecision = false;
		m_game.promptForNumCardsToDeal();
		waitForDecision(() -> m_numCardsToDealDecision);
		return m_numCardsToDeal;
	}

	/** Called by the dialog's item-click handler once the user picks a count. */
	public void setNumCardsToDeal(int numCardsToDeal) {
		m_numCardsToDeal         = numCardsToDeal;
		m_numCardsToDealDecision = true;
	}

	@Override
	public int chooseColor() {
		m_colorDecision = false;
		m_game.promptForColor();
		waitForDecision(() -> m_colorDecision);
		return m_chosenColor;
	}

	/** Called by the color-chooser tap handler once the user selects a colour. */
	public void setColor(int color) {
		m_chosenColor   = color;
		m_colorDecision = true;
	}

	@Override
	public void chooseVictim() {
		// If only one other player is active, select automatically.
		int activeCount      = 0;
		int onlyActivePlayer = 0;
		for (int seat : new int[]{Game.SEAT_WEST, Game.SEAT_NORTH, Game.SEAT_EAST}) {
			if (m_game.getPlayer(seat - 1).getActive()) {
				activeCount++;
				onlyActivePlayer = seat;
			}
		}

		if (activeCount == 1) {
			m_chosenVictim  = onlyActivePlayer;
			m_victimDecision = true;
			return;
		}

		m_victimDecision = false;
		m_game.promptForVictim();
		waitForDecision(() -> m_victimDecision);
	}

	/** Called by the victim-picker dialog once the user makes a selection. */
	public void setVictim(int victim) {
		m_chosenVictim   = victim;
		m_victimDecision = true;
	}

	// -------------------------------------------------------------------------
	// Internal helper
	// -------------------------------------------------------------------------

	/** Spins (sleeping 100 ms per tick) until {@code condition} returns true or game stops. */
	private void waitForDecision(BooleanSupplier condition) {
		while (!condition.get()) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException ignored) {
			}
			if (m_game.getStopping()) return;
		}
	}

	/** Minimal functional interface so we can pass lambda conditions without java.util.function. */
	private interface BooleanSupplier {
		boolean get();
	}
}