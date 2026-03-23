package com.smorgasbork.hotdeath;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.ActionBar;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

public class Prefs extends AppCompatActivity {
	private static final String OPT_GAME_SPEED = "game_speed";
	private static final String OPT_GAME_SPEED_DEF = "1";
	private static final String OPT_DECK_TYPE = "deck_type";
	private static final String OPT_DECK_TYPE_DEF = "standard";
	private static final String OPT_COMPUTER_4TH = "computer_4th";
	private static final boolean OPT_COMPUTER_4TH_DEF = false;
	private static final String OPT_FACE_UP = "face_up";
	private static final boolean OPT_FACE_UP_DEF = false;
	private static final String OPT_CHEAT_LEVEL = "cheat_level";
	private static final String OPT_CHEAT_LEVEL_DEF = "0";
	private static final String OPT_CHEAT_CODE = "cheat_code";
	private static final String OPT_CHEAT_CODE_DEF = "";

	private static final String OPT_P1_SKILL_LEVEL = "p1_skill";
	private static final String OPT_P1_SKILL_LEVEL_DEF = "1";
	private static final String OPT_P2_SKILL_LEVEL = "p2_skill";
	private static final String OPT_P2_SKILL_LEVEL_DEF = "1";
	private static final String OPT_P3_SKILL_LEVEL = "p3_skill";
	private static final String OPT_P3_SKILL_LEVEL_DEF = "1";

	private static final String OPT_P1_AGGRESSION_LEVEL = "p1_aggression";
	private static final String OPT_P1_AGGRESSION_LEVEL_DEF = "0";
	private static final String OPT_P2_AGGRESSION_LEVEL = "p2_aggression";
	private static final String OPT_P2_AGGRESSION_LEVEL_DEF = "0";
	private static final String OPT_P3_AGGRESSION_LEVEL = "p3_aggression";
	private static final String OPT_P3_AGGRESSION_LEVEL_DEF = "0";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_prefs);

		// Initialize default preferences
		PreferenceManager.setDefaultValues(this, R.xml.main_preferences, false);

		// Load the preferences fragment
		if (savedInstanceState == null) {
			getSupportFragmentManager()
					.beginTransaction()
					.replace(R.id.settings, new PrefsFragment())
					.commit();
		}
	}

	@Override
	public boolean onSupportNavigateUp() {
		finish();
		return true;
	}

	/**
	 * Fragment displaying preference UI.
	 * Uses AndroidX PreferenceFragmentCompat for API 21 compatibility.
	 */
	public static class PrefsFragment extends PreferenceFragmentCompat {
		@Override
		public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
			setPreferencesFromResource(R.xml.main_preferences, rootKey);
		}
	}

	// -----------------------------------------------------------------------
	// Preference Getters
	// -----------------------------------------------------------------------

	public static int getGameSpeed(Context context) {
		String s = PreferenceManager.getDefaultSharedPreferences(context)
				.getString(OPT_GAME_SPEED, OPT_GAME_SPEED_DEF);
		return Integer.parseInt(s);
	}

	/**
	 * Gets the selected deck type as a DeckType enum.
	 * Handles migration from old two_decks boolean preference if needed.
	 */
	public static CardDeck.DeckType getDeckType(Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

		// Try to get deck_type preference (new)
		String key = prefs.getString(OPT_DECK_TYPE, null);
		if (key != null) {
			return CardDeck.DeckType.fromKey(key);
		}

		// Fallback: check for old two_decks boolean preference for backward compatibility
		if (prefs.contains("two_decks")) {
			boolean twoDecks = prefs.getBoolean("two_decks", false);
			// Migrate to new preference
			prefs.edit()
					.putString(OPT_DECK_TYPE, twoDecks ? "standard" : "half")
					.remove("two_decks")
					.apply();
			return twoDecks ? CardDeck.DeckType.STANDARD : CardDeck.DeckType.HALF;
		}

		// Default to standard
		return CardDeck.DeckType.fromKey(OPT_DECK_TYPE_DEF);
	}

	public static boolean getComputer4th(Context context) {
		return PreferenceManager.getDefaultSharedPreferences(context)
				.getBoolean(OPT_COMPUTER_4TH, OPT_COMPUTER_4TH_DEF);
	}

	public static boolean getFaceUp(Context context) {
		return PreferenceManager.getDefaultSharedPreferences(context)
				.getBoolean(OPT_FACE_UP, OPT_FACE_UP_DEF);
	}

	public static int getCheatLevel(Context context) {
		return Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(context)
				.getString(OPT_CHEAT_LEVEL, OPT_CHEAT_LEVEL_DEF));
	}

	public static String getCheatCode(Context context) {
		return PreferenceManager.getDefaultSharedPreferences(context)
				.getString(OPT_CHEAT_CODE, OPT_CHEAT_CODE_DEF);
	}

	public static int getP1SkillLevel(Context context) {
		return Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(context)
				.getString(OPT_P1_SKILL_LEVEL, OPT_P1_SKILL_LEVEL_DEF));
	}

	public static int getP1AggressionLevel(Context context) {
		return Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(context)
				.getString(OPT_P1_AGGRESSION_LEVEL, OPT_P1_AGGRESSION_LEVEL_DEF));
	}

	public static int getP2SkillLevel(Context context) {
		return Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(context)
				.getString(OPT_P2_SKILL_LEVEL, OPT_P2_SKILL_LEVEL_DEF));
	}

	public static int getP2AggressionLevel(Context context) {
		return Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(context)
				.getString(OPT_P2_AGGRESSION_LEVEL, OPT_P2_AGGRESSION_LEVEL_DEF));
	}

	public static int getP3SkillLevel(Context context) {
		return Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(context)
				.getString(OPT_P3_SKILL_LEVEL, OPT_P3_SKILL_LEVEL_DEF));
	}

	public static int getP3AggressionLevel(Context context) {
		return Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(context)
				.getString(OPT_P3_AGGRESSION_LEVEL, OPT_P3_AGGRESSION_LEVEL_DEF));
	}

	public static class PlayersPrefsFragment extends PreferenceFragmentCompat {
		@Override
		public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
			setPreferencesFromResource(R.xml.opponent_preferences, rootKey);
		}
	}
}