package com.smorgasbork.hotdeath;

import java.util.Random;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * AI-controlled player. Skill levels:
 *   0 = Weak    – plays highest-value valid card; victim chosen naively
 *   1 = Strong  – avoids wilds while opponents have many cards; smarter heuristics;
 *                 targets the opponent closest to winning
 *   2 = Expert  – all of Strong, plus color-balance optimisation, endgame pressure
 *                 cards (skip/skip-double to protect own win), and smarter v3 card use
 *
 * Key improvements over previous version:
 *  - Threat-aware victim selection: always target the player with fewest cards
 *    (adjusted by aggression toward SEAT_SOUTH) rather than lowest score
 *  - Endgame-pressure scoring: when the AI itself is close to winning, heavily
 *    bonus Skip/Skip-Double/Reverse-Skip cards that protect its position
 *  - Defender play: if under a large draw penalty, strongly prefer playing a
 *    defender card (Holy Defender, FuckYou, Magic5) immediately
 *  - Wild color choice: prefer the color the AI holds MOST of so it can keep
 *    playing after declaring (unchanged behaviour, now applied at Weak too)
 *  - Backstab/Swap/Clone/Ping scoring: v3 cards now get proper heuristic values
 *  - Draw-pile awareness: if draw pile is small, hoard wilds less aggressively
 *  - Ping victim targeting: directs Ping at the opponent with the fewest cards
 *  - Color balance caching: baseline balance computed once per turn, not per candidate
 */
public class ComputerPlayer extends Player {

	// How many cards remaining in AI's hand counts as "endgame" pressure
	private static final int ENDGAME_HAND_SIZE = 3;
	// Bonus added to skip-family cards when protecting own endgame
	private static final int ENDGAME_SKIP_BONUS = 60;
	// Minimum draw-penalty size before we strongly prefer defenders
	private static final int DEFEND_THRESHOLD = 4;

	// ── Color-balance cache (invalidated at start of each playCard() call) ──
	// Avoids recomputing the baseline balance once per candidate card.
	private double m_cachedBaseBalance  = Double.NaN;
	private int    m_cachedHandSnapshot = -1; // m_hand.getNumCards() when cache was set

	public ComputerPlayer(Game g, GameOptions go) {
		super(g, go);
	}

	public ComputerPlayer(JSONObject o, Game g, GameOptions go) throws JSONException {
		super(o, g, go);
	}

	// -------------------------------------------------------------------------
	// Turn lifecycle
	// -------------------------------------------------------------------------

	@Override
	public boolean startTurn() {
		if (!super.startTurn()) {
			return false;
		}

		m_game.waitABit();

		if (!m_hand.hasValidCards(m_game)) {
			if (m_hasTriedDrawing) {
				m_wantsToPass = true;
			} else {
				m_wantsToDraw = true;
			}
			return false;
		}

		playCard();
		return true;
	}

	@Override
	public void addCardToHand(Card c, boolean instant) {
		super.addCardToHand(c, instant);
		readAggressionAndSkill();
		invalidateBalanceCache();
	}

	// -------------------------------------------------------------------------
	// Decisions
	// -------------------------------------------------------------------------

	@Override
	public void chooseNumCardsToDeal() {
		m_numCardsToDeal = new Random().nextInt(11) + 5;
	}

	@Override
	public int chooseColor() {
		m_game.waitABit();

		if (m_hand.getNumCards() == 0) {
			return Card.COLOR_RED;
		}

		// All skill levels: pick the color we hold the most of so follow-up
		// plays are more likely
		int maxCount = 0;
		int maxColor = 0;
		for (int color = Card.COLOR_RED; color < Card.COLOR_WILD; color++) {
			int cnt = m_hand.countSuit(color);
			if (cnt > maxCount) {
				maxCount = cnt;
				maxColor = color;
			}
		}

		m_chosenColor = (maxColor == 0) ? Card.COLOR_RED : maxColor;
		return m_chosenColor;
	}

