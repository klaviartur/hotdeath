package com.smorgasbork.hotdeath;

import android.graphics.Color;
import android.util.Log;
import org.json.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

/**
 * Core game logic thread. Drives the round/turn state machine and coordinates
 * all player actions, card movements, and scoring.
 *
 * Threading model
 * ---------------
 * This class extends Thread and runs the game loop on its own thread. UI
 * interactions are posted back to the main thread via GameActivity.runOnUiThread()
 * or GameTable.postInvalidate().  The pause/resume mechanism uses a monitor lock
 * ({@link #m_pauseLock}).
 */
public class Game extends Thread {

	// -------------------------------------------------------------------------
	// Constants
	// -------------------------------------------------------------------------

	public static final int MAX_NUM_CARDS = 216;

	public static final int SEAT_SOUTH = 1;
	public static final int SEAT_WEST  = 2;
	public static final int SEAT_NORTH = 3;
	public static final int SEAT_EAST  = 4;

	public static final int DIR_NONE       = 0;
	public static final int DIR_CLOCKWISE  = 1;
	public static final int DIR_CCLOCKWISE = 2;

	private static final String TAG = "HDU";

	// -------------------------------------------------------------------------
	// State
	// -------------------------------------------------------------------------

	private volatile boolean m_stopping = false;

	private boolean m_roundComplete        = true;
	private boolean m_waitingToStartRound  = false;
	private boolean m_gameOver             = false;
	private int     m_winner               = 0;

	private GameTable   m_gt;
	private GameOptions m_go;
	private GameActivity m_ga;

	private final Player[] m_players = new Player[4];

	private Player   m_currPlayer;
	private Player   m_nextPlayerPreset;
	private Player   m_dealer;
	private int      m_numCardsToDeal;

	private Card     m_currCard;
	private Card     m_prevCard;

	private CardDeck m_deck;
	private CardPile m_drawPile;
	private CardPile m_discardPile;

	private int      m_direction;
	private int      m_currColor;
	private int      m_cardsPlayed;

	private boolean  m_forceDrawing = false;
	private boolean  m_fastForward  = false;

	private Penalty  m_penalty;
	private boolean  m_lastCardCheckedIsDefender = false;

	private final Object m_pauseLock = new Object();
	private boolean  m_paused            = false;
	private boolean  m_resumingSavedGame = false;

	private JSONObject m_snapshot = null;
	private boolean    m_standardRules;
	private CardDeck.DeckType m_deckType;

	// -------------------------------------------------------------------------
	// Accessors
	// -------------------------------------------------------------------------

	public boolean   getStopping()          { return m_stopping; }
	public int       getWinner()            { return m_winner; }
	public boolean   getRoundComplete()     { return m_roundComplete; }
	public boolean   getFastForward()       { return m_fastForward; }
	public Player    getCurrPlayer()        { return m_currPlayer; }
	public Player    getDealer()            { return m_dealer; }
	public Player    getPlayer(int i)       { return m_players[i]; }
	public Card      getLastPlayedCard()    { return m_currCard; }
	public CardPile  getDrawPile()          { return m_drawPile; }
	public CardPile  getDiscardPile()       { return m_discardPile; }
	public CardDeck  getDeck()              { return m_deck; }
	public int       getCurrColor()         { return m_currColor; }
	public int       getDirection()         { return m_direction; }
	public Penalty   getPenalty()           { return m_penalty; }
	public GameTable getGameTable()         { return m_gt; }

	public boolean getCurrPlayerUnderAttack() {
		return m_penalty.getType() != Penalty.PENTYPE_NONE;
	}

	public boolean getCurrPlayerDrawn() {
		return m_currPlayer.getHasTriedDrawing();
	}

	boolean getLastCardCheckedIsDefender() {
		return m_lastCardCheckedIsDefender;
	}

	public void setGameTable(GameTable gt) { m_gt = gt; }

	public void setFastForward(boolean ff) {
		Log.d(TAG, "setFastForward(" + ff + ")");
		m_fastForward = ff;
	}

	public void setWaitingToStartRound(boolean wtsr) {
		Log.d(TAG, "setWaitingToStartRound(" + wtsr + ")");
		m_waitingToStartRound = wtsr;
	}

	// -------------------------------------------------------------------------
	// Construction
	// -------------------------------------------------------------------------

	/** New game. */
	public Game(GameActivity ga, GameOptions go) {
		m_go = go;
		m_ga = ga;
		m_penalty = null;

		m_deckType 		= m_go.getDeckType();
		m_standardRules = m_deckType == CardDeck.DeckType.VANILLA;
		m_deck 			= new CardDeck (m_deckType);
		m_direction     = DIR_NONE;

		m_players[0] = m_go.getComputer4th()
				? new ComputerPlayer(SEAT_SOUTH, this, m_go)
				: new HumanPlayer(SEAT_SOUTH, this, m_go);
		m_players[1] = new ComputerPlayer(SEAT_WEST, this, m_go);
		m_players[2] = new ComputerPlayer(SEAT_NORTH, this, m_go);
		m_players[3] = new ComputerPlayer(SEAT_EAST, this, m_go);
	}

	/** Resume saved game. */
	public Game(JSONObject gamestate, GameActivity ga, GameOptions go) {
		m_go = go;
		m_ga = ga;
		m_penalty = null;

		try {
			m_deckType 		= CardDeck.DeckType.fromKey(gamestate.getString("deckType"));
			m_standardRules = m_deckType == CardDeck.DeckType.VANILLA;
			m_snapshot      = gamestate;

			m_deck        = new CardDeck(m_deckType);
			m_drawPile    = new CardPile(gamestate.getJSONObject("drawPile"),    m_deck, m_go.getFaceUp(), Card.CardState.DRAW_PILE);
			m_discardPile = new CardPile(gamestate.getJSONObject("discardPile"), m_deck, true,             Card.CardState.DISCARD_PILE);

			JSONObject state = gamestate.getJSONObject("state");
			m_currColor    = state.getInt("currColor");
			m_direction    = state.getInt("direction");
			m_cardsPlayed  = state.getInt("cardsPlayed");
			m_roundComplete = state.getBoolean("roundComplete");

			int nCurrCard = state.getInt("currCard");
			m_currCard = (nCurrCard != -1) ? m_deck.getCard(nCurrCard) : null;

			int nCurrPlayer = state.getInt("currPlayer") - 1;
			int nDealer     = state.getInt("dealer")     - 1;

			JSONArray players = gamestate.getJSONArray("players");
			m_players[0] = m_go.getComputer4th()
					? new ComputerPlayer(SEAT_SOUTH, players.getJSONObject(0), this, m_go)
					: new HumanPlayer(SEAT_SOUTH, players.getJSONObject(0), this, m_go);
			m_players[1] = new ComputerPlayer(SEAT_WEST, players.getJSONObject(1), this, m_go);
			m_players[2] = new ComputerPlayer(SEAT_NORTH, players.getJSONObject(2), this, m_go);
			m_players[3] = new ComputerPlayer(SEAT_EAST, players.getJSONObject(3), this, m_go);

			m_penalty   = new Penalty(gamestate.getJSONObject("penalty"), this, m_deck);
			m_currPlayer = m_players[nCurrPlayer];
			m_dealer     = m_players[nDealer];

			m_resumingSavedGame = true;
		} catch (JSONException e) {
			Log.e(TAG, "Failed to restore game from JSON: " + e.getMessage());
		}
	}

	// -------------------------------------------------------------------------
	// Serialisation
	// -------------------------------------------------------------------------

	public String getSnapshot() {
		if (m_gameOver || m_snapshot == null) return "";
		return m_snapshot.toString();
	}

