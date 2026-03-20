package com.smorgasbork.hotdeath;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Builds and holds the card deck.
 *
 * <p>The original file contained ~800 lines of repetitive
 * {@code m_cards[i] = new Card(...); i++;} statements. This version replaces
 * that with small data-tables and a loop, cutting the class to a fraction of
 * its former size while being far easier to maintain.
 *
 * <p>The public API ({@link #getCards()}, {@link #getNumCards()},
 * {@link #getCard(int)}, {@link #getCard(int, int)}, {@link #shuffle()}) is
 * unchanged so the rest of the codebase requires no modifications.
 */
public class CardDeck {

	// -----------------------------------------------------------------------
	// Inner helper: a lightweight description of one card in the deck
	// -----------------------------------------------------------------------

	private static final class CardDef {
		final int color, value, id, points;
		CardDef(int color, int value, int id, int points) {
			this.color  = color;
			this.value  = value;
			this.id     = id;
			this.points = points;
		}
		 int getId() { return this.id; }
	}

	// -----------------------------------------------------------------------
	// Fields
	// -----------------------------------------------------------------------

	private final Card[] m_cards;
	private final Card[] m_oCards;   // shuffled order
	private int    m_numCards = 0;

	// -----------------------------------------------------------------------
	// Public API
	// -----------------------------------------------------------------------

	public Card[] getCards()    { return m_cards; }
	public int    getNumCards() { return m_numCards; }

	public Card getCard(int i) {
		return (i >= 0 && i < m_numCards) ? m_oCards[i] : null;
	}

	public Card getCard(int color, int value) {
		for (int i = 0; i < m_numCards; i++) {
			if (m_cards[i].getColor() == color && m_cards[i].getValue() == value) {
				return m_cards[i];
			}
		}
		return null;
	}

	// -----------------------------------------------------------------------
	// Constructor
	// -----------------------------------------------------------------------

	public CardDeck(boolean standardRules, boolean oneDeck) {
		List<CardDef> defs = buildDefinitions(standardRules, oneDeck);
		m_numCards = defs.size();
		m_cards  = new Card[m_numCards];
		m_oCards = new Card[m_numCards];

		for (int i = 0; i < m_numCards; i++) {
			CardDef d = defs.get(i);
			m_cards[i]  = new Card(i, d.color, d.value, d.id, d.points);
			m_oCards[i] = m_cards[i];
		}
	}

	// -----------------------------------------------------------------------
	// Shuffle
	// -----------------------------------------------------------------------

	public void shuffle() {
		for (int i = 0; i < m_numCards; i++) m_cards[i].setFaceUp(false);
		shuffle(7);
	}

	public void shuffle(int numTimes) {
		Random rng = new Random();
		for (int pass = 0; pass < numTimes; pass++) {
			for (int j = 0; j < m_numCards; j++) {
				int k = rng.nextInt(m_numCards);
				Card tmp   = m_oCards[j];
				m_oCards[j] = m_oCards[k];
				m_oCards[k] = tmp;
			}
		}
	}

	// -----------------------------------------------------------------------
	// Definition builder — returns an ordered list of CardDef entries
	// -----------------------------------------------------------------------

	private static List<CardDef> buildDefinitions(boolean standardRules, boolean oneDeck) {
		List<CardDef> defs = new ArrayList<>(oneDeck ? 108 : 216);
		int copies = oneDeck ? 1 : 2;

		addVanillaCards(defs, copies);
		if (!standardRules) {
			swapInHotDeathCards(defs, copies);
		}
		return defs;
	}

	// -----------------------------------------------------------------------
	// Standard (vanilla) deck
	// -----------------------------------------------------------------------

	private static void addVanillaCards(List<CardDef> defs, int copies) {
		int[] colors = {
				Card.COLOR_RED, Card.COLOR_GREEN, Card.COLOR_BLUE, Card.COLOR_YELLOW
		};
		int[][] colorIds = {
				// RED
				{ Card.ID_RED_0, Card.ID_RED_1, Card.ID_RED_2, Card.ID_RED_3, Card.ID_RED_4,
						Card.ID_RED_5, Card.ID_RED_6, Card.ID_RED_7, Card.ID_RED_8, Card.ID_RED_9,
						Card.ID_RED_D, Card.ID_RED_S, Card.ID_RED_R },
				// GREEN
				{ Card.ID_GREEN_0, Card.ID_GREEN_1, Card.ID_GREEN_2, Card.ID_GREEN_3, Card.ID_GREEN_4,
						Card.ID_GREEN_5, Card.ID_GREEN_6, Card.ID_GREEN_7, Card.ID_GREEN_8, Card.ID_GREEN_9,
						Card.ID_GREEN_D, Card.ID_GREEN_S, Card.ID_GREEN_R },
				// BLUE
				{ Card.ID_BLUE_0, Card.ID_BLUE_1, Card.ID_BLUE_2, Card.ID_BLUE_3, Card.ID_BLUE_4,
						Card.ID_BLUE_5, Card.ID_BLUE_6, Card.ID_BLUE_7, Card.ID_BLUE_8, Card.ID_BLUE_9,
						Card.ID_BLUE_D, Card.ID_BLUE_S, Card.ID_BLUE_R },
				// YELLOW
				{ Card.ID_YELLOW_0, Card.ID_YELLOW_1, Card.ID_YELLOW_2, Card.ID_YELLOW_3, Card.ID_YELLOW_4,
						Card.ID_YELLOW_5, Card.ID_YELLOW_6, Card.ID_YELLOW_7, Card.ID_YELLOW_8, Card.ID_YELLOW_9,
						Card.ID_YELLOW_D, Card.ID_YELLOW_S, Card.ID_YELLOW_R }
		};
		int[] values = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9,
				Card.VAL_D, Card.VAL_S, Card.VAL_R };
		int[] points = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 20, 20, 20 };

		for (int ci = 0; ci < colors.length; ci++) {
			for (int vi = 0; vi < values.length; vi++) {
				// 0s appear once per deck copy; 1-9 and specials appear twice per copy
				int count = (values[vi] == 0) ? copies : copies * 2;
				for (int k = 0; k < count; k++) {
					defs.add(new CardDef(colors[ci], values[vi], colorIds[ci][vi], points[vi]));
				}
			}
		}

		// Wild cards
		int wildCount = 4 * copies;
		for (int i = 0; i < wildCount; i++) {
			defs.add(new CardDef(Card.COLOR_WILD, Card.VAL_WILD, Card.ID_WILD, 50));
		}
		for (int i = 0; i < wildCount; i++) {
			defs.add(new CardDef(Card.COLOR_WILD, Card.VAL_WILD_DRAW, Card.ID_WILD_DRAW_FOUR, 50));
		}
	}

	// -----------------------------------------------------------------------
	// Hot Death deck
	// -----------------------------------------------------------------------

	/**
	 * Each row: { id_to_replace, max_replace_count, color, value, id, points }.
	 * For the two-deck variant each entry is simply added twice.
	 */
	private static final int[][] HD_CARDS = {
		// ---- RED ----
		{ Card.ID_RED_0, 1, Card.COLOR_RED, 0,						Card.ID_RED_0_HD,       0   },
		{ Card.ID_RED_2, 1, Card.COLOR_RED, 2,     					Card.ID_RED_2_GLASNOST, 75  },
		{ Card.ID_RED_5, 1, Card.COLOR_RED, 5,     					Card.ID_RED_5_MAGIC,   -5   },
		{ Card.ID_RED_D, 2, Card.COLOR_RED, Card.VAL_D_SPREAD, 		Card.ID_RED_D_SPREADER, 60 	},
		{ Card.ID_RED_S, 2, Card.COLOR_RED, Card.VAL_S_DOUBLE, 		Card.ID_RED_S_DOUBLE, 	40 	},
		{ Card.ID_RED_R, 2, Card.COLOR_RED, Card.VAL_R_SKIP, 		Card.ID_RED_R_SKIP,  	40  },
		// ---- GREEN ----
		{ Card.ID_GREEN_0, 1, Card.COLOR_GREEN, 0,					Card.ID_GREEN_0_QUITTER, 	100 },
		{ Card.ID_GREEN_3, 1, Card.COLOR_GREEN, 3,					Card.ID_GREEN_3_AIDS, 		3 	},
		{ Card.ID_GREEN_4, 1, Card.COLOR_GREEN, 4,					Card.ID_GREEN_4_IRISH, 		75  },
		{ Card.ID_GREEN_D, 2, Card.COLOR_GREEN, Card.VAL_D_SPREAD, 	Card.ID_GREEN_D_SPREADER, 	60 	},
		{ Card.ID_GREEN_S, 2, Card.COLOR_GREEN, Card.VAL_S_DOUBLE, 	Card.ID_GREEN_S_DOUBLE, 	40 	},
		{ Card.ID_GREEN_R, 2, Card.COLOR_GREEN, Card.VAL_R_SKIP, 	Card.ID_GREEN_R_SKIP,  		40  },
		// ---- BLUE ----
		{ Card.ID_BLUE_0, 1, Card.COLOR_BLUE, 0,             		Card.ID_BLUE_0_FUCK_YOU, 	0 },
		{ Card.ID_BLUE_2, 1, Card.COLOR_BLUE, 2,		            Card.ID_BLUE_2_SHIELD, 		0 },
		{ Card.ID_BLUE_D, 2, Card.COLOR_BLUE, Card.VAL_D_SPREAD, 	Card.ID_BLUE_D_SPREADER, 	60 },
		{ Card.ID_BLUE_S, 2, Card.COLOR_BLUE, Card.VAL_S_DOUBLE, 	Card.ID_BLUE_S_DOUBLE, 		40 	},
		{ Card.ID_BLUE_R, 2, Card.COLOR_BLUE, Card.VAL_R_SKIP, 		Card.ID_BLUE_R_SKIP,  		40  },
		// ---- YELLOW ----
		{ Card.ID_YELLOW_0, 1, Card.COLOR_YELLOW, 0,           			Card.ID_YELLOW_0_SHITTER, 0  },
		{ Card.ID_YELLOW_1, 1, Card.COLOR_YELLOW, 1,           			Card.ID_YELLOW_1_MAD,   100 },
		{ Card.ID_YELLOW_6, 1, Card.COLOR_YELLOW, 6,           			Card.ID_YELLOW_69,      69   },
		{ Card.ID_YELLOW_D, 2, Card.COLOR_YELLOW, Card.VAL_D_SPREAD, 	Card.ID_YELLOW_D_SPREADER, 60 	},
		{ Card.ID_YELLOW_S, 2, Card.COLOR_YELLOW, Card.VAL_S_DOUBLE, 	Card.ID_YELLOW_S_DOUBLE, 	40 	},
		{ Card.ID_YELLOW_R, 2, Card.COLOR_YELLOW, Card.VAL_R_SKIP, 		Card.ID_YELLOW_R_SKIP,  	40  },
		// ---- WILD ----
		{ Card.ID_WILD, 1, Card.COLOR_WILD, Card.VAL_WILD_DRAW, Card.ID_WILD_DB,      100 },
		{ Card.ID_WILD, 1, Card.COLOR_WILD, Card.VAL_WILD_DRAW, Card.ID_WILD_MYSTERY, 0 },
		{ Card.ID_WILD, 1, Card.COLOR_WILD, Card.VAL_WILD_DRAW, Card.ID_WILD_HD,      100 },
		{ Card.ID_WILD, 1, Card.COLOR_WILD, Card.VAL_WILD_DRAW, Card.ID_WILD_HOS, 	  0 },
		// ---- v3 new cards ----
		// Backstab (one per color, replaces one Reverse slot — added on top of existing Reverses)
		//{ Card.COLOR_RED,    Card.VAL_R_BACKSTAB, Card.ID_RED_R_BACKSTAB,    20 },
		//{ Card.COLOR_GREEN,  Card.VAL_R_BACKSTAB, Card.ID_GREEN_R_BACKSTAB,  20 },
		//{ Card.COLOR_BLUE,   Card.VAL_R_BACKSTAB, Card.ID_BLUE_R_BACKSTAB,   20 },
		//{ Card.COLOR_YELLOW, Card.VAL_R_BACKSTAB, Card.ID_YELLOW_R_BACKSTAB, 20 },
		// Dodge (one per color, replaces one 8 slot)
		/*
		{ Card.COLOR_RED,    Card.VAL_DODGE, Card.ID_RED_8_DODGE,    8 },
		{ Card.COLOR_GREEN,  Card.VAL_DODGE, Card.ID_GREEN_8_DODGE,  8 },
		{ Card.COLOR_BLUE,   Card.VAL_DODGE, Card.ID_BLUE_8_DODGE,   8 },
		{ Card.COLOR_YELLOW, Card.VAL_DODGE, Card.ID_YELLOW_8_DODGE, 8 },
		 */
		// Clone (2 total: Green 2, Yellow 2)
		//{ Card.COLOR_GREEN,  Card.VAL_CLONE, Card.ID_GREEN_2_CLONE,  20 },
		//{ Card.COLOR_YELLOW, Card.VAL_CLONE, Card.ID_YELLOW_2_CLONE, 20 },
		// Ping (Blue 1 directed)
		//{ Card.COLOR_BLUE,   Card.VAL_PING,  Card.ID_BLUE_1_PING,    1  },
		// Swap (Green Reverse + Yellow Reverse)
		//{ Card.COLOR_GREEN,  Card.VAL_SWAP,  Card.ID_GREEN_R_SWAP,   20 },
		//{ Card.COLOR_YELLOW, Card.VAL_SWAP,  Card.ID_YELLOW_R_SWAP,  20 },
	};

	private static void swapInHotDeathCards(List<CardDef> defs, int copies) {
		// For each HD row: { id_to_replace, max_count, color, value, id, points }
		for (int[] row : HD_CARDS) {
			int idToReplace = row[0];
			int maxReplacementCount = row[1];
			int color = row[2];
			int value = row[3];
			int newId = row[4];
			int points = row[5];

			// total replacements allowed across the supplied number of deck copies
			int totalAllowed = Math.min (maxReplacementCount, copies);

			int replaced = 0;
			// iterate once through the list bottom to top, replacing up to totalAllowed occurrences
			for (int i = defs.size() - 1; i >= 0  && replaced < totalAllowed; i--) {
				CardDef cd = defs.get(i);
				if (cd.getId() == idToReplace) {
					// replace this entry with the HD variant
					defs.set(i, new CardDef(color, value, newId, points));
					replaced++;
				}
			}
		}
	}
}