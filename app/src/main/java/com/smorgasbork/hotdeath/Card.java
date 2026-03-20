package com.smorgasbork.hotdeath;

import android.content.Context;

public class Card implements Animatable {

	// Flip animation constants
	public static final float FLIPPING_START     = 0f;
	public static final float FLIPPING_END       = 0.875f;
	public static final float FLIPPING_MID_POINT = (FLIPPING_START + FLIPPING_END) / 2f;
	public static final float FLIPPING_HALF_SPAN = (FLIPPING_END - FLIPPING_START) / 2f;

	// -----------------------------------------------------------------------
	// Enums replacing int constants
	// -----------------------------------------------------------------------

	public enum CardState { DRAW_PILE, MOVING, HAND, DISCARD_PILE }

	public enum Color {
		RED(1), GREEN(2), BLUE(3), YELLOW(4), WILD(5);

		public final int id;
		Color(int id) { this.id = id; }

		public static Color fromId(int id) {
			for (Color c : values()) if (c.id == id) return c;
			throw new IllegalArgumentException("Unknown color id: " + id);
		}
	}

	public enum Value {
		ZERO(0), ONE(1), TWO(2), THREE(3), FOUR(4),
		FIVE(5), SIX(6), SEVEN(7), EIGHT(8), NINE(9),
		DRAW(11), SKIP(12), REVERSE(13),
		DRAW_SPREAD(14), SKIP_DOUBLE(15), REVERSE_SKIP(16),
		WILD(17), WILD_DRAW(18);

		public final int id;
		Value(int id) { this.id = id; }

		public static Value fromId(int id) {
			for (Value v : values()) if (v.id == id) return v;
			throw new IllegalArgumentException("Unknown value id: " + id);
		}
	}

	// -----------------------------------------------------------------------
	// Card ID constants (unchanged — needed for resource/deck lookups)
	// -----------------------------------------------------------------------
	public static final int COLOR_RED    = 1;
	public static final int COLOR_GREEN  = 2;
	public static final int COLOR_BLUE   = 3;
	public static final int COLOR_YELLOW = 4;
	public static final int COLOR_WILD   = 5;

	public static final int VAL_D        = 11;
	public static final int VAL_S        = 12;
	public static final int VAL_R        = 13;
	public static final int VAL_D_SPREAD = 14;
	public static final int VAL_S_DOUBLE = 15;
	public static final int VAL_R_SKIP   = 16;
	public static final int VAL_WILD     = 17;
	public static final int VAL_WILD_DRAW = 18;

	// v3 new card values
	public static final int VAL_R_BACKSTAB = 19;  // Reverse + draw-2-behind
	//public static final int VAL_DODGE      = 20;  // Dodge (numbered 8 slot)
	public static final int VAL_CLONE      = 21;  // Clone
	public static final int VAL_PING       = 22;  // Ping (numbered 1 slot)
	public static final int VAL_SWAP       = 23;  // Swap (reverse slot)