	public JSONObject toJSON() {
		synchronized (m_pauseLock) {
			JSONObject o = new JSONObject();
			try {
				o.put("deckType", m_deckType.key);

				JSONObject state = new JSONObject();
				state.put("dealer",       m_dealer.getSeat());
				state.put("currPlayer",   m_currPlayer.getSeat());
				state.put("currColor",    m_currColor);
				state.put("direction",    m_direction);
				state.put("cardsPlayed",  m_cardsPlayed);
				state.put("roundComplete", m_roundComplete);
				state.put("currCard", m_currCard != null ? m_currCard.getDeckIndex() : -1);
				o.put("state", state);

				o.put("drawPile",    m_drawPile.toJSON());
				o.put("discardPile", m_discardPile.toJSON());
				o.put("penalty",     m_penalty.toJSON());

				JSONArray players = new JSONArray();
				for (Player p : m_players) players.put(p.toJSON());
				o.put("players", players);
			} catch (JSONException e) {
				Log.e(TAG, "toJSON failed: " + e.getMessage());
			}
			return o;
		}
	}

	// -------------------------------------------------------------------------
	// Pause / resume
	// -------------------------------------------------------------------------

	public void pause() {
		synchronized (m_pauseLock) { m_paused = true; }
	}

	public void unpause() {
		synchronized (m_pauseLock) {
			m_paused = false;
			m_pauseLock.notifyAll();
		}
	}

	private void waitUntilUnpaused() {
		synchronized (m_pauseLock) {
			while (m_paused) {
				try { m_pauseLock.wait(); }
				catch (InterruptedException e) { Log.d(TAG, "waitUntilUnpaused interrupted"); }
			}
		}
	}

	// -------------------------------------------------------------------------
	// Shutdown
	// -------------------------------------------------------------------------

	public void shutdown() {
		if (!m_stopping) {
			m_stopping = true;
			Log.d(TAG, "Game thread shutdown requested…");
			return;
		}
		Log.d(TAG, "Game thread shutting down…");
		if (m_gt != null) {
			m_gt.shutdown();
			m_go.shutdown();
			DirectionIndicator.getInstance().reset();
			ColorChooser.getInstance().reset();
			for (Player p : m_players) p.shutdown();
		}
		m_go = null;
		m_ga = null;
		m_gt = null;
	}

	// -------------------------------------------------------------------------
	// Thread entry point
	// -------------------------------------------------------------------------

	@Override
	public void run() {
		if (m_resumingSavedGame) {
			m_resumingSavedGame = false;
			if (m_roundComplete) {
				showNextRoundButton(true);
				waitForNextRound();
				startRound();
			} else {
				m_gt.startPointerAnimation((m_currPlayer.getSeat() - 1) * 90, DIR_NONE);
				m_gt.startDirectionIndicatorAnimation(m_direction, m_currColor);
				if (!m_players[SEAT_SOUTH - 1].getActive()) showFastForwardButton(true);
			}
		} else {
			startGame();
		}

		while (!m_gameOver) {
			runRound();
			if (m_stopping) { shutdown(); Log.d(TAG, "exiting Game.run()"); return; }
			if (!m_gameOver) {
				waitForNextRound();
				startRound();
			}
		}

		shutdown();
		Log.d(TAG, "exiting Game.run()");
	}

	// -------------------------------------------------------------------------
	// Game / round management
	// -------------------------------------------------------------------------

	public void startGame() {
		resetGame();
		startRound();
	}

	public void resetGame() {
		m_gameOver = false;
		m_winner   = 0;
		for (Player p : m_players) p.resetGame();
		m_dealer = m_players[new Random().nextInt(4)];
	}

	public void resetRound()
	{
		m_direction = DIR_CLOCKWISE;
		m_drawPile =    new CardPile (m_go.getFaceUp(), Card.CardState.DRAW_PILE, new ArrayList<>(Arrays.asList(m_deck.getCards())));
		m_discardPile = new CardPile (true, Card.CardState.DISCARD_PILE);
		m_cardsPlayed = 0;
		m_roundComplete = false;
		DirectionIndicator.getInstance().reset();
		ColorChooser.getInstance().reset();
		for (Player p : m_players) p.resetRound();
		m_penalty = new Penalty ();
		m_currCard = null;
		m_prevCard = null;
	}

	public void startRound() {
		waitUntilUnpaused();
		showNextRoundButton(false);
		resetRound();
		m_currPlayer = m_dealer;
		Pointer.getInstance().setRot((m_currPlayer.getSeat() - 1) * 90);

		m_drawPile.shuffle();
		for (Player p : m_players) p.getHand().reset();

		if (m_standardRules) {
			m_numCardsToDeal = 7;
		} else {
			m_numCardsToDeal = m_dealer.getNumCardsToDeal();
			if (m_stopping) return;
		}

		waitABit(2);
		dealHands();
		postDealHands();
		waitABit();
	}

	private void runRound() {
		do {
			m_snapshot = this.toJSON();
			waitUntilUnpaused();
			if (m_roundComplete || m_stopping) return;
		} while (advanceRound());
	}

	private void waitForNextRound() {
		promptUser(getString(R.string.msg_tap_draw_pile));
		m_waitingToStartRound = true;
		while (m_waitingToStartRound) {
			try { Thread.sleep(100); }
			catch (InterruptedException e) { Log.d(TAG, "waitForNextRound interrupted"); }
		}
	}

	// -------------------------------------------------------------------------
	// Dealing
	// -------------------------------------------------------------------------

	public void dealHands() {
		waitUntilUnpaused();
		Player p = getNextPlayer(m_dealer);
		promptUser(String.format(getString(R.string.msg_dealing),
				seatToString(m_dealer.getSeat()), m_numCardsToDeal));

		if (android.os.Debug.isDebuggerConnected() && false /* set to true to enable debug deal */) {
			dealDebugHands();
		} else {
			dealNormalHands(p);
		}
	}

	private void dealNormalHands(Player firstPlayer) {
		Player p = firstPlayer;
		for (int i = 0; i < 4 * m_numCardsToDeal; i++) {
			Card c = m_drawPile.drawCard();
			p.addCardToHand(c, false);
			m_gt.dealCard(c, m_dealer.getSeat(), p, 60);
			p = getNextPlayer(p);
		}
		waitABit(2);
	}

	private boolean isCheatCard(Card c) {
		int id = c.getID();
		return id == Card.ID_RED_0_HD       || id == Card.ID_RED_2_GLASNOST
				|| id == Card.ID_RED_5_MAGIC    || id == Card.ID_RED_D_SPREADER
				|| id == Card.ID_YELLOW_69      || id == Card.ID_GREEN_D_SPREADER
				|| id == Card.ID_WILD_MYSTERY   || id == Card.ID_GREEN_3_AIDS
				|| id == Card.ID_WILD_DB        || id == Card.ID_BLUE_2_SHIELD
				|| id == Card.ID_GREEN_4_IRISH  || id == Card.ID_WILD_DRAW_FOUR;
	}

	/** Stub for debug deals — populate as needed during development. */
	private void dealDebugHands() {
		// Add debug hand scenarios here when needed.
	}

	private void postDealHands() {
		for (Player p : m_players) {
			if (checkForAllBastardCards(p.getHand())) {
				gotAllBastardCards(p);
				finishRound(p);
				return;
			}
		}

		m_players[SEAT_SOUTH - 1].getHand().reveal();
		waitABit();

		int cheatLevel = m_go.getCheatLevel();
		for (int i = m_drawPile.getNumCards() - 1; i >= 0  && cheatLevel > 0; i--) {
			Card newCard = m_drawPile.getCard(i);
			if (isCheatCard(newCard)) {
				int pos = m_drawPile.pullCard(newCard);
				if (pos != -1) {
					Card oldCard = m_players[SEAT_SOUTH - 1].getHand().swapCard(newCard);
					m_drawPile.putCard(oldCard, pos);
					m_gt.swapCheatCard(newCard, oldCard, 2);
					cheatLevel--;
				}
			}
		}

		do {
			m_currCard = m_drawPile.drawCard();
			m_currColor = m_currCard.getColor();
			m_discardPile.addCard(m_currCard, false);

			if ((m_currCard.getValue() == Card.VAL_R || m_currCard.getValue() == Card.VAL_R_SKIP)
					&& !m_standardRules) {
				changeDirection();
			}
			m_gt.moveCardToDiscardPile(m_currCard);
		} while ( m_standardRules && m_currColor == Card.COLOR_WILD);

		if (!m_standardRules) { handleSpecialCards(true); }

		if (m_nextPlayerPreset != null || m_standardRules) { m_currPlayer = nextPlayer(); }
	}

