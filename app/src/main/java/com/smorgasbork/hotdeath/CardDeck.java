package com.smorgasbork.hotdeath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

	public enum DeckType {
		STANDARD(0, "standard", 2, true, false, false),
		HALF(1, "half", 1, true, false, false),
		COMPACT(2, "compact", 1, false, true, false),
		EXTENDED(3, "extended", 2, true, false, true),
		VANILLA(4, "vanilla", 1, false, false, false);

		public final int id;
		public final String key;
		public final int baseDeckCount;
		public final boolean modifyDecks;
		public final boolean addModifiedCards;
		public final boolean addExtendedCards;

		public final int cardCount;

		DeckType(int id, String key, int baseDeckCount, boolean modifyDecks, boolean addModifiedCards, boolean addExtendedCards) {
			this.id = id;
			this.key = key;
			this.baseDeckCount = baseDeckCount;
			this.modifyDecks = modifyDecks;
			this.addModifiedCards = addModifiedCards;
			this.addExtendedCards = addExtendedCards;
			this.cardCount = baseDeckCount * 108 + (addModifiedCards ? 27 : 0) + (addExtendedCards ? 9 : 0);
		}

		public static DeckType fromId(int id) {
			for (DeckType type : values()) {
				if (type.id == id) return type;
			}
			return STANDARD; // default
		}

		public static DeckType fromKey(String key) {
			if (key == null) return VANILLA;
			for (DeckType type : values()) {
				if (type.key.equals(key)) return type;
			}
			return STANDARD; // default
		}
	}

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
	}

	// -----------------------------------------------------------------------
	// Fields
	// -----------------------------------------------------------------------

	private final Card[] m_cards;
	private int    m_numCards = 0;

	// -----------------------------------------------------------------------
	// Public API
	// -----------------------------------------------------------------------

	public Card[] getCards()    { return m_cards; }
	public int    getNumCards() { return m_numCards; }

	public Card getCard(int i) {
		return (i >= 0 && i < m_numCards) ? m_cards[i] : null;
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

	public CardDeck(DeckType deckType) {
		List<CardDef> defs = buildDefinitions(deckType);
		m_numCards = defs.size();
		m_cards  = new Card[m_numCards];

		for (int i = 0; i < m_numCards; i++) {
			CardDef d = defs.get(i);
			m_cards[i]  = new Card(i, d.color, d.value, d.id, d.points);
		}
	}

	// -----------------------------------------------------------------------
	// Definition builder — returns an ordered list of CardDef entries
	// -----------------------------------------------------------------------

	private static List<CardDef> buildDefinitions(DeckType deckType) {

		List<CardDef> defs = new ArrayList<>(deckType.cardCount);

		addVanillaCards(defs, deckType.baseDeckCount);
		if (deckType.modifyDecks) {
			swapInHotDeathCards(defs, deckType.baseDeckCount);
			if (deckType.addExtendedCards) {
				addExtendedCards(defs);
			}
		} else if (deckType.addModifiedCards) {
			addHotDeathCards(defs);
		}
		//sort defs by color, then value, then id
		Collections.sort(defs, new Comparator<CardDef>() {
			@Override
			public int compare(CardDef a, CardDef b) {
				// compare color
				int c = Integer.compare(a.color, b.color);
				if (c != 0) return c;
				// compare value
				c = Integer.compare(a.value, b.value);
				if (c != 0) return c;
				// compare ID
				return Integer.compare(a.id, b.id);
			}
		});;
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
				if (cd.id == idToReplace) {
					// replace this entry with the HD variant
					defs.set(i, new CardDef(color, value, newId, points));
					replaced++;
				}
			}
		}
	}

	private static void addHotDeathCards(List<CardDef> defs) {
		for (int[] row : HD_CARDS) {
			int count = row[1];
			int color = row[2];
			int value = row[3];
			int id = row[4];
			int points = row[5];

			for (int i = 0; i < count; i++) {
				defs.add(new CardDef(color, value, id, points));
			}
		}
	}

	//------------------------------
 	// Extended Cards
 	//------------------------------

	private static final int[][] EXTENDED_CARDS = {
			// Backstab (one per color, replaces one Reverse slot — added on top of existing Reverses)
			{ Card.COLOR_RED,    Card.VAL_R_BACKSTAB, Card.ID_RED_R_BACKSTAB,    20 },
			{ Card.COLOR_GREEN,  Card.VAL_R_BACKSTAB, Card.ID_GREEN_R_BACKSTAB,  20 },
			{ Card.COLOR_BLUE,   Card.VAL_R_BACKSTAB, Card.ID_BLUE_R_BACKSTAB,   20 },
			{ Card.COLOR_YELLOW, Card.VAL_R_BACKSTAB, Card.ID_YELLOW_R_BACKSTAB, 20 },
			// Dodge (one per color, replaces one 8 slot)
			/*
            { Card.COLOR_RED,    Card.VAL_DODGE, Card.ID_RED_8_DODGE,    8 },
            { Card.COLOR_GREEN,  Card.VAL_DODGE, Card.ID_GREEN_8_DODGE,  8 },
            { Card.COLOR_BLUE,   Card.VAL_DODGE, Card.ID_BLUE_8_DODGE,   8 },
            { Card.COLOR_YELLOW, Card.VAL_DODGE, Card.ID_YELLOW_8_DODGE, 8 },
             */
			// Clone (2 total: Green 2, Yellow 2)
			{ Card.COLOR_GREEN,  Card.VAL_CLONE, Card.ID_GREEN_2_CLONE,  20 },
			{ Card.COLOR_YELLOW, Card.VAL_CLONE, Card.ID_YELLOW_2_CLONE, 20 },
			// Ping (Blue 1 directed)
			{ Card.COLOR_BLUE,   Card.VAL_PING,  Card.ID_BLUE_1_PING,    1  },
			// Swap (Green Reverse + Yellow Reverse)
			{ Card.COLOR_GREEN,  Card.VAL_SWAP,  Card.ID_GREEN_R_SWAP,   20 },
			{ Card.COLOR_YELLOW, Card.VAL_SWAP,  Card.ID_YELLOW_R_SWAP,  20 },
	};

	private static void addExtendedCards(List<CardDef> defs) {
		for (int[] row : EXTENDED_CARDS) {
				int color = row[0];
				int value = row[1];
				int id = row[2];
				int points = row[3];

				defs.add(new CardDef(color, value, id, points));
		}
	}
}