	@Override
	public void chooseVictim() {
		m_game.waitABit();

		if (m_skill == 0) {
			chooseVictimWeak();
			return;
		}

		// Strong / Expert: target the player with the FEWEST cards remaining
		// (i.e. closest to winning), adjusted by aggression toward SEAT_SOUTH.
		// Fewest-card targeting is more tactically sound than lowest-score targeting
		// because it directly disrupts imminent wins.
		int minCards  = Integer.MAX_VALUE;
		int minPlayer = 0;

		for (int i = 3; i >= 0; i--) {
			Player p = m_game.getPlayer(i);
			if (p == this || !p.getActive()) continue;

			int cardCount = p.getHand().getNumCards();
			// Bias: treat SEAT_SOUTH as if they have fewer cards when aggression is high
			if (i == Game.SEAT_SOUTH - 1) {
				cardCount -= m_aggression * 2;
			}
			if (cardCount < minCards) {
				minPlayer = i + 1;
				minCards  = cardCount;
			}
		}
		m_chosenVictim = minPlayer;
	}

	/**
	 * Victim selection for Ping specifically: always targets the active opponent
	 * with the fewest cards, regardless of skill level, since Ping is unblockable
	 * and wasting it on a player with a large hand is always suboptimal.
	 */
	private void choosePingVictim() {
		m_game.waitABit();

		int minCards  = Integer.MAX_VALUE;
		int minPlayer = 0;

		for (int i = 3; i >= 0; i--) {
			Player p = m_game.getPlayer(i);
			if (p == this || !p.getActive()) continue;

			int cardCount = p.getHand().getNumCards();
			// Same SEAT_SOUTH aggression bias as normal victim selection
			if (i == Game.SEAT_SOUTH - 1) {
				cardCount -= m_aggression * 2;
			}
			if (cardCount < minCards) {
				minPlayer = i + 1;
				minCards  = cardCount;
			}
		}

		// Fall back to generic victim selection if no target found
		m_chosenVictim = (minPlayer != 0) ? minPlayer : defaultFallbackVictim();
	}

	/** Returns any active opponent seat, used as a last-resort fallback. */
	private int defaultFallbackVictim() {
		for (int i = 0; i < 4; i++) {
			Player p = m_game.getPlayer(i);
			if (p != this && p.getActive()) return i + 1;
		}
		return 0;
	}

	/** Weak victim selection – clockwise or counter-clockwise depending on aggression. */
	private void chooseVictimWeak() {
		int[] order = (m_aggression > 3)
				? new int[]{0, 1, 2, 3}   // clockwise from SOUTH, aggressive
				: new int[]{3, 2, 1, 0};  // counter-clockwise from EAST

		for (int i : order) {
			if (m_seat == i + 1) continue;
			if (m_game.getPlayer(i).getActive()) {
				m_chosenVictim = i + 1;
				return;
			}
		}
	}

	// -------------------------------------------------------------------------
	// Card selection
	// -------------------------------------------------------------------------

