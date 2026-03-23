package com.smorgasbork.hotdeath;

import java.util.Random;
import org.json.*;

/**
 * Base class for all players (human or AI).
 * Holds hand, score bookkeeping, and per-turn decision state.
 */
public class Player {

	// ── dependencies ──────────────────────────────────────────────────────────
	protected Game        m_game;
	protected GameOptions m_go;
	protected Hand        m_hand;

	// ── identity ──────────────────────────────────────────────────────────────
	protected int m_seat;

	// ── per-round state ───────────────────────────────────────────────────────
	protected boolean m_active;
	protected boolean m_hasTriedDrawing;
	protected Card    m_lastDrawn;

	// ── per-turn decision flags ───────────────────────────────────────────────
	protected boolean m_wantsToDraw     = false;
	protected boolean m_wantsToPlayCard = false;
	protected boolean m_wantsToPass     = false;
	protected Card    m_playingCard     = null;

	// ── AI / prompt results ───────────────────────────────────────────────────
	protected int m_chosenColor  = 0;
	protected int m_chosenVictim = 0;
	protected int m_numCardsToDeal = 0;
	protected int m_skill      = 1;   // 0=weak, 1=strong, 2=expert
	protected int m_aggression = 0;

	// ── scoring ───────────────────────────────────────────────────────────────
	protected int m_lastScore;
	protected int m_lastVirusPenalty;
	protected int m_totalScore;
	protected int m_virusPenalty;

	/** Retained for compatibility; never set on this base class. */
	protected final Card m_changedLastClicked = null;

	@SuppressWarnings("unused")
	protected final boolean[][] m_othersVoids = new boolean[4][4];

	// ── constructors ──────────────────────────────────────────────────────────

	public Player(Game game, GameOptions go) {
		m_game           = game;
		m_go             = go;
		m_skill          = 1;
		m_aggression     = 0;
		m_virusPenalty   = 0;
		m_hasTriedDrawing = false;
		m_lastDrawn      = null;
	}

	public Player(JSONObject o, Game game, GameOptions go) throws JSONException {
		this(game, go);
		m_hand              = new Hand(o.getJSONObject("hand"), this, game.getDeck(), go);
		m_totalScore        = o.getInt("totalScore");
		m_lastScore         = o.getInt("lastScore");
		m_virusPenalty      = o.getInt("virusPenalty");
		m_lastVirusPenalty  = o.getInt("lastVirusPenalty");
		m_active            = o.getBoolean("active");
		m_hasTriedDrawing   = o.getBoolean("hasTriedDrawing");

		int nLastDrawn = o.getInt("lastDrawn");
		m_lastDrawn = (nLastDrawn != -1) ? game.getDeck().getCard(nLastDrawn) : null;
	}

	// ── lifecycle ─────────────────────────────────────────────────────────────

	public void shutdown() {
		m_game = null;
		m_go   = null;
		m_hand = null;
	}

	public void resetRound() {
		for (boolean[] row : m_othersVoids) java.util.Arrays.fill(row, false);
		m_hand   = new Hand(this, m_go.getFaceUp());
		m_active = true;
	}

	public void resetGame() {
		m_lastScore   = 0;
		m_totalScore  = 0;
		m_virusPenalty = 0;
		resetRound();
	}

	// ── turn logic ────────────────────────────────────────────────────────────

	/**
	 * Called at the start of each turn.  Resets per-turn flags and handles the
	 * common case where the player has no valid cards while under penalty.
	 *
	 * @return {@code false} if the player cannot act (will pass), {@code true} otherwise.
	 */
	public boolean startTurn() {
		m_wantsToDraw     = false;
		m_wantsToPass     = false;
		m_wantsToPlayCard = false;

		if (m_game.getPenalty().getType() != Penalty.PENTYPE_NONE
				&& !m_hand.hasValidCards(m_game)) {
			m_game.waitABit();
			m_wantsToPass = true;
			return false;
		}
		return true;
	}

	// ── drawing ───────────────────────────────────────────────────────────────

	protected Card drawCard() {
		m_hasTriedDrawing = true;
		Card c = m_game.drawCard();
		if (c == null) return null;
		addCardToHand(c, false);
		m_lastDrawn = c;
		return c;
	}

	// ── decision stubs (overridden by subclasses) ─────────────────────────────

	public void  chooseNumCardsToDeal() {}
	public int   chooseColor()          { return Card.COLOR_WILD; }
	public void  chooseVictim()         {}
	public void  finishTrick()          {}

	public int getNumCardsToDeal() {
		return new Random().nextInt(11) + 5;
	}

	// ── hand management ───────────────────────────────────────────────────────

	public void addCardToHand(Card c, boolean instant) {
		m_hand.addCard(c, instant);
	}

	// ── accessors ─────────────────────────────────────────────────────────────

	public int     getSeat()              { return m_seat; }
	public void    setSeat(int s)         { m_seat = s; }
	public boolean getActive()            { return m_active; }
	public void    setActive(boolean a)   { m_active = a; }
	public Hand    getHand()              { return m_hand; }

	public boolean getWantsToDraw()       { return m_wantsToDraw; }
	public boolean getWantsToPlayCard()   { return m_wantsToPlayCard; }
	public boolean getWantsToPass()       { return m_wantsToPass; }
	public Card    getPlayingCard()       { return m_playingCard; }
	public int     getChosenVictim()      { return m_chosenVictim; }

	public boolean getHasTriedDrawing()   { return m_hasTriedDrawing; }
	public Card    getLastDrawn()         { return m_lastDrawn; }
	public void    resetLastDrawn()       { m_hasTriedDrawing = false; m_lastDrawn = null; }

	public int  getVirusPenalty()         { return m_virusPenalty; }
	public void setVirusPenalty(int p)    { m_virusPenalty = p; }
	public int  getLastVirusPenalty()     { return m_lastVirusPenalty; }
	public void setLastVirusPenalty(int p){ m_lastVirusPenalty = p; }
	public int  getLastScore()            { return m_lastScore; }
	public void setLastScore(int s)       { m_lastScore = s; }
	public int  getTotalScore()           { return m_totalScore; }
	public void setTotalScore(int s)      { m_totalScore = s; }
	public Card getChangedLastClicked()   { return m_changedLastClicked; }

	// ── serialization ─────────────────────────────────────────────────────────

	public JSONObject toJSON() throws JSONException {
		JSONObject o = new JSONObject();
		o.put("active",           m_active);
		o.put("totalScore",       m_totalScore);
		o.put("lastScore",        m_lastScore);
		o.put("hasTriedDrawing",  m_hasTriedDrawing);
		o.put("lastDrawn",        m_lastDrawn != null ? m_lastDrawn.getDeckIndex() : -1);
		o.put("virusPenalty",     m_virusPenalty);
		o.put("lastVirusPenalty", m_lastVirusPenalty);
		o.put("hand",             m_hand.toJSON());
		return o;
	}
}