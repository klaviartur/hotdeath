package com.smorgasbork.hotdeath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * An ordered collection of {@link Card}s representing either the draw pile
 * or the discard pile.
 *
 * <p>Switched from a fixed-size array to an {@link ArrayList} to eliminate
 * the hard-coded {@code Game.MAX_NUM_CARDS} ceiling and to give O(1) amortised
 * add/remove from the top of the pile.
 */
public class CardPile {

	private final boolean        faceUp;
	private final Card.CardState cardState;
	private final List<Card>     cards = new ArrayList<>();

	// -----------------------------------------------------------------------
	// Constructors
	// -----------------------------------------------------------------------

	public CardPile(boolean faceUp, Card.CardState cardState) {
		this.faceUp    = faceUp;
		this.cardState = cardState;
	}

	/** Deserialisation constructor. */
	public CardPile(JSONObject o, CardDeck deck, boolean faceUp, Card.CardState cardState)
			throws JSONException {
		this(faceUp, cardState);
		JSONArray a = o.getJSONArray("cards");
		for (int i = 0; i < a.length(); i++) {
			addCard(deck.getCard(a.getInt(i)), true);
		}
	}

	// -----------------------------------------------------------------------
	// Accessors
	// -----------------------------------------------------------------------

	public int  getNumCards()  { return cards.size(); }
	public boolean isFaceUp()  { return faceUp; }

	/** Returns the card at position {@code i} (0 = bottom). */
	public Card getCard(int i) {
		return (i >= 0 && i < cards.size()) ? cards.get(i) : null;
	}

	// -----------------------------------------------------------------------
	// Mutation
	// -----------------------------------------------------------------------

	public void addCard(Card c, boolean instant) {
		cards.add(c);
		if (instant) {
			c.setState(cardState);
			c.setFaceUp(faceUp);
		}
	}

	/** Removes and returns the top (last) card, or null if empty. */
	public Card drawCard() {
		if (cards.isEmpty()) return null;
		Card c = cards.remove(cards.size() - 1);
		return c;
	}

	/**
	 * Finds and removes the first card with the given {@code id}.
	 * Returns null if not found.
	 */
	public Card pullCard(int id) {
		for (int i = 0; i < cards.size(); i++) {
			if (cards.get(i).getID() == id) {
				return cards.remove(i);
			}
		}
		return null;
	}

	// -----------------------------------------------------------------------
	// Shuffle
	// -----------------------------------------------------------------------

	public void shuffle() { shuffle(7); }

	public void shuffle(int numTimes) {
		for (Card c : cards) c.setFaceUp(false);
		Random rng = new Random();
		for (int pass = 0; pass < numTimes; pass++) {
			Collections.shuffle(cards, rng);
		}
	}

	// -----------------------------------------------------------------------
	// JSON serialisation
	// -----------------------------------------------------------------------

	public JSONObject toJSON() throws JSONException {
		JSONArray a = new JSONArray();
		for (Card c : cards) a.put(c.getDeckIndex());
		JSONObject o = new JSONObject();
		o.put("cards", a);
		return o;
	}
}