	public void playCard() {
		readAggressionAndSkill();
		invalidateBalanceCache();   // fresh turn → stale cache

		m_wantsToPass = false;
		m_hand.calculateValue();

		// If we just drew, play it if legal – otherwise pass.
		if (m_lastDrawn != null) {
			if (m_game.checkCard(m_hand, m_lastDrawn)) {
				m_playingCard     = m_lastDrawn;
				m_wantsToPlayCard = true;
			} else {
				m_wantsToPass = true;
			}
			return;
		}

		int opponentMinCards = getMinCardsRemaining();
		int wildCount        = countCardsOfColor(Card.COLOR_WILD);
		int penalty          = m_game.getPenalty().getNumCards();

		// Pre-compute the baseline color balance once for the whole candidate loop.
		// adjustForColorBalance() will read m_cachedBaseBalance rather than
		// recomputing it for every card.
		if (m_skill >= 2) {
			ensureBalanceCache();
		}

		int  maxTestVal = Integer.MIN_VALUE;
		Card bestCard   = null;

		for (int i = 0; i < m_hand.getNumCards(); i++) {
			Card tc = m_hand.getCard(i);
			if (!m_game.checkCard(m_hand, tc)) continue;

			// Always play a defender immediately when under a heavy penalty.
			if (m_game.getLastCardCheckedIsDefender() && penalty >= DEFEND_THRESHOLD) {
				bestCard = tc;
				break;
			}
			// TODO: improve this AI
			//   - throw the mystery draw on numbered cards
			//   - don't throw MAD when point count in hand is too high (unless player count < 4)
			//   - consider the damage of offensive cards; look to hit players with low card counts
			//   - advanced: throw the MAD card to catch an opponent and force him over 1000 points,
			//     even if you have hundreds of points, if you're assured of winning
			int testVal = scoreCandidate(tc, opponentMinCards, wildCount, penalty);

			if (testVal >= maxTestVal) {
				maxTestVal = testVal;
				bestCard   = tc;
			}
		}

		if (bestCard != null) {
			// If the chosen card is Ping, use dedicated victim selection so it
			// always targets the most threatening opponent.
			if (bestCard.getValue() == Card.VAL_PING) {
				choosePingVictim();
			}
			m_playingCard     = bestCard;
			m_wantsToPlayCard = true;
		} else {
			m_wantsToDraw = true;
		}
	}

	/**
	 * Assigns a heuristic value to playing {@code tc}. Higher = more desirable.
	 *
	 * @param tc               candidate card
	 * @param opponentMinCards smallest hand size among active opponents
	 * @param wildCount        number of wild cards in AI's hand
	 * @param currentPenalty   draw-card penalty currently on the table (0 if none)
	 */
	private int scoreCandidate(Card tc, int opponentMinCards, int wildCount, int currentPenalty) {
		if (m_skill == 0) {
			return tc.getCurrentValue();
		}

		// ── Skill 1+ base value ──────────────────────────────────────────────
		int testVal;
		if (tc.getColor() == Card.COLOR_WILD) {
			// Hold wilds while opponents still have many cards AND draw pile is healthy.
			boolean drawPileHealthy    = m_game.getDrawPile().getNumCards() > 15;
			boolean opponentsHaveCards = wildCount < opponentMinCards - 1;
			testVal = (opponentsHaveCards && drawPileHealthy) ? 0 : tc.getCurrentValue();
		} else {
			testVal = tc.getCurrentValue();
		}

		// ── Standard heuristic adjustments ──────────────────────────────────
		testVal = adjustForMad(tc, testVal);
		testVal = adjustForSixtyNine(tc, testVal);
		testVal = adjustForMysteryDraw(tc, testVal);
		testVal = adjustForV3Cards(tc, testVal, opponentMinCards);

		// ── Skill 2: color balance + endgame pressure ────────────────────────
		if (m_skill >= 2) {
			testVal = adjustForColorBalance(tc, testVal, opponentMinCards);
			testVal = adjustForEndgamePressure(tc, testVal);
		}

		// ── All skills: under-penalty defender bonus ─────────────────────────
		// (Heavy bonus so defenders almost always win the comparison)
		if (currentPenalty >= DEFEND_THRESHOLD) {
			testVal = adjustForDefender(tc, testVal, currentPenalty);
		}

		return testVal;
	}

	// -------------------------------------------------------------------------
	// Individual heuristic adjustors
	// -------------------------------------------------------------------------

	private int adjustForMad(Card tc, int testVal) {
		if (tc.getID() != Card.ID_YELLOW_1_MAD || m_game.getActivePlayerCount() <= 3) {
			return testVal;
		}
		int newHandValue = m_hand.calculateValue(false, tc);
		if      (newHandValue < 10) return testVal + 100;
		else if (newHandValue < 20) return testVal + 70;
		else if (newHandValue < 50) return 0;
		else                        return testVal - 20;
	}