	// -------------------------------------------------------------------------
	// Round advancement
	// -------------------------------------------------------------------------

	public boolean advanceRound() {
		if (m_currPlayer == null) return false;

		if (m_currPlayer instanceof HumanPlayer) {
			promptUser(getString(R.string.msg_your_play), false);
			showMenuButton(true);
		} else {
			showMenuButton(false);
		}

		m_currPlayer.startTurn();
		if (m_stopping) return false;

		if (m_currPlayer.getHand().hasValidCards(this) && !m_currPlayer.getWantsToPass()) {
			handleValidCardsTurn();
		} else {
			handleNoValidCardsTurn();
		}

		// Check for a winner after every player action.
		for (int i = 0; i < 4; i++) {
			Hand h = m_players[i].getHand();
			boolean allBastard = checkForAllBastardCards(h);
			if (allBastard) gotAllBastardCards(m_players[i]);
			if ((allBastard || h.getNumCards() == 0) && m_penalty.getType() == Penalty.PENTYPE_NONE) {
				finishRound(m_players[i]);
				return false;
			}
		}

		return true;
	}

	private void handleValidCardsTurn() {
		if (m_currPlayer.getWantsToPlayCard()) {
			executeCardPlay();
		} else if (m_currPlayer.getWantsToDraw() && !getCurrPlayerDrawn()) {
			executeDrawCard();
		} else if (m_currPlayer.getWantsToPass() && getCurrPlayerDrawn()) {
			executePass();
		}
	}

	private void executeCardPlay() {
		m_prevCard = m_currCard;
		m_currCard = m_currPlayer.getPlayingCard();
		m_currPlayer.getHand().removeCard(m_currCard);
		logCardPlay(m_currPlayer, m_currCard);
		m_cardsPlayed++;
		m_discardPile.addCard(m_currCard, false);

				if (m_currCard.getValue() == Card.VAL_R || m_currCard.getValue() == Card.VAL_R_SKIP ||
						(m_currCard.getID() == Card.ID_BLUE_0_FUCK_YOU) && (m_penalty.getVictim() == m_currPlayer || m_penalty.getSecondaryVictim() == m_currPlayer)) {
					changeDirection();
				}

		m_currColor = m_currCard.getColor();
		m_gt.moveCardToDiscardPile(m_currCard);
		handleSpecialCards(false);
		if (m_stopping) return;

		// if previous player set us up, and we did not throw something
		// that would negate the penalty, then we get penalized now
		if (m_penalty.getType() != Penalty.PENTYPE_NONE
			&& (m_penalty.getVictim() == m_currPlayer
			|| m_currCard.getID() == Card.ID_YELLOW_1_MAD
			|| m_penalty.getVictim() == null && m_penalty.getSecondaryVictim() == m_currPlayer))
		{
			assessPenalty();
		}



		if (m_penalty.getType() == Penalty.PENTYPE_NONE && m_currPlayer.getHand().getNumCards() == 0) {
			finishRound(m_currPlayer);
			return;
		}
		m_currPlayer = nextPlayer();
	}

	private void executeDrawCard() {
		Card card = m_currPlayer.drawCard();
		if (card == null) return;
		m_gt.moveCardToPlayer(card, m_currPlayer, 2);
		if (m_currPlayer instanceof HumanPlayer) {
			Log.d(TAG, String.format(getString(R.string.msg_player_draws_specific_card),
					seatToString(m_currPlayer.getSeat()), cardToString(m_currPlayer.getLastDrawn())));
		}
	}

	private void executePass() {
		if (m_penalty.getType() != Penalty.PENTYPE_NONE) assessPenalty();
		m_currPlayer = nextPlayer();
	}

	private void handleNoValidCardsTurn() {
		if (m_penalty.getType() != Penalty.PENTYPE_NONE) {
			assessPenalty();
			if (m_currColor == Card.COLOR_WILD) {
				m_currColor = m_currPlayer.chooseColor();
				m_gt.startDirectionIndicatorAnimation(m_direction, m_currColor);
			}
			m_currPlayer = nextPlayer();
		} else {
			if (getCurrPlayerDrawn()) {
				m_currPlayer = nextPlayer();
			} else {
				Card card = m_currPlayer.drawCard();
				if (card == null) return;
				m_gt.moveCardToPlayer(card, m_currPlayer, 2);
				String msg = String.format(getString(R.string.msg_player_draws_card),
						seatToString(m_currPlayer.getSeat()));
				Log.d(TAG, msg);
				if (m_currPlayer.getSeat() != SEAT_SOUTH) promptUser(msg);
				if (!m_currPlayer.getHand().hasValidCards(this)) {
					m_currPlayer = nextPlayer();
				}
			}
		}
	}

	// -------------------------------------------------------------------------
	// Player navigation
	// -------------------------------------------------------------------------

	public Player getNextPlayer() { return getNextPlayer(null); }

	public Player getNextPlayer(Player from) {
		if (from == null) from = m_currPlayer;
		Player p = from;
		do {
			p = (m_direction == DIR_CLOCKWISE)
					? m_players[p.getSeat() % 4]
					: m_players[(p.getSeat() + 2) % 4];
		} while (!p.getActive());
		return p;
	}

	public Player nextPlayer() {
		Player p;
		int dir = m_direction;
		m_currPlayer.resetLastDrawn();

		if (m_nextPlayerPreset != null) {
			p = m_nextPlayerPreset;
			m_nextPlayerPreset = null;
			dir = DIR_NONE;
		} else {
			p = getNextPlayer();
		}

		if (p != m_currPlayer) {
			m_gt.startPointerAnimation((p.getSeat() - 1) * 90, dir);
		}
		return p;
	}

	// -------------------------------------------------------------------------
	// Card checking
	// -------------------------------------------------------------------------

	boolean checkCard(Hand h, Card c) {
		if (!h.isInHand(c)) return false;

		int currVal  = m_currCard.getValue();
		int currID   = m_currCard.getID();
		int checkVal = c.getValue();
		int checkID  = c.getID();
		boolean hasMatch = h.hasColorMatch(m_currColor);

		m_lastCardCheckedIsDefender = false;

		if (m_penalty.getType() != Penalty.PENTYPE_NONE) {
			return checkCardUnderPenalty(c, checkVal, checkID, currID);
		}

		// 69 interplay: 6↔9 if player holds the 69 card
		if ((checkVal == 6 && currVal == 9) || (checkVal == 9 && currVal == 6)) {
			for (int i = 0; i < h.getNumCards(); i++) {
				if (h.getCard(i).getID() == Card.ID_YELLOW_69) return true;
			}
		}

		if (checkID == Card.ID_YELLOW_0_SHITTER) {
			return currID == Card.ID_RED_0_HD
					|| currID == Card.ID_RED_5_MAGIC
					|| h.getNumCards() == 1;
		}

		if (currID  == Card.ID_YELLOW_69 && (checkVal == 6 || checkVal == 9)) return true;
		if (checkID == Card.ID_YELLOW_69 && (currVal  == 6 || currVal  == 9)) return true;
		if (checkID == Card.ID_RED_5_MAGIC) return true;

		// D/S/R variant interplay
		if (currVal == Card.VAL_D       && checkVal == Card.VAL_D_SPREAD) return true;
		if (currVal == Card.VAL_D_SPREAD && checkVal == Card.VAL_D)       return true;
		if (currVal == Card.VAL_R       && checkVal == Card.VAL_R_SKIP)   return true;
		if (currVal == Card.VAL_R_SKIP  && checkVal == Card.VAL_R)        return true;
		if (currVal == Card.VAL_S       && checkVal == Card.VAL_S_DOUBLE) return true;
		if (currVal == Card.VAL_S_DOUBLE && checkVal == Card.VAL_S)       return true;
		// Backstab plays on any Reverse (same suit family)
		if (currVal == Card.VAL_R       && checkVal == Card.VAL_R_BACKSTAB) return true;
		if (currVal == Card.VAL_R_SKIP  && checkVal == Card.VAL_R_BACKSTAB) return true;
		if (currVal == Card.VAL_R_BACKSTAB && checkVal == Card.VAL_R)     return true;
		if (currVal == Card.VAL_R_BACKSTAB && checkVal == Card.VAL_R_SKIP) return true;
		if (currVal == Card.VAL_R_BACKSTAB && checkVal == Card.VAL_R_BACKSTAB) return true;
		// Swap plays on any Reverse
		if (currVal == Card.VAL_R       && checkVal == Card.VAL_R_SWAP)     return true;
		if (currVal == Card.VAL_R_SKIP  && checkVal == Card.VAL_R_SWAP)     return true;
		if (currVal == Card.VAL_R_BACKSTAB && checkVal == Card.VAL_R_SWAP)  return true;
		if (currVal == Card.VAL_R_SWAP && checkVal == Card.VAL_R)        return true;
		if (currVal == Card.VAL_R_SWAP && checkVal == Card.VAL_R_SKIP)   return true;
		if (currVal == Card.VAL_R_SWAP && checkVal == Card.VAL_R_BACKSTAB) return true;
		if (currVal == Card.VAL_R_SWAP && checkVal == Card.VAL_R_SWAP)     return true;
		// Dodge plays on any 8 (same face value)
		//if (currVal == 8                && checkVal == Card.VAL_DODGE)    return true;
		//if (currVal == Card.VAL_DODGE   && checkVal == 8)                 return true;
		// Clone can be played on any card (handled separately below via color/wild check)
		if (checkVal == Card.VAL_2_CLONE) return true;

		if (m_standardRules && hasMatch && c.getValue() == Card.VAL_WILD_DRAW) return false;

		return c.getColor() == m_currColor
				|| c.getColor() == Card.COLOR_WILD
				|| m_currColor  == Card.COLOR_WILD
				|| checkVal == currVal;
	}