	public static final int ID_RED_0             = 100;
	public static final int ID_RED_1             = 101;
	public static final int ID_RED_2             = 102;
	public static final int ID_RED_3             = 103;
	public static final int ID_RED_4             = 104;
	public static final int ID_RED_5             = 105;
	public static final int ID_RED_6             = 106;
	public static final int ID_RED_7             = 107;
	public static final int ID_RED_8             = 108;
	public static final int ID_RED_9             = 109;
	public static final int ID_RED_D             = 110;
	public static final int ID_RED_S             = 111;
	public static final int ID_RED_R             = 112;
	public static final int ID_GREEN_0           = 113;
	public static final int ID_GREEN_1           = 114;
	public static final int ID_GREEN_2           = 115;
	public static final int ID_GREEN_3           = 116;
	public static final int ID_GREEN_4           = 117;
	public static final int ID_GREEN_5           = 118;
	public static final int ID_GREEN_6           = 119;
	public static final int ID_GREEN_7           = 120;
	public static final int ID_GREEN_8           = 121;
	public static final int ID_GREEN_9           = 122;
	public static final int ID_GREEN_D           = 123;
	public static final int ID_GREEN_S           = 124;
	public static final int ID_GREEN_R           = 125;
	public static final int ID_BLUE_0            = 126;
	public static final int ID_BLUE_1            = 127;
	public static final int ID_BLUE_2            = 128;
	public static final int ID_BLUE_3            = 129;
	public static final int ID_BLUE_4            = 130;
	public static final int ID_BLUE_5            = 131;
	public static final int ID_BLUE_6            = 132;
	public static final int ID_BLUE_7            = 133;
	public static final int ID_BLUE_8            = 134;
	public static final int ID_BLUE_9            = 135;
	public static final int ID_BLUE_D            = 136;
	public static final int ID_BLUE_S            = 137;
	public static final int ID_BLUE_R            = 138;
	public static final int ID_YELLOW_0          = 139;
	public static final int ID_YELLOW_1          = 140;
	public static final int ID_YELLOW_2          = 141;
	public static final int ID_YELLOW_3          = 142;
	public static final int ID_YELLOW_4          = 143;
	public static final int ID_YELLOW_5          = 144;
	public static final int ID_YELLOW_6          = 145;
	public static final int ID_YELLOW_7          = 146;
	public static final int ID_YELLOW_8          = 147;
	public static final int ID_YELLOW_9          = 148;
	public static final int ID_YELLOW_D          = 149;
	public static final int ID_YELLOW_S          = 150;
	public static final int ID_YELLOW_R          = 151;
	public static final int ID_WILD              = 152;
	public static final int ID_WILD_DRAW_FOUR    = 153;
	public static final int ID_WILD_HOS          = 154;
	public static final int ID_WILD_HD           = 155;
	public static final int ID_WILD_MYSTERY      = 156;
	public static final int ID_WILD_DB           = 157;
	public static final int ID_RED_0_HD          = 158;
	public static final int ID_RED_2_GLASNOST    = 159;
	public static final int ID_RED_5_MAGIC       = 160;
	public static final int ID_RED_D_SPREADER    = 161;
	public static final int ID_RED_S_DOUBLE      = 162;
	public static final int ID_RED_R_SKIP        = 163;
	public static final int ID_GREEN_0_QUITTER   = 164;
	public static final int ID_GREEN_3_AIDS      = 165;
	public static final int ID_GREEN_4_IRISH     = 166;
	public static final int ID_GREEN_D_SPREADER  = 167;
	public static final int ID_GREEN_S_DOUBLE    = 168;
	public static final int ID_GREEN_R_SKIP      = 169;
	public static final int ID_BLUE_0_FUCK_YOU   = 170;
	public static final int ID_BLUE_2_SHIELD     = 171;
	public static final int ID_BLUE_D_SPREADER   = 172;
	public static final int ID_BLUE_S_DOUBLE     = 173;
	public static final int ID_BLUE_R_SKIP       = 174;
	public static final int ID_YELLOW_0_SHITTER  = 175;
	public static final int ID_YELLOW_1_MAD      = 176;
	public static final int ID_YELLOW_69         = 177;
	public static final int ID_YELLOW_D_SPREADER = 178;
	public static final int ID_YELLOW_S_DOUBLE   = 179;
	public static final int ID_YELLOW_R_SKIP     = 180;

	// ---- v3 new cards ----
	//public static final int ID_RED_R_BACKSTAB    = 181;  // Backstab (Red Reverse)
	//public static final int ID_GREEN_R_BACKSTAB  = 182;  // Backstab (Green Reverse)
	//public static final int ID_BLUE_R_BACKSTAB   = 183;  // Backstab (Blue Reverse)
	//public static final int ID_YELLOW_R_BACKSTAB = 184;  // Backstab (Yellow Reverse)
	//public static final int ID_RED_8_DODGE       = 185;  // Dodge (Red 8)
	//public static final int ID_GREEN_8_DODGE     = 186;  // Dodge (Green 8)
	//public static final int ID_BLUE_8_DODGE      = 187;  // Dodge (Blue 8)
	//public static final int ID_YELLOW_8_DODGE    = 188;  // Dodge (Yellow 8)
	//public static final int ID_GREEN_2_CLONE     = 189;  // Clone (Green 2)
	//public static final int ID_YELLOW_2_CLONE    = 190;  // Clone (Yellow 2)
	//public static final int ID_BLUE_1_PING       = 191;  // Ping (Blue 1)
	//public static final int ID_GREEN_R_SWAP      = 192;  // Swap (Green Reverse)
	//public static final int ID_YELLOW_R_SWAP     = 193;  // Swap (Yellow Reverse)

	// -----------------------------------------------------------------------
	// Fields
	// -----------------------------------------------------------------------

	private Hand      m_hand;
	private final int m_color;
	private final int m_value;
	private int       m_currentValue = 0;
	private final int m_pointValue;
	private final int m_deckIndex;
	private final int m_id;
	private boolean   m_faceUp = false;

	// Animation state
	private CardState m_state        = CardState.DRAW_PILE;
	private float     m_x, m_y;
	private float     m_startX, m_startY;
	private float     m_targetX, m_targetY;
	private float     m_rot          = 0f;
	private float     m_startRot;
	private float     m_targetRot;
	private float     m_flip         = 0f;
	private long      m_startTime;
	private long      m_duration;
	private boolean   m_targetFaceUp;
	private CardState m_targetState;
	private boolean   m_isAnimating;

	// -----------------------------------------------------------------------
	// Constructor
	// -----------------------------------------------------------------------

	public Card(int deckIndex, int color, int value, int id, int pointValue) {
		m_deckIndex  = deckIndex;
		m_color      = color;
		m_value      = value;
		m_id         = id;
		m_pointValue = pointValue;
	}

	// -----------------------------------------------------------------------
	// Accessors
	// -----------------------------------------------------------------------

	public Hand    getHand()        { return m_hand; }
	public void    setHand(Hand h)  { m_hand = h; }
	public int     getDeckIndex()   { return m_deckIndex; }
	public int     getColor()       { return m_color; }
	public int     getValue()       { return m_value; }
	public int     getID()          { return m_id; }
	public int     getPointValue()  { return m_pointValue; }
	public int     getCurrentValue(){ return m_currentValue; }
	public void    setCurrentValue(int cv) { m_currentValue = cv; }
	public boolean isFaceUp()       { return m_faceUp; }
	public void    setFaceUp(boolean f) { m_faceUp = f; }
	public CardState getState()     { return m_state; }
	public void    setState(CardState s) { m_state = s; }
	public float   getX()           { return m_x; }
	public float   getY()           { return m_y; }
	public float   getRot()         { return m_rot; }
	public float   getFlip()        { return m_flip; }
	public void    setX(int x)      { m_x = x; }
	public void    setY(int y)      { m_y = y; }
	public void    setTargetX(int x){ m_targetX = x; }
	public void    setTargetY(int y){ m_targetY = y; }

	// -----------------------------------------------------------------------
	// toString
	// -----------------------------------------------------------------------

	public String toString(Context ctx, boolean familyFriendly) {
		// Named special cards take priority
		String special = getSpecialCardName(ctx, familyFriendly);
		if (special != null) return special;

		String color = getColorString(ctx);
		String value = getValueString(ctx);
		return color + " " + value;
	}

	/** Returns a localised name for cards that have a unique name, null otherwise. */
	private String getSpecialCardName(Context ctx, boolean familyFriendly) {
		switch (m_id) {
			case ID_WILD:             return ctx.getString(R.string.cardval_wild);
			case ID_WILD_DRAW_FOUR:   return ctx.getString(R.string.cardval_wild_drawfour);
			case ID_WILD_HD:          return ctx.getString(R.string.cardname_wild_hd);
			case ID_WILD_DB:          return ctx.getString(R.string.cardname_wild_db);
			case ID_WILD_HOS:         return ctx.getString(R.string.cardname_wild_hos);
			case ID_WILD_MYSTERY:     return ctx.getString(R.string.cardname_wild_mystery);
			case ID_RED_0_HD:         return ctx.getString(R.string.cardname_red_0_hd);
			case ID_RED_2_GLASNOST:   return ctx.getString(R.string.cardname_red_2_glasnost);
			case ID_RED_5_MAGIC:      return ctx.getString(R.string.cardname_red_5_magic);
			case ID_GREEN_0_QUITTER:  return ctx.getString(R.string.cardname_green_0_quitter);
			case ID_GREEN_3_AIDS:     return ctx.getString(familyFriendly
					? R.string.cardname_green_3_aids_ff : R.string.cardname_green_3_aids);
			case ID_GREEN_4_IRISH:    return ctx.getString(R.string.cardname_green_4_irish);
			case ID_BLUE_0_FUCK_YOU:  return ctx.getString(familyFriendly
					? R.string.cardname_blue_0_fuck_you_ff : R.string.cardname_blue_0_fuck_you);
			case ID_BLUE_2_SHIELD:    return ctx.getString(R.string.cardname_blue_2_shield);
			case ID_YELLOW_0_SHITTER: return ctx.getString(familyFriendly
					? R.string.cardname_yellow_0_shitter_ff : R.string.cardname_yellow_0_shitter);
			case ID_YELLOW_1_MAD:     return ctx.getString(R.string.cardname_yellow_1_mad);
			case ID_YELLOW_69:        return ctx.getString(R.string.cardname_yellow_69);
			// v3 new cards
			//case ID_RED_R_BACKSTAB:
			//case ID_GREEN_R_BACKSTAB:
			//case ID_BLUE_R_BACKSTAB:
			//case ID_YELLOW_R_BACKSTAB: return ctx.getString(R.string.cardname_backstab);
			//case ID_RED_8_DODGE:
			//case ID_GREEN_8_DODGE:
			//case ID_BLUE_8_DODGE:
			//case ID_YELLOW_8_DODGE:    return ctx.getString(R.string.cardname_dodge);
			//case ID_GREEN_2_CLONE:
			//case ID_YELLOW_2_CLONE:    return ctx.getString(R.string.cardname_clone);
			//case ID_BLUE_1_PING:       return ctx.getString(R.string.cardname_ping);
			//case ID_GREEN_R_SWAP:
			//case ID_YELLOW_R_SWAP:     return ctx.getString(R.string.cardname_swap);
			default:                  return null;
		}
	}

	private String getColorString(Context ctx) {
		switch (m_color) {
			case COLOR_RED:    return ctx.getString(R.string.cardcolor_red);
			case COLOR_GREEN:  return ctx.getString(R.string.cardcolor_green);
			case COLOR_BLUE:   return ctx.getString(R.string.cardcolor_blue);
			case COLOR_YELLOW: return ctx.getString(R.string.cardcolor_yellow);
			default:           return "";
		}
	}

	private String getValueString(Context ctx) {
		switch (m_value) {
			case VAL_D:        return ctx.getString(R.string.cardval_d);
			case VAL_S:        return ctx.getString(R.string.cardval_s);
			case VAL_R:        return ctx.getString(R.string.cardval_r);
			case VAL_D_SPREAD: return ctx.getString(R.string.cardval_d_spread);
			case VAL_S_DOUBLE: return ctx.getString(R.string.cardval_s_double);
			case VAL_R_SKIP:   return ctx.getString(R.string.cardval_r_skip);
			//case VAL_R_BACKSTAB: return ctx.getString(R.string.cardname_backstab);
			//case VAL_DODGE:    return ctx.getString(R.string.cardname_dodge);
			//case VAL_CLONE:    return ctx.getString(R.string.cardname_clone);
			//case VAL_PING:     return ctx.getString(R.string.cardname_ping);
			//case VAL_SWAP:     return ctx.getString(R.string.cardname_swap);
			default:           return String.valueOf(m_value);
		}
	}

	// -----------------------------------------------------------------------
	// Animatable implementation
	// -----------------------------------------------------------------------

	@Override
	public void startAnimation(AnimationParams params) {
		m_startX       = m_x;
		m_startY       = m_y;
		m_targetX      = params.toX;
		m_targetY      = params.toY;
		m_startRot     = m_rot;
		m_targetRot    = params.toRot;
		m_targetFaceUp = params.toFaceUp;
		m_startTime    = params.startTime;
		m_duration     = params.duration;
		m_targetState  = params.toState;
		m_isAnimating  = true;
	}

	@Override
	public void update() {
		if (!m_isAnimating) return;

		long elapsed = System.currentTimeMillis() - m_startTime;
		if (elapsed >= m_duration) {
			elapsed = m_duration;
			m_state = m_targetState;
			m_isAnimating = false;
		}

		float t = (float) elapsed / m_duration;

		m_x   = lerp(m_startX, m_targetX, t);
		m_y   = lerp(m_startY, m_targetY, t);
		m_rot = lerp(m_startRot, m_targetRot, t);

		updateFlip(t);
	}

	/**
	 * Handles the Y-axis flip animation for face-up/face-down transitions.
	 * First half: rotate to 90° (card edge-on). At midpoint flip the face.
	 * Second half: rotate back from -90° to 0°.
	 */
	private void updateFlip(float t) {
		if (m_targetFaceUp == m_faceUp) {
			// No flip needed — if flip is still nonzero, snap it toward 0
			if (m_flip != 0f) {
				float unflipProgress = Math.min(1f,
						(t - FLIPPING_MID_POINT) / FLIPPING_HALF_SPAN);
				m_flip = (1f - unflipProgress) * -90f;
			}
			return;
		}

		if (t <= FLIPPING_MID_POINT) {
			float flipProgress = Math.max(0f,
					(t - FLIPPING_START) / FLIPPING_HALF_SPAN);
			m_flip = Math.min(1f, flipProgress) * 90f;
		} else {
			m_faceUp = m_targetFaceUp;
		}
	}

	@Override
	public boolean isAnimating() { return m_isAnimating; }

	// -----------------------------------------------------------------------
	// Utility
	// -----------------------------------------------------------------------

	private static float lerp(float a, float b, float t) {
		return a + t * (b - a);
	}

	// Returns the canonical ID for catalog display.
	// Variant cards (same mechanic, different color) map to one representative.
	//public int getCatalogID() {
		//switch (m_id) {
			//case ID_GREEN_R_BACKSTAB:
			//case ID_BLUE_R_BACKSTAB:
			//case ID_YELLOW_R_BACKSTAB:  return ID_RED_R_BACKSTAB;
			//case ID_GREEN_8_DODGE:
			//case ID_BLUE_8_DODGE:
			//case ID_YELLOW_8_DODGE:     return ID_RED_8_DODGE;
			//case ID_YELLOW_2_CLONE:     return ID_GREEN_2_CLONE;
			//case ID_YELLOW_R_SWAP:      return ID_GREEN_R_SWAP;
			// Ping only has one variant, no mapping needed.
			//default: return m_id;
		//}
	//}
}