	private int adjustForSixtyNine(Card tc, int testVal) {
		if (tc.getID() != Card.ID_YELLOW_69) return testVal;
		int oldVal = m_hand.calculateValue();
		int newVal = m_hand.calculateValue(false, tc);
		return oldVal - newVal;
	}

	private int adjustForMysteryDraw(Card tc, int testVal) {
		if (tc.getID() != Card.ID_WILD_MYSTERY) return testVal;
		Card lpc = m_game.getLastPlayedCard();
		if (lpc == null) return testVal;
		int lpv = lpc.getValue();
		if (lpc.getID() == Card.ID_YELLOW_69) return testVal + 200;
		if (lpv > 0) {
			if (lpv < 5)  return testVal + 15;
			if (lpv < 8)  return testVal + 30;
			if (lpv < 10) return testVal + 50;
		}
		return testVal;
	}

	/**
	 * Scores v3 new cards:
	 *  - Backstab: bonus when targeting the player with most cards (acts as Draw-2 + Reverse)
	 *  - Swap:     big bonus when AI has more cards than the target (swap is beneficial);
	 *              penalty if AI has fewer cards than target
	 *  - Clone:    bonus proportional to the value of the previously played card
	 *  - Ping:     bonus scales with how few cards the most-threatened opponent holds;
	 *              the actual victim is chosen separately via choosePingVictim()
	 */
	private int adjustForV3Cards(Card tc, int testVal, int opponentMinCards) {
		int val = tc.getValue();

		// ── Backstab ─────────────────────────────────────────────────────────
		if (val == Card.VAL_R_BACKSTAB) {
			// Backstab draws 2 onto the player BEFORE us and reverses.
			// Valuable when that player has few cards (threatens to win).
			Player before = getPlayerBefore(m_seat);
			if (before != null) {
				int theirCards = before.getHand().getNumCards();
				if      (theirCards <= 3) testVal += 50;   // very threatening player
				else if (theirCards <= 6) testVal += 25;
			}
			return testVal;
		}

		// ── Swap ─────────────────────────────────────────────────────────────
		if (val == Card.VAL_SWAP) {
			Player before = getPlayerBefore(m_seat);
			if (before != null) {
				int myCards    = m_hand.getNumCards();
				int theirCards = before.getHand().getNumCards();
				int delta      = theirCards - myCards; // positive = we gain cards if we swap
				// We want to swap when we have more cards than them (delta < 0 = we lose cards)
				// or when they are about to win (small hand)
				if      (delta < -3) testVal += 60;    // swap rids us of a big hand
				else if (delta <  0) testVal += 30;
				else if (delta >  3) testVal -= 40;    // would give us even more cards
				else                 testVal -= 10;
			}
			return testVal;
		}

		// ── Clone ─────────────────────────────────────────────────────────────
		if (val == Card.VAL_CLONE) {
			Card prev = m_game.getLastPlayedCard();
			if (prev == null) return testVal - 10;     // no previous card, weaker play
			int prevVal = prev.getValue();
			// Clone is great when the previous card was an attack
			if      (prevVal == Card.VAL_WILD_DRAW)    testVal += 80;
			else if (prevVal == Card.VAL_D)            testVal += 40;
			else if (prevVal == Card.VAL_D_SPREAD)     testVal += 70;
			else if (prevVal == Card.VAL_S
					|| prevVal == Card.VAL_S_DOUBLE)     testVal += 30;
			else                                       testVal += 10;
			return testVal;
		}

		// ── Ping ─────────────────────────────────────────────────────────────
		if (val == Card.VAL_PING) {
			// Unblockable directed draw-1.
			// Base bonus for always being useful; scale up when opponentMinCards is
			// very low because forcing even one extra card onto a near-winning opponent
			// is high-value. The actual victim is picked in choosePingVictim() later.
			testVal += 15;
			if      (opponentMinCards <= 1) testVal += 40;  // opponent is about to win
			else if (opponentMinCards <= 2) testVal += 25;
			else if (opponentMinCards <= 3) testVal += 10;
			return testVal;
		}

		return testVal;
	}