	private boolean checkCardUnderPenalty(Card c, int checkVal, int checkID, int currID) {
		// AIDS cannot be defended against.
		if (currID == Card.ID_GREEN_3_AIDS) return false;

		int origID = m_penalty.getOrigCard().getID();
		int origVal = m_penalty.getOrigCard().getValue();

		// Holy Defender, Fuck You, and AIDS can defend against most draw penalties.
		boolean isDefender = checkID == Card.ID_BLUE_0_FUCK_YOU
				|| checkID == Card.ID_RED_0_HD
				|| checkID == Card.ID_GREEN_3_AIDS;

		boolean origIsAttack = (origVal == Card.VAL_WILD_DRAW
				&& origID != Card.ID_WILD_HOS)
				|| origID == Card.ID_GREEN_0_QUITTER
				|| origID == Card.ID_RED_2_GLASNOST;

		if (isDefender && origIsAttack) {
			m_lastCardCheckedIsDefender = true;
			return true;
		}

		// Stack wild-draw fours (except on HOS / Mystery).
		if (!m_standardRules
				&& m_penalty.getSecondaryVictim() == null
				&& origVal    == Card.VAL_WILD_DRAW
				&& origID != Card.ID_WILD_HOS
				&& origID != Card.ID_WILD_MYSTERY
				&& checkVal   == Card.VAL_WILD_DRAW
				&& checkID    != Card.ID_WILD_MYSTERY) {
			m_lastCardCheckedIsDefender = true;
			return true;
		}

		// Magic 5 defends specifically against Hot Death.
		m_lastCardCheckedIsDefender = checkID == Card.ID_RED_5_MAGIC
				&& m_penalty.hasHotDeath()
				&& m_currCard.getID() != Card.ID_WILD_HOS;
		return m_lastCardCheckedIsDefender;
	}

	// -------------------------------------------------------------------------
	// Special card handling
	// -------------------------------------------------------------------------

	public void handleSpecialCards(boolean virtualPlayer) {
		if (m_currColor == Card.COLOR_WILD && !virtualPlayer) {
			m_currColor = m_currPlayer.chooseColor();
			if (m_stopping) return;
			Log.d(TAG, String.format(getString(R.string.msg_color_chosen),
					seatToString(m_currPlayer.getSeat()), colorToString(m_currColor)));
			m_gt.startDirectionIndicatorAnimation(m_direction, m_currColor);
		}

		int currVal = m_currCard.getValue();
		int currID  = m_currCard.getID();
		m_nextPlayerPreset = null;

		// Reverse
		if (currVal == Card.VAL_R) {
			if (getActivePlayerCount() == 2) m_currPlayer = nextPlayer();
			return;
		}
		if (currVal == Card.VAL_R_SKIP) {
			if (virtualPlayer) waitABit(2);
			m_currPlayer = nextPlayer();
			return;
		}
		// Backstab: draw 2 lands on the player BEFORE the current player, then reverse
		if (currVal == Card.VAL_R_BACKSTAB) {
			if (virtualPlayer) waitABit(2);
			// "Before" in current direction = next in opposite direction
			Player prev = getPlayerBefore(m_currPlayer);
			if (prev != null && prev != m_currPlayer) {
				forceDraw(prev, 2);
			}
			// Reverse direction (already done in executeCardPlay for VAL_R values,
			// but Backstab uses its own value so we do it here)
			changeDirection();
			if (getActivePlayerCount() == 2) m_currPlayer = nextPlayer();
			return;
		}
		// Swap: swap hands with player before you, then reverse
		if (currVal == Card.VAL_R_SWAP) {
			if (virtualPlayer) waitABit(2);
			Player prev = getPlayerBefore(m_currPlayer);
			if (prev != null && prev != m_currPlayer) {
				swapHands(m_currPlayer, prev);
				promptUser(String.format(getString(R.string.msg_swap),
						seatToString(m_currPlayer.getSeat()), seatToString(prev.getSeat())));
			}
			changeDirection();
			if (getActivePlayerCount() == 2) m_currPlayer = nextPlayer();
			return;
		}

		// Skip
		if (currVal == Card.VAL_S || currVal == Card.VAL_S_DOUBLE) {
			if (virtualPlayer) waitABit(2);
			m_currPlayer = nextPlayer();
			if (currVal == Card.VAL_S_DOUBLE && getActivePlayerCount() > 2) {
				m_currPlayer = nextPlayer();
			}
			return;
		}

		// Draw 2
		if (currVal == Card.VAL_D) {
			if (virtualPlayer) waitABit(2); else m_currPlayer = nextPlayer();
			forceDraw(m_currPlayer, 2);
			if (virtualPlayer) m_currPlayer = nextPlayer();
			return;
		}

		// Spreader
		if (currVal == Card.VAL_D_SPREAD) {
			handleSpreader(virtualPlayer);
			return;
		}

		handleWildCards(currID, virtualPlayer);
		handleOtherSpecialCards(currID, virtualPlayer);
	}

	private void handleSpreader(boolean virtualPlayer) {
		boolean everyone = virtualPlayer;
		for (int i = 0; i < 4; i++) {
			Player p = m_players[i];
			if (p != m_currPlayer && p.getActive() && checkForShield(p.getHand())) {
				m_nextPlayerPreset = p;
				promptUser(String.format(getString(R.string.msg_has_blue_shield), seatToString(p.getSeat())));
				m_currPlayer = nextPlayer();
				everyone = false;
				break;
			}
		}
		if (everyone) { waitABit(2); forceDraw(m_currPlayer, 2); }
		for (int i = 1; i < getActivePlayerCount(); i++) {
			m_currPlayer = nextPlayer();
			forceDraw(m_currPlayer, 2);
		}
		if (everyone) m_currPlayer = nextPlayer();
	}

