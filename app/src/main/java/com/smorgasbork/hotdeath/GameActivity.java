package com.smorgasbork.hotdeath;

import android.app.Activity;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

public class GameActivity extends Activity {

	public static final String STARTUP_MODE = "com.smorgasbork.hotdeath.startup_mode";
	public static final int STARTUP_MODE_NEW = 1;
	public static final int STARTUP_MODE_CONTINUE = 2;

	private static final String TAG = "HDU";
	private static final String PREF_GAMESTATE = "gamestate";

	// Colors as constants instead of magic literals scattered through the code
	private static final int COLOR_ENABLED_TEXT  = 0xffffffff;
	private static final int COLOR_DISABLED_TEXT = 0xff7f7f7f;

	private Dialog m_dlgCardCatalog = null;
	private Dialog m_dlgCardHelp = null;

	private View m_vMenuPanel = null;
	private Button m_btnFastForward = null;
	private Button m_btnNextRound = null;
	private Button m_btnMenuDraw = null;
	private Button m_btnMenuPass = null;
	private Button m_btnMenuHelp = null;

	private GameTable m_gt;
	private Game m_game;
	private GameOptions m_go;

	// Accessors used by GameTable

	public Integer getCardImageID(int id)  { return m_gt.getCardImageID(id); }
	public Integer[] getCardIDs()          { return m_gt.getCardIDs(); }
	public Game getGame()                  { return m_game; }
	public Button getBtnNextRound()        { return m_btnNextRound; }
	public Button getBtnFastForward()      { return m_btnFastForward; }

	// Lifecycle

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		int startupMode = getIntent().getIntExtra(STARTUP_MODE, STARTUP_MODE_NEW);
		m_go = new GameOptions(this);
		m_game = (startupMode == STARTUP_MODE_CONTINUE) ? tryLoadSavedGame() : null;

		if (m_game == null || m_game.getDeck() == null) {
			m_game = new Game(this, m_go);
		}

		m_gt = new GameTable(this, m_game, m_go);
		m_gt.setId(View.generateViewId());

		setContentView(buildLayout());

		bindMenuButtons();

		m_gt.setBottomMargin(dpToPx(58));
		m_gt.invalidate();
		m_gt.requestFocus();
		m_gt.startGameWhenReady();
	}

	@Override
	protected void onPause() {
		dismissDialogIfShowing(m_dlgCardHelp);
		dismissDialogIfShowing(m_dlgCardCatalog);

		getIntent().putExtra(STARTUP_MODE, STARTUP_MODE_CONTINUE);
		super.onPause();

		m_game.pause();
		saveGameState();
	}

	@Override
	protected void onResume() {
		super.onResume();
		m_game.unpause();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		// Ignore orientation changes (paired with manifest setting)
		super.onConfigurationChanged(newConfig);
	}

	@Override
	protected void onDestroy() {
		m_game.shutdown();
		m_game = null;
		m_gt = null;
		m_go = null;
		super.onDestroy();
	}

	// Private helpers

	/** Attempt to deserialise a saved game; returns null on any failure. */
	private Game tryLoadSavedGame() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		String json = prefs.getString(PREF_GAMESTATE, "");
		try {
			return new Game(new JSONObject(json), this, m_go);
		} catch (JSONException e) {
			Log.d(TAG, "Creating Game from JSON failed: " + e.getMessage());
			return null;
		}
	}

	private void saveGameState() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		prefs.edit()
				.putString(PREF_GAMESTATE, m_game.getSnapshot())
				.apply(); // apply() is async and preferred over commit() on the main thread
	}

	/** Builds the root layout programmatically (mirrors original logic, cleaned up). */
	private RelativeLayout buildLayout() {
		RelativeLayout root = new RelativeLayout(this);

		int btnWidth  = dpToPx(160);
		int btnHeight = dpToPx(42);

		// --- Next Round button ---
		m_btnNextRound = (Button) getLayoutInflater().inflate(R.layout.action_button, null);
		m_btnNextRound.setText(getString(R.string.lbl_next_round));
		m_btnNextRound.setId(View.generateViewId());
		m_btnNextRound.setVisibility(View.INVISIBLE);
		m_btnNextRound.setOnClickListener(v -> {
			m_btnNextRound.setVisibility(View.INVISIBLE);
			m_game.setWaitingToStartRound(false);
		});

		// --- Fast Forward button ---
		m_btnFastForward = (Button) getLayoutInflater().inflate(R.layout.action_button, null);
		m_btnFastForward.setText(getString(R.string.lbl_fast_forward));
		m_btnFastForward.setId(View.generateViewId());
		m_btnFastForward.setVisibility(View.INVISIBLE);
		m_btnFastForward.setOnClickListener(v -> {
			m_btnFastForward.setVisibility(View.INVISIBLE);
			m_game.setFastForward(true);
		});

		// --- Options menu panel ---
		m_vMenuPanel = getLayoutInflater().inflate(R.layout.options_menu, null);
		m_vMenuPanel.setId(View.generateViewId());
		m_vMenuPanel.setVisibility(View.INVISIBLE);

		root.addView(m_gt);

		// Menu panel: align to bottom-centre of GameTable
		RelativeLayout.LayoutParams lpMenu = new RelativeLayout.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		lpMenu.addRule(RelativeLayout.ALIGN_BOTTOM, m_gt.getId());
		lpMenu.addRule(RelativeLayout.CENTER_HORIZONTAL, m_gt.getId());
		lpMenu.bottomMargin = dpToPx(8);
		root.addView(m_vMenuPanel, lpMenu);

		// Fast Forward: above the menu panel, centred
		RelativeLayout.LayoutParams lpFF = new RelativeLayout.LayoutParams(btnWidth, btnHeight);
		lpFF.addRule(RelativeLayout.ABOVE, m_vMenuPanel.getId());
		lpFF.addRule(RelativeLayout.CENTER_HORIZONTAL, m_gt.getId());
		lpFF.bottomMargin = dpToPx(8);
		root.addView(m_btnFastForward, lpFF);

		// Next Round: centred in parent
		RelativeLayout.LayoutParams lpNR = new RelativeLayout.LayoutParams(btnWidth, btnHeight);
		lpNR.addRule(RelativeLayout.CENTER_IN_PARENT, m_gt.getId());
		lpNR.topMargin = dpToPx(8);
		root.addView(m_btnNextRound, lpNR);

		root.setFitsSystemWindows(true);
		return root;
	}

	/** Wire up the in-game action buttons after setContentView. */
	private void bindMenuButtons() {
		m_btnMenuDraw = findViewById(R.id.btn_menu_draw);
		m_btnMenuPass = findViewById(R.id.btn_menu_pass);
		m_btnMenuHelp = findViewById(R.id.btn_menu_help);

		m_btnMenuDraw.setOnClickListener(v -> {
			m_game.drawPileTapped();
			showMenuButtons();
		});
		m_btnMenuPass.setOnClickListener(v -> {
			m_game.humanPlayerPass();
			showMenuButtons();
		});
		m_btnMenuHelp.setOnClickListener(v -> showCardCatalog());
	}

	/** Converts dp to pixels using the current display density. */
	private int dpToPx(int dp) {
		float density = getResources().getDisplayMetrics().density;
		return (int) (dp * density + 0.5f);
	}

	private static void dismissDialogIfShowing(Dialog dlg) {
		if (dlg != null && dlg.isShowing()) {
			dlg.dismiss();
		}
	}

	// Dialog helpers

	public void showCardHelp() {
		if (m_dlgCardHelp == null) {
			m_dlgCardHelp = new TapDismissableDialog(this);
			m_dlgCardHelp.setContentView(R.layout.dlg_card_help);
		}

		int cid = m_gt.getHelpCardID();
		Card c = m_gt.getCardByID(cid);
		if (c != null) {
			m_dlgCardHelp.setTitle(m_game.cardToString(c));
			((TextView) m_dlgCardHelp.findViewById(R.id.text)).setText(m_gt.getCardHelpText(cid));
			((ImageView) m_dlgCardHelp.findViewById(R.id.image)).setImageBitmap(m_gt.getCardBitmap(cid));
		}
		m_dlgCardHelp.show();
	}

	public void showCardCatalog() {
		if (m_dlgCardCatalog == null) {
			m_dlgCardCatalog = new Dialog(this);
			m_dlgCardCatalog.requestWindowFeature(Window.FEATURE_NO_TITLE);
			m_dlgCardCatalog.setContentView(R.layout.dlg_card_catalog);

			GridView gridview = m_dlgCardCatalog.findViewById(R.id.gridview);
			gridview.setAdapter(new CardImageAdapter(this));
			gridview.setOnItemClickListener((parent, v, position, id) -> {
				Integer[] cardIds = ((CardImageAdapter) parent.getAdapter()).getCardIDs();
				m_gt.setHelpCardID(cardIds[position]);
				showCardHelp();
			});
		}
		m_dlgCardCatalog.show();
	}

	// Menu button state management

	public void showMenuButtons() {
		if (m_game.getCurrPlayer() instanceof HumanPlayer) {
			boolean canDraw = !m_game.getCurrPlayerUnderAttack() && !m_game.getCurrPlayerDrawn();
			setButtonEnabled(m_btnMenuDraw, canDraw);
			setButtonEnabled(m_btnMenuPass, !canDraw);
		} else {
			setButtonEnabled(m_btnMenuDraw, false);
			setButtonEnabled(m_btnMenuPass, false);
		}
		m_vMenuPanel.setVisibility(View.VISIBLE);
	}

	public void hideMenuButtons() {
		m_vMenuPanel.setVisibility(View.INVISIBLE);
	}

	/** Enables/disables a button and updates its text colour accordingly. */
	private static void setButtonEnabled(Button btn, boolean enabled) {
		btn.setEnabled(enabled);
		btn.setTextColor(enabled ? COLOR_ENABLED_TEXT : COLOR_DISABLED_TEXT);
	}
}