	/**
	 * When the AI itself has very few cards, heavily prefer skip/reverse-skip cards
	 * so opponents are less likely to win before the AI can go out.
	 */
	private int adjustForEndgamePressure(Card tc, int testVal) {
		if (m_hand.getNumCards() > ENDGAME_HAND_SIZE) return testVal;

		int val = tc.getValue();
		// Skip-family cards buy the AI another turn
		if (val == Card.VAL_S || val == Card.VAL_S_DOUBLE || val == Card.VAL_R_SKIP) {
			testVal += ENDGAME_SKIP_BONUS;
		}
		// Draw cards punish opponents right before we might win
		if (val == Card.VAL_D || val == Card.VAL_D_SPREAD) {
			testVal += ENDGAME_SKIP_BONUS / 2;
		}
		// Prefer NOT to play a wild when we have a matching color card —
		// saves the wild for a future turn if we can win without it
		if (tc.getColor() == Card.COLOR_WILD) {
			int colorMatches = m_hand.countSuit(m_game.getCurrColor());
			if (colorMatches > 0) testVal -= 20;
		}
		return testVal;
	}

	/**
	 * When under a draw penalty, heavily reward playing a valid defender.
	 * The game's checkCard() already vetted this card as a legal defender play;
	 * we just need to make sure it wins the scoring comparison.
	 */
	private int adjustForDefender(Card tc, int testVal, int penaltyCards) {
		int     id         = tc.getID();
		boolean isDefender = id == Card.ID_RED_0_HD
				|| id == Card.ID_BLUE_0_FUCK_YOU
				|| id == Card.ID_GREEN_3_AIDS
				|| id == Card.ID_RED_5_MAGIC;
		// Wild-draw stacking also counts
		boolean isStack = tc.getValue() == Card.VAL_WILD_DRAW
				&& m_game.getPenalty().getOrigCard() != null
				&& m_game.getPenalty().getOrigCard().getValue() == Card.VAL_WILD_DRAW;

		if (isDefender || isStack) {
			// Scale bonus with severity of penalty — the worse it is, the more we want out
			testVal += penaltyCards * 15;
		}
		return testVal;
	}

	/**
	 * Adjusts score based on how playing {@code tc} changes the hand's color balance.
	 *
	 * Uses the cached baseline balance so this method is O(n) rather than O(n²)
	 * across the full candidate loop.
	 */
	private int adjustForColorBalance(Card tc, int testVal, int opponentMinCards) {
		boolean considerBalance = (opponentMinCards + m_aggression / 3) > 3;
		if (!considerBalance) return testVal;

		double improvement = computeChangeInColorBalance(tc);
		if      (improvement < -0.5)  testVal += 40;
		else if (improvement < -0.25) testVal += 20;
		else if (improvement >  0.5)  testVal -= 40;
		else if (improvement >  0.25) testVal -= 20;
		return testVal;
	}

	// -------------------------------------------------------------------------
	// Color balance helpers
	// -------------------------------------------------------------------------

	/**
	 * Returns the fractional change in color balance caused by removing {@code c}.
	 * Positive = more imbalanced (worse); negative = more balanced (better).
	 *
	 * Reads the cached baseline so the per-card O(n) inner loop is not repeated
	 * for every candidate.
	 */
	public double computeChangeInColorBalance(Card c) {
		double before = getCachedBaseBalance();
		double after  = computeColorBalance(c);
		if (before == 0) return 0;
		return (after - before) / before;
	}

	/**
	 * Computes the standard deviation of the four color counts, optionally
	 * excluding one card (for look-ahead scoring).
	 *
	 * When called from {@link #computeChangeInColorBalance} the "before" value
	 * comes from the cache; only the "after" (with exclusion) requires a full scan.
	 */
	public double computeColorBalance(Card exclude) {
		int[] totals = new int[4];
		for (int i = 0; i < m_hand.getNumCards(); i++) {
			int color = m_hand.getCard(i).getColor();
			if (color >= Card.COLOR_RED && color <= Card.COLOR_YELLOW) {
				totals[color - Card.COLOR_RED]++;
			}
		}
		if (exclude != null) {
			int ec = exclude.getColor();
			if (ec >= Card.COLOR_RED && ec <= Card.COLOR_YELLOW) {
				totals[ec - Card.COLOR_RED]--;
			}
		}
		double avg     = (totals[0] + totals[1] + totals[2] + totals[3]) / 4.0;
		double balance = 0;
		for (int t : totals) {
			balance += Math.pow(t - avg, 2);
		}
		return Math.sqrt(balance);
	}

	// ── Cache management ──────────────────────────────────────────────────────

	/**
	 * Populates the balance cache if it is stale.
	 * The snapshot key is the current hand size; any add/remove invalidates it.
	 */
	private void ensureBalanceCache() {
		int currentSize = m_hand.getNumCards();
		if (Double.isNaN(m_cachedBaseBalance) || m_cachedHandSnapshot != currentSize) {
			m_cachedBaseBalance  = computeColorBalance(null);
			m_cachedHandSnapshot = currentSize;
		}
	}

	/** Returns the cached baseline balance, computing it first if necessary. */
	private double getCachedBaseBalance() {
		ensureBalanceCache();
		return m_cachedBaseBalance;
	}

	/** Marks the cache as stale. Call whenever the hand composition changes. */
	private void invalidateBalanceCache() {
		m_cachedBaseBalance  = Double.NaN;
		m_cachedHandSnapshot = -1;
	}

	// -------------------------------------------------------------------------
	// Utility helpers
	// -------------------------------------------------------------------------

	/** Returns the smallest hand size among active opponents. */
	public int getMinCardsRemaining() {
		int min = Integer.MAX_VALUE;
		for (int i = 0; i < 4; i++) {
			Player p = m_game.getPlayer(i);
			if (p == this || !p.getActive()) continue;
			min = Math.min(min, p.getHand().getNumCards());
		}
		return min;
	}

	private int countCardsOfColor(int color) {
		int count = 0;
		for (int i = 0; i < m_hand.getNumCards(); i++) {
			if (m_hand.getCard(i).getColor() == color) count++;
		}
		return count;
	}

	/**
	 * Returns the active player sitting immediately before {@code seat} in the
	 * current play direction — used for Backstab / Swap target evaluation
	 * without mutating game state.
	 */
	private Player getPlayerBefore(int mySeat) {
		int dir = m_game.getDirection();
		// Step backwards: next in the opposite direction from my seat
		int idx = mySeat - 1; // 0-based
		for (int attempts = 0; attempts < 4; attempts++) {
			if (dir == Game.DIR_CLOCKWISE) {
				idx = (idx + 3) % 4;  // go counter-clockwise one step
			} else {
				idx = (idx + 1) % 4;  // go clockwise one step
			}
			Player p = m_game.getPlayer(idx);
			if (p != this && p.getActive()) return p;
		}
		return null;
	}

	public void readAggressionAndSkill() {
		switch (m_seat) {
			case Game.SEAT_WEST:
				m_aggression = m_go.getP1Agg();
				m_skill      = m_go.getP1Skill();
				break;
			case Game.SEAT_NORTH:
				m_aggression = m_go.getP2Agg();
				m_skill      = m_go.getP2Skill();
				break;
			case Game.SEAT_EAST:
				m_aggression = m_go.getP3Agg();
				m_skill      = m_go.getP3Skill();
				break;
			default:
				// 4th computer player (testing) mirrors player 2
				m_aggression = m_go.getP2Agg();
				m_skill      = m_go.getP2Skill();
				break;
		}
	}
}