	private void handleWildCards(int currID, boolean virtualPlayer) {
		switch (currID) {
			case Card.ID_WILD_DRAW_FOUR:
				m_penalty.addCards(m_currCard, 4,
						virtualPlayer ? null : m_currPlayer,
						virtualPlayer ? m_currPlayer : getNextPlayer());
				redrawTable();
				break;

			case Card.ID_WILD_HD:
				m_penalty.addCards(m_currCard, 8,
						virtualPlayer ? null : m_currPlayer,
						virtualPlayer ? m_currPlayer : getNextPlayer());
				redrawTable();
				break;

			case Card.ID_WILD_DB: {
				Player origin = m_currPlayer;
				if (getActivePlayerCount() > 2) m_currPlayer = nextPlayer();
				m_penalty.addCards(m_currCard, 4,
						virtualPlayer ? null : origin,
						virtualPlayer ? m_currPlayer : getNextPlayer());
				redrawTable();
				break;
			}

			case Card.ID_WILD_HOS:
				m_penalty.addCards(m_currCard, 4,
						virtualPlayer ? null : m_currPlayer,
						virtualPlayer ? m_currPlayer : getNextPlayer());
				redrawTable();
				break;

			case Card.ID_WILD_MYSTERY:
				if (!virtualPlayer) handleMysteryDraw();
				break;
		}
	}

	private void handleMysteryDraw() {
		int prevVal = m_prevCard.getValue();
		int prevID  = m_prevCard.getID();
		if (prevID == Card.ID_YELLOW_69) {
			m_penalty.addCards(m_currCard, 69, m_currPlayer, getNextPlayer());
		} else if (prevVal > 0 && prevVal < 10) {
			m_penalty.addCards(m_currCard, prevVal, m_currPlayer, getNextPlayer());
		}
		redrawTable();
	}

	private void handleOtherSpecialCards(int currID, boolean virtualPlayer) {
		switch (currID) {
			case Card.ID_RED_0_HD:
				if (m_penalty.getVictim() == m_currPlayer) {
					m_penalty.setGeneratingPlayer(m_currPlayer);
					m_penalty.setVictim(getNextPlayer());
					promptUser(String.format(getString(R.string.msg_holy_defender),
							seatToString(m_penalty.getVictim().getSeat())));
				}
				break;

			case Card.ID_RED_2_GLASNOST:
				if (m_currPlayer.getHand().getNumCards() == 0) break;
				m_currPlayer.chooseVictim();
				if (m_stopping) break;
				int victimSeatGlasnost = m_currPlayer.getChosenVictim();
				if (virtualPlayer) m_penalty.setFaceup(m_currCard, null, m_players[victimSeatGlasnost - 1]);
				else               m_penalty.setFaceup(m_currCard, m_currPlayer, m_players[victimSeatGlasnost - 1]);
				m_nextPlayerPreset = m_players[victimSeatGlasnost - 1];
				redrawTable();
				break;

			case Card.ID_RED_5_MAGIC:
				if (m_penalty.getVictim() == m_currPlayer && m_penalty.hasHotDeath()) {
					m_penalty.removeHotDeath();
					promptUser(getString(R.string.msg_magic_5));
				}
				break;

			case Card.ID_GREEN_0_QUITTER:
				if (m_currPlayer.getHand().getNumCards() == 0) break;
				if (getActivePlayerCount() > 2) {
					if (virtualPlayer) m_penalty.setEject(m_currCard, null, m_currPlayer);
					else               m_penalty.setEject(m_currCard, m_currPlayer, getNextPlayer());
					redrawTable();
				}
				break;

			case Card.ID_GREEN_3_AIDS:
				if (m_penalty.getVictim() == m_currPlayer) {
					Player g = m_penalty.getGeneratingPlayer();
					m_penalty.setVictim(g);
					m_penalty.setGeneratingPlayer(m_currPlayer);
					m_penalty.setSecondaryVictim(m_currPlayer);
					m_nextPlayerPreset = g;
				}
				break;

			case Card.ID_BLUE_0_FUCK_YOU:
				handleFuckYou();
				break;

			case Card.ID_YELLOW_1_MAD:
				if (m_currPlayer.getHand().getNumCards() == 0) break;
				if (getActivePlayerCount() > 3) {
					m_currPlayer.chooseVictim();
					if (m_stopping) break;
					int victimSeatMad = m_currPlayer.getChosenVictim();
					m_penalty.setEject(m_currCard, m_currPlayer, m_players[victimSeatMad - 1]);
					m_penalty.setSecondaryVictim(m_currPlayer);
					redrawTable();
				}
				break;

			// ---- v3 new cards ----
			/*
			case Card.ID_RED_8_DODGE:
			case Card.ID_GREEN_8_DODGE:
			case Card.ID_BLUE_8_DODGE:
			case Card.ID_YELLOW_8_DODGE:
				// Dodge: redirect current attack to next player; if no active attack,
				// the card acts as a plain 8.
				if (m_penalty.getType() != Penalty.PENTYPE_NONE
						&& m_penalty.getVictim() == m_currPlayer) {
					Player dodgedOnto = getNextPlayer(m_currPlayer);
					m_penalty.setVictim(dodgedOnto);
					m_penalty.setGeneratingPlayer(m_currPlayer);
					promptUser(String.format(getString(R.string.msg_dodge),
							seatToString(dodgedOnto.getSeat())));
					redrawTable();
				}
				break;
			*/
			case Card.ID_GREEN_2_CLONE:
			case Card.ID_YELLOW_2_CLONE:
				// Clone: repeat the effect of the previous card onto the next player.
				if (!virtualPlayer && m_prevCard != null) {
					handleClone(virtualPlayer);
				}
				break;

			case Card.ID_BLUE_1_PING:
				// Ping: directed – chosen player draws exactly 1 card. Unblockable.
				if (!virtualPlayer) {
					m_currPlayer.chooseVictim();
					if (m_stopping) break;
					int pingVictimSeat = m_currPlayer.getChosenVictim();
					Player pingVictim = m_players[pingVictimSeat - 1];
					promptUser(String.format(getString(R.string.msg_ping),
							seatToString(m_currPlayer.getSeat()),
							seatToString(pingVictim.getSeat())));
					// Force draw bypasses all shields and modifiers per rules.
					Card drawn = drawCard();
					if (drawn != null) {
						pingVictim.addCardToHand(drawn, false);
						m_gt.moveCardToPlayer(drawn, pingVictim, 15);
					}
					waitABit(2);
				}
				break;
		}
	}

	// -------------------------------------------------------------------------
	// v3 card helpers
	// -------------------------------------------------------------------------

	/**
	 * Returns the active player who would play immediately before {@code from}
	 * in the current direction (i.e. next player in the reversed direction).
	 */
	private Player getPlayerBefore(Player from) {
		int saved = m_direction;
		m_direction = (m_direction == DIR_CLOCKWISE) ? DIR_CCLOCKWISE : DIR_CLOCKWISE;
		Player prev = getNextPlayer(from);
		m_direction = saved;
		return prev;
	}

	/** Swap the held cards of two players' hands. Field/table cards are not swapped per rules. */
	private void swapHands(Player a, Player b) {
		Hand ha = a.getHand();
		Hand hb = b.getHand();
		java.util.List<Card> cardsA = ha.getHeldCards();
		java.util.List<Card> cardsB = hb.getHeldCards();
		for (Card c : cardsA) ha.removeCard(c);
		for (Card c : cardsB) hb.removeCard(c);
		for (Card c : cardsB) a.addCardToHand(c, true);
		for (Card c : cardsA) b.addCardToHand(c, true);
	}

	/**
	 * Clone effect: re-apply m_prevCard's special effect targeting the next player.
	 * Field cards and Communism are excluded per rules.
	 */
	/**
	 * Clone effect: re-apply m_prevCard's special effect targeting the next player.
	 *
	 * Coverage:
	 *  - Wild-draw family (Draw-4, HD, DB, HOS, Mystery) → penalty cards
	 *  - Draw-2 → penalty cards
	 *  - Spreader → handleSpreader()
	 *  - Skip / Skip-Double / Reverse-Skip → skip next player(s)
	 *  - Reverse / Backstab / Swap → change direction
	 *  - Glasnost (Red-2) → faceup penalty on chosen victim
	 *  - Quitter (Green-0) → eject next player
	 *  - Mad (Yellow-1) → eject chosen victim (4-player only)
	 *  - Holy Defender (Red-0) → redirect current penalty to next player
	 *  - Fuck You (Blue-0) → send current penalty to next player
	 *  - Ping → force-draw 1 card onto chosen victim
	 *
	 * Intentional exclusions (documented):
	 *  - Clone itself            → no chain-clone (prevent infinite loops)
	 *  - Backstab already handled above via VAL_R_BACKSTAB path
	 *  - AIDS (Green-3)          → biological rules; cannot be cloned
	 *  - Yellow-69               → unique card identity, cannot be cloned
	 *  - Blue Shield             → passive card; cloning it has no effect
	 *  - Magic-5 (Red-5)         → defensive only; meaningless to clone offensively
	 *  - Mystery Wild            → handled separately via handleMysteryDraw()
	 */
	private void handleClone(boolean virtualPlayer) {
		if (m_prevCard == null) return;
		int prevID  = m_prevCard.getID();
		int prevVal = m_prevCard.getValue();

		// Guard: never chain-clone
		if (prevVal == Card.VAL_2_CLONE) return;

		promptUser(String.format(getString(R.string.msg_clone), cardToString(m_prevCard)));

		// ── Wild-draw family ──────────────────────────────────────────────────
		if (prevVal == Card.VAL_WILD_DRAW) {
			if (prevID == Card.ID_WILD_MYSTERY) {
				// Mystery reuses the card-before-the-clone (m_prevCard is Mystery,
				// so we re-enter handleMysteryDraw which reads m_prevCard itself).
				// Swap prevCard temporarily so Mystery reads the right context.
				handleMysteryDraw();
			} else {
				int amt;
				if      (prevID == Card.ID_WILD_HD) amt = 8;
				else if (prevID == Card.ID_WILD_DB) amt = 4;   // DB: full 4 onto next player
				else                                amt = 4;    // Draw-4, HOS → 4
				m_penalty.addCards(m_prevCard, amt, m_currPlayer, getNextPlayer());
				redrawTable();
			}
			return;
		}

		// ── Draw-2 ───────────────────────────────────────────────────────────
		if (prevVal == Card.VAL_D) {
			m_penalty.addCards(m_prevCard, 2, m_currPlayer, getNextPlayer());
			redrawTable();
			return;
		}

		// ── Spreader ─────────────────────────────────────────────────────────
		if (prevVal == Card.VAL_D_SPREAD) {
			handleSpreader(false);
			return;
		}

		// ── Skip family ───────────────────────────────────────────────────────
		if (prevVal == Card.VAL_S || prevVal == Card.VAL_S_DOUBLE || prevVal == Card.VAL_R_SKIP) {
			m_currPlayer = nextPlayer();
			if (prevVal == Card.VAL_S_DOUBLE && getActivePlayerCount() > 2) {
				m_currPlayer = nextPlayer();
			}
			return;
		}

		// ── Reverse family (direction change) ─────────────────────────────────
		if (prevVal == Card.VAL_R || prevVal == Card.VAL_R_BACKSTAB || prevVal == Card.VAL_R_SWAP) {
			changeDirection();
			// Backstab also forces a draw-2 onto the player now "before" us
			if (prevVal == Card.VAL_R_BACKSTAB) {
				Player prev = getPlayerBefore(m_currPlayer);
				if (prev != null && prev != m_currPlayer) {
					forceDraw(prev, 2);
				}
			}
			// Swap also swaps hands with the player now "before" us
			if (prevVal == Card.VAL_R_SWAP) {
				Player prev = getPlayerBefore(m_currPlayer);
				if (prev != null && prev != m_currPlayer) {
					swapHands(m_currPlayer, prev);
					promptUser(String.format(getString(R.string.msg_swap),
							seatToString(m_currPlayer.getSeat()), seatToString(prev.getSeat())));
				}
			}
			if (getActivePlayerCount() == 2) m_currPlayer = nextPlayer();
			return;
		}

		// ── Glasnost (Red-2) ──────────────────────────────────────────────────
		if (prevID == Card.ID_RED_2_GLASNOST) {
			if (m_currPlayer.getHand().getNumCards() == 0) return;
			m_currPlayer.chooseVictim();
			if (m_stopping) return;
			int victimSeat = m_currPlayer.getChosenVictim();
			m_penalty.setFaceup(m_prevCard, m_currPlayer, m_players[victimSeat - 1]);
			m_nextPlayerPreset = m_players[victimSeat - 1];
			redrawTable();
			return;
		}

		// ── Quitter (Green-0): eject next player ──────────────────────────────
		if (prevID == Card.ID_GREEN_0_QUITTER) {
			if (getActivePlayerCount() > 2) {
				m_penalty.setEject(m_prevCard, m_currPlayer, getNextPlayer());
				redrawTable();
			}
			return;
		}

		// ── Mad (Yellow-1): eject a chosen victim (4-player only) ────────────
		if (prevID == Card.ID_YELLOW_1_MAD) {
			if (m_currPlayer.getHand().getNumCards() == 0) return;
			if (getActivePlayerCount() > 3) {
				m_currPlayer.chooseVictim();
				if (m_stopping) return;
				int victimSeat = m_currPlayer.getChosenVictim();
				m_penalty.setEject(m_prevCard, m_currPlayer, m_players[victimSeat - 1]);
				m_penalty.setSecondaryVictim(m_currPlayer);
				redrawTable();
			}
			return;
		}

		// ── Holy Defender (Red-0): redirect active penalty to next player ─────
		if (prevID == Card.ID_RED_0_HD) {
			if (m_penalty.getType() != Penalty.PENTYPE_NONE
					&& m_penalty.getVictim() == m_currPlayer) {
				m_penalty.setGeneratingPlayer(m_currPlayer);
				m_penalty.setVictim(getNextPlayer());
				promptUser(String.format(getString(R.string.msg_holy_defender),
						seatToString(m_penalty.getVictim().getSeat())));
			}
			return;
		}

		// ── Fuck You (Blue-0): send active penalty to next player ─────────────
		if (prevID == Card.ID_BLUE_0_FUCK_YOU) {
			if (m_penalty.getType() != Penalty.PENTYPE_NONE
					&& m_penalty.getVictim() == m_currPlayer) {
				Player g = m_penalty.getGeneratingPlayer();
				if (g == null) g = getNextPlayer();
				m_penalty.setVictim(g);
				m_penalty.setGeneratingPlayer(m_currPlayer);
				promptUser(String.format(getString(R.string.msg_sending_penalty),
						seatToString(m_penalty.getVictim().getSeat())));
			}
			return;
		}

		// ── Ping: force-draw 1 onto a chosen victim ───────────────────────────
		if (prevID == Card.ID_BLUE_1_PING) {
			if (m_currPlayer.getHand().getNumCards() == 0) return;
			m_currPlayer.chooseVictim();
			if (m_stopping) return;
			int pingVictimSeat = m_currPlayer.getChosenVictim();
			Player pingVictim  = m_players[pingVictimSeat - 1];
			promptUser(String.format(getString(R.string.msg_ping),
					seatToString(m_currPlayer.getSeat()),
					seatToString(pingVictim.getSeat())));
			Card drawn = drawCard();
			if (drawn != null) {
				pingVictim.addCardToHand(drawn, false);
				m_gt.moveCardToPlayer(drawn, pingVictim, 15);
			}
			waitABit(2);
			return;
		}

		// ── Intentional no-ops ────────────────────────────────────────────────
		// AIDS, Yellow-69, Blue Shield, Magic-5: cloning these has no game effect.
		// Plain numeric cards also fall through here (clone of a 7 does nothing useful).
	}

	private void handleFuckYou() {
		if (m_penalty.getVictim() != m_currPlayer) return;
		Player g = m_penalty.getGeneratingPlayer();
		if (g == null) g = getNextPlayer();
		m_penalty.setVictim(g);
		m_penalty.setGeneratingPlayer(m_currPlayer);

		if (m_penalty.getOrigCard().getID() == Card.ID_WILD_DB
				&& getActivePlayerCount() > 2
				&& g != getNextPlayer()) {
			m_currPlayer = nextPlayer();
		} else if (m_penalty.getOrigCard().getID() == Card.ID_RED_2_GLASNOST) {
			m_nextPlayerPreset = g;
			m_currPlayer = nextPlayer();
		}
		promptUser(String.format(getString(R.string.msg_sending_penalty), seatToString(m_penalty.getVictim().getSeat())));
	}

	// -------------------------------------------------------------------------
	// Penalty assessment
	// -------------------------------------------------------------------------

	public void assessPenalty() {
		if (m_penalty.getType() == Penalty.PENTYPE_NONE) return;

		Player victim  = m_penalty.getVictim();
		Player victim2 = m_penalty.getSecondaryVictim();
		if (victim == null && victim2 == null) return;

		switch (m_penalty.getType()) {
			case Penalty.PENTYPE_CARD:  assessCardPenalty(victim, victim2);  break;
			case Penalty.PENTYPE_FACEUP: assessFaceUpPenalty(victim, victim2); break;
			case Penalty.PENTYPE_EJECT:  assessEjectPenalty(victim, victim2);  break;
		}
		m_penalty.reset();
	}

	private void assessCardPenalty(Player victim, Player victim2) {
		int numCards = m_penalty.getNumCards();
		if (victim2 != null) numCards = (numCards + 1) / 2;

		// Hoist reason so both the victim and victim2 blocks can reference it
		String reason = getPenaltyReason();

		if (victim != null) {
			promptUser(String.format(
					getString(R.string.msg_player_takes_penalty),
					seatToString(victim.getSeat()), numCards, reason));
			forceDraw(victim, numCards);
		} else {
			waitABit(2);
		}

		if (victim2 != null) {
			if (m_currPlayer != victim2) {
				m_nextPlayerPreset = victim2;
				m_currPlayer = nextPlayer();
			}
			int numCards2 = m_penalty.getNumCards() / 2;
			promptUser(String.format(
					getString(R.string.msg_player_takes_penalty),
					seatToString(victim2.getSeat()), numCards2, reason));
			forceDraw(victim2, numCards2);
		}
	}

	private String getPenaltyReason() {
		if (m_penalty == null || m_penalty.getOrigCard() == null) return "";
		return cardToString(m_penalty.getOrigCard());
	}

	private void assessFaceUpPenalty(Player victim, Player victim2) {
		if (victim != null) {
			victim.getHand().placeOnTable();
			if (m_players[SEAT_SOUTH - 1] instanceof HumanPlayer) {
				promptUser(String.format(getString(R.string.msg_player_faceup), seatToString(victim.getSeat())));
			}
		}
		if (victim2 != null) {
			if (m_currPlayer != victim2) { m_nextPlayerPreset = victim2; m_currPlayer = nextPlayer(); }
			victim2.getHand().placeOnTable();
			if (m_players[SEAT_SOUTH - 1] instanceof HumanPlayer) {
				promptUser(String.format(getString(R.string.msg_player_faceup), seatToString(victim2.getSeat())));
			}
		} else {
			m_nextPlayerPreset = m_penalty.getGeneratingPlayer() != null
					? m_penalty.getGeneratingPlayer() : m_dealer;
			m_currPlayer = nextPlayer();
		}
	}

	private void assessEjectPenalty(Player victim, Player victim2) {
		if (victim != null) {
			promptUser(String.format(
					getString(R.string.msg_player_ejected_by),
					seatToString(victim.getSeat()),
					m_penalty.getGeneratingPlayer() != null
							? seatToString(m_penalty.getGeneratingPlayer().getSeat()) : "?"));
			ejectPlayer(victim);
		}
		if (victim2 != null) {
			ejectPlayer(victim2);
		}
		if (getActivePlayerCount() == 1) {
			m_penalty.reset();
			for (int i = 0; i < 4; i++) {
				if (m_players[i].getActive()) {
					m_players[i].getHand().reset();
					finishRound(m_players[i]);
					return;
				}
			}
		}
	}

	private void ejectPlayer(Player p) {
		m_nextPlayerPreset = p;
		m_currPlayer = nextPlayer();
		p.setActive(false);
		promptUser(String.format(getString(R.string.msg_player_ejected), seatToString(p.getSeat())));
		if (p == m_players[SEAT_SOUTH - 1]) showFastForwardButton(true);
	}

	// -------------------------------------------------------------------------
	// Round finish / scoring
	// -------------------------------------------------------------------------

	public void finishRound(Player winner) {
		m_fastForward = false;
		showFastForwardButton(false);
		showMenuButton(false);
		m_gt.startDirectionIndicatorAnimation(m_direction, Color.TRANSPARENT);

		m_dealer = winner;
		calculateScore(winner);
		m_roundComplete = true;

		for (Player p : m_players) {
			p.setActive(true);
			p.getHand().reveal();
		}

		promptUser(String.format(getString(R.string.msg_declare_round_winner), seatToString(winner.getSeat())));

		int minScore    = Integer.MAX_VALUE;
		int minPlayer   = 0;
		int endScore    = m_standardRules ? 500 : 1000;
		for (int i = 0; i < 4; i++) {
			int s = m_players[i].getTotalScore();
			if (s < minScore) { minScore = s; minPlayer = i; }
			if (s >= endScore) m_gameOver = true;
		}

		m_snapshot = this.toJSON();
		if (!m_gameOver) {
			showNextRoundButton(true);
		} else {
			m_winner = m_players[minPlayer].getSeat();
			redrawTable();
		}
	}

	private void calculateScore(Player winner) {
		int maxScore  = 0;
		int[] scores  = new int[4];

		for (int i = 0; i < 4; i++) {
			Hand h = m_players[i].getHand();
			scores[i] = checkForAllBastardCards(h) ? 0 : h.calculateValue(true);
			if (scores[i] > maxScore) maxScore = scores[i];
		}

		// Shitter penalty: player with the Shitter inherits the highest score.
		for (int i = 0; i < 4; i++) {
			if (!checkForAllBastardCards(m_players[i].getHand())) {
				Hand h = m_players[i].getHand();
				for (int j = 0; j < h.getNumCards(); j++) {
					if (h.getCard(j).getID() == Card.ID_YELLOW_0_SHITTER) {
						scores[i] = maxScore;
					}
				}
			}
			m_players[i].setLastScore(scores[i]);
			int virusPen = (m_players[i] == winner) ? 0 : m_players[i].getVirusPenalty();
			m_players[i].setLastVirusPenalty(virusPen);
			m_players[i].setTotalScore(m_players[i].getTotalScore() + scores[i] + virusPen);
		}
	}

	// -------------------------------------------------------------------------
	// Draw pile management
	// -------------------------------------------------------------------------

	public Card drawCard() {
		if (m_drawPile.getNumCards() == 0) {
			return rolloverDiscardPile() > 0 ? m_drawPile.drawCard() : null;
		}
		Card c = m_drawPile.drawCard();
		// Proactively roll over if draw pile is now empty and discard has enough cards.
		if (m_drawPile.getNumCards() == 0 && m_discardPile.getNumCards() >= 5) {
			rolloverDiscardPile();
		}
		return c;
	}

	public int rolloverDiscardPile() {
		int numPlayed = m_discardPile.getNumCards();
		if (numPlayed > 1) {
			Card topCard = m_discardPile.drawCard();
			String msg = (numPlayed > 2)
					? String.format(getString(R.string.msg_shuffling_discard), numPlayed - 1)
					: getString(R.string.msg_shuffling_discard_1);
			promptUser(msg);
			while (m_discardPile.getNumCards() > 0) {
				Card tc = m_discardPile.drawCard();
				tc.setFaceUp(false);
				m_drawPile.addCard(tc, true);
			}
			m_drawPile.shuffle();
			m_discardPile.addCard(topCard, true);
			return numPlayed - 1;
		}
		if (!m_forceDrawing) {
			redrawTable();
			promptUser(getString(R.string.msg_discard_empty));
		}
		return 0;
	}

	// -------------------------------------------------------------------------
	// Force-draw helper
	// -------------------------------------------------------------------------

	void forceDraw(Player p, int numCards) {
		if (numCards <= 0) return;

		// Check for the Luck of the Irish card.
		Hand h = p.getHand();
		for (int i = 0; i < h.getNumCards(); i++) {
			if (h.getCard(i).getID() == Card.ID_GREEN_4_IRISH) {
				numCards--;
				h.getCard(i).setFaceUp(true);
				promptUser(getString(R.string.msg_luck_of_irish));
				break;
			}
		}

		promptUser(String.format(getString(R.string.msg_player_drawing),
				seatToString(p.getSeat()), numCards));
		Log.d(TAG, String.format(getString(R.string.msg_player_drawing),
				seatToString(p.getSeat()), numCards));

		if (numCards > m_drawPile.getNumCards()) rolloverDiscardPile();

		m_forceDrawing = true;
		boolean notEnough = false;
		for (int i = 0; i < numCards; i++) {
			Card c = drawCard();
			if (c == null) { notEnough = true; break; }
			p.addCardToHand(c, false);
			m_gt.moveCardToPlayer(c, p, 15);
		}
		waitABit(2);
		m_forceDrawing = false;

		if (notEnough) promptUser(getString(R.string.msg_discard_empty));
	}

	// -------------------------------------------------------------------------
	// Bastard-card checks
	// -------------------------------------------------------------------------

	public boolean checkForAllBastardCards(Hand h) {
		int count = 0;
		for (int i = 0; i < h.getNumCards(); i++) {
			int id = h.getCard(i).getID();
			if (id == Card.ID_RED_0_HD)        count++;
			if (id == Card.ID_GREEN_0_QUITTER)  count++;
			if (id == Card.ID_BLUE_0_FUCK_YOU)  count++;
			if (id == Card.ID_YELLOW_0_SHITTER) count++;
		}
		return count == 4;
	}

	public void gotAllBastardCards(Player p) {
		promptUser(String.format(getString(R.string.msg_all_bastard_cards), seatToString(p.getSeat())));
	}

	// -------------------------------------------------------------------------
	// Shield / defender checks
	// -------------------------------------------------------------------------

	public boolean checkForShield(Hand h) {
		for (int i = 0; i < h.getNumCards(); i++) {
			if (h.getCard(i).getID() == Card.ID_BLUE_2_SHIELD) {
				h.getCard(i).setFaceUp(true);
				return true;
			}
		}
		return false;
	}

	public int checkForDefender(Hand h) {
		Card orig     = m_penalty.getOrigCard();
		int  origID   = orig.getID();
		int  origVal  = orig.getValue();
		int  count    = 0;

		if (origVal == Card.VAL_WILD_DRAW && origID != Card.ID_WILD_HOS) {
			for (int i = 0; i < h.getNumCards(); i++) {
				int id  = h.getCard(i).getID();
				int val = h.getCard(i).getValue();
				if (m_penalty.getSecondaryVictim() == null
						&& val == Card.VAL_WILD_DRAW
						&& origID != Card.ID_WILD_MYSTERY
						&& id     != Card.ID_WILD_MYSTERY) count++;
				if (id == Card.ID_RED_0_HD)         count++;
				if (id == Card.ID_BLUE_0_FUCK_YOU)  count++;
				if (id == Card.ID_GREEN_3_AIDS)      count++;
				if (id == Card.ID_RED_5_MAGIC && origID == Card.ID_WILD_HD) count++;
			}
		}

		if (origID == Card.ID_RED_2_GLASNOST || origID == Card.ID_GREEN_0_QUITTER) {
			for (int i = 0; i < h.getNumCards(); i++) {
				int id = h.getCard(i).getID();
				if (id == Card.ID_RED_0_HD || id == Card.ID_GREEN_3_AIDS || id == Card.ID_BLUE_0_FUCK_YOU) {
					count++;
				}
			}
		}
		return count;
	}

	// -------------------------------------------------------------------------
	// Utility helpers
	// -------------------------------------------------------------------------

	public int getActivePlayerCount() {
		int count = 0;
		for (Player p : m_players) if (p.getActive()) count++;
		return count;
	}

	public boolean roundIsActive() {
		return !m_waitingToStartRound && !m_gameOver;
	}

	public int getDelay() {
		if (m_fastForward) return 0;
		if (!m_players[SEAT_SOUTH - 1].getActive()) return 250;
		switch (m_go.getPauseLength()) {
			case 0: return  700;
			case 1: return 1200;
			case 2: return 1700;
			case 3: return 2900;
			default: return 4000;
		}
	}

	public void waitABit() { waitABit(1); }

	public void waitABit(int div) {
		int delay = getDelay() / div;
		if (delay == 0) return;
		try { Thread.sleep(delay); }
		catch (InterruptedException ignored) {}
	}

	// -------------------------------------------------------------------------
	// UI interaction
	// -------------------------------------------------------------------------

	public void drawPileTapped() {
		if (m_waitingToStartRound) { m_waitingToStartRound = false; return; }
		if (!(m_currPlayer instanceof HumanPlayer) || m_penalty.getType() != Penalty.PENTYPE_NONE) return;
		((HumanPlayer) m_currPlayer).turnDecisionDrawCard();
	}

	public void discardPileTapped() {
		if (m_waitingToStartRound) m_waitingToStartRound = false;
	}

	public void humanPlayerPass() {
		if (m_currPlayer instanceof HumanPlayer) {
			((HumanPlayer) m_currPlayer).turnDecisionPass();
		}
	}

	void promptUser(String msg) { promptUser(msg, false); }

	void promptUser(String msg, boolean wait) {
		if (m_fastForward) return;
		m_ga.runOnUiThread(() -> {
			Log.d(TAG, "[promptUser] " + msg);
			m_gt.Toast(msg);
		});
		if (wait) waitABit();
	}

	public void promptForNumCardsToDeal() {
		m_ga.runOnUiThread(() -> m_gt.PromptForNumCardsToDeal());
	}

	public void promptForVictim() {
		m_ga.runOnUiThread(() -> m_gt.PromptForVictim());
	}

	public void promptForColor() {
		m_gt.PromptForColor();
	}

	private void redrawTable() {
		m_ga.runOnUiThread(() -> m_gt.RedrawTable());
	}

	private void showNextRoundButton(boolean show) {
		m_ga.runOnUiThread(() -> m_gt.showNextRoundButton(show));
	}

	private void showFastForwardButton(boolean show) {
		m_ga.runOnUiThread(() -> m_gt.showFastForwardButton(show));
	}

	private void showMenuButton(boolean show) {
		m_ga.runOnUiThread(() -> m_gt.showMenuButton(show));
	}

	private void changeDirection() {
		m_direction = (m_direction == DIR_CLOCKWISE) ? DIR_CCLOCKWISE : DIR_CLOCKWISE;
		Log.d(TAG, "direction change → " + directionToString(m_direction));
	}

	// -------------------------------------------------------------------------
	// String helpers
	// -------------------------------------------------------------------------

	public String cardToString(Card c) {
		return c.toString(m_gt.getContext(), m_go.getFamilyFriendly());
	}

	private String directionToString(int dir) {
		return getString(dir == DIR_CLOCKWISE ? R.string.direction_clockwise : R.string.direction_counterclockwise);
	}

	private String colorToString(int c) {
		switch (c) {
			case Card.COLOR_BLUE:   return getString(R.string.cardcolor_blue);
			case Card.COLOR_GREEN:  return getString(R.string.cardcolor_green);
			case Card.COLOR_RED:    return getString(R.string.cardcolor_red);
			case Card.COLOR_YELLOW: return getString(R.string.cardcolor_yellow);
			default: return "";
		}
	}

	public String seatToString(int seat) {
		switch (seat) {
			case SEAT_NORTH: return getString(R.string.seat_north);
			case SEAT_EAST:  return getString(R.string.seat_east);
			case SEAT_SOUTH: return getString(R.string.seat_south);
			case SEAT_WEST:  return getString(R.string.seat_west);
			default: return "";
		}
	}

	private void logCardPlay(Player p, Card c) {
		Log.d(TAG, seatToString(p.getSeat()) + " plays " + cardToString(c));
	}

	/**
	 * Convenience accessor: retrieve a string resource without needing a Context reference
	 * in Player subclasses.
	 */
	public String getString(int resId) {
		return m_gt.getContext().getString(resId);
	}
}