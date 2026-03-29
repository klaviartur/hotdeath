package com.smorgasbork.hotdeath;

import static java.lang.Math.*;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameTable extends View {

	private static final String TAG = "HDU";
	private static final int VIEW_ID = 42;

	// -------------------------------------------------------------------------
	// CardInfo — replaces four parallel HashMaps with one unified record
	// -------------------------------------------------------------------------

	/**
	 * Bundles every per-card asset reference that used to live in four separate
	 * HashMaps (m_cardLookup, m_imageIDLookup, m_imageLookup, m_cardHelpLookup).
	 * Keeping them together makes initCards() ~4× shorter and eliminates the risk
	 * of the maps falling out of sync.
	 */
	private static final class CardInfo {
		final Card   card;
		final int    imageResId;
		final Bitmap bitmap;
		final int    helpResId;

		CardInfo(Card card, int imageResId, Bitmap bitmap, int helpResId) {
			this.card       = card;
			this.imageResId = imageResId;
			this.bitmap     = bitmap;
			this.helpResId  = helpResId;
		}
	}

	/** Master lookup: card-ID → CardInfo */
	private final Map<Integer, CardInfo> m_cardInfo = new HashMap<>();
	/** Ordered list used by the card-catalog grid */
	private Integer[] m_cardIDs;

	// -------------------------------------------------------------------------
	// Layout / drawing state
	// -------------------------------------------------------------------------

	private final int[] m_heldCardsOffset  = new int[4];
	private final int[] m_tableCardsOffset = new int[4];
	private final int[] m_heldCardsDrag    = new int[4];
	private final int[] m_tableCardsDrag   = new int[4];

	private int m_maxCardsDisplay = 7;

	private final Matrix m_drawMatrix = new Matrix();

	private Point m_ptDiscardPile;
	private Point m_ptDrawPile;
	private Point m_ptDiscardBadge;

	private final Point[] m_ptSeat                    = new Point[4];
	private final Point[] m_ptEmoticon                = new Point[4];
	private final Point[] m_ptHeldCardsOffsetBadge    = new Point[4];
	private final Point[] m_ptTableCardsOffsetBadge   = new Point[4];
	private final Point[] m_ptHeldCardsOverflowBadge  = new Point[4];
	private final Point[] m_ptTableCardsOverflowBadge = new Point[4];
	private final Point[] m_ptScoreText               = new Point[4];

	private Point m_ptPointer;
	private Point m_ptWinningMessage;
	private Point m_ptMessages;

	private final Rect[] m_heldCardsBoundingRect  = new Rect[4];
	private final Rect[] m_tableCardsBoundingRect = new Rect[4];
	private Rect m_drawPileBoundingRect;
	private Rect m_discardPileBoundingRect;

	private int m_bottomMarginExternal = 0;
	private int m_cardSpacing          = 0;
	private int m_cardSpacingSouth     = 0;
	private int m_cardWidth            = 0;
	private int m_cardHeight           = 0;
	private int m_emoticonWidth        = 0;
	private int m_emoticonHeight       = 0;

	// -------------------------------------------------------------------------
	// Touch state
	// -------------------------------------------------------------------------

	private Point   m_ptTouchDown           = null;
	private boolean m_heldSteady            = false;
	private boolean m_waitingForTouchAndHold = false;
	private boolean m_touchAndHold          = false;
	private boolean m_touchDrawPile         = false;
	private boolean m_touchDiscardPile      = false;
	private int     m_touchHeldCardsSeat    = 0;
	private int     m_touchTableCardsSeat   = 0;

	// -------------------------------------------------------------------------
	// Bitmaps & Paint
	// -------------------------------------------------------------------------

	private Bitmap m_bmpCardBack;
	private Bitmap m_bmpPointer;
	private Bitmap m_bmpDirection;
	private Bitmap m_bmpColorChooser;
	private Bitmap m_bmpEmoticonAggressor;
	private Bitmap m_bmpEmoticonVictim;
	private final Bitmap[] m_bmpWinningMessage = new Bitmap[4];
	private Bitmap m_bmpCardBadge;

	private final Paint m_paintScoreText;
	private final Paint m_paintCardBadgeText;
	private final Paint m_paintPointer;

	// -------------------------------------------------------------------------
	// Game references
	// -------------------------------------------------------------------------

	private Game        m_game;
	private GameOptions m_go;

	private final AnimationManager m_animationManager;
	private boolean m_discardPileOnTop = false;
	private boolean m_waitingForColor  = false;

	private boolean m_readyToStartGame   = false;
	private boolean m_waitingToStartGame = false;

	private int   m_helpCardID = -1;
	private Toast m_toast      = null;

	private final Handler m_handler = new Handler();

	// =========================================================================
	// Constructor
	// =========================================================================

	public GameTable(Context context, Game g, GameOptions go) {
		super(context);

		m_animationManager = new AnimationManager(this);
		setBackgroundResource(R.drawable.table_background);
		setFocusable(true);
		setFocusableInTouchMode(true);
		setId(VIEW_ID);

		m_go   = go;
		m_game = g;
		m_game.setGameTable(this);

		final float scale = context.getResources().getDisplayMetrics().density;

		m_paintScoreText = buildPaint(R.color.score_text, 12 * scale, Typeface.DEFAULT_BOLD, Paint.Align.CENTER);
		m_paintCardBadgeText = buildPaint(R.color.card_badge_text, 14 * scale, Typeface.DEFAULT_BOLD, Paint.Align.CENTER);
		m_paintPointer = new Paint(Paint.ANTI_ALIAS_FLAG);

		initCards();

		m_cardHeight    = m_bmpCardBack.getHeight();
		m_cardWidth     = m_bmpCardBack.getWidth();
		m_emoticonHeight = m_bmpEmoticonAggressor.getHeight();
		m_emoticonWidth  = m_bmpEmoticonAggressor.getWidth();
	}

	// =========================================================================
	// Public accessors
	// =========================================================================

	public void setHelpCardID(int id)   { m_helpCardID = id; }
	public int  getHelpCardID()         { return m_helpCardID; }

	public Card    getCardByID(int id)  { CardInfo ci = m_cardInfo.get(id); return ci != null ? ci.card : null; }
	public int     getCardImageID(int id) { CardInfo ci = m_cardInfo.get(id); return ci != null ? ci.imageResId : 0; }
	public int     getCardHelpText(int id) { CardInfo ci = m_cardInfo.get(id); return ci != null ? ci.helpResId : 0; }
	public Bitmap  getCardBitmap(int id) { CardInfo ci = m_cardInfo.get(id); return ci != null ? ci.bitmap : null; }
	public Integer[] getCardIDs()       { return m_cardIDs; }

	public void setBottomMargin(int m)  { m_bottomMarginExternal = m; }

	public void shutdown() {
		m_game = null;
		m_go   = null;
	}

	// =========================================================================
	// Layout
	// =========================================================================

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		int leftMargin   = m_cardWidth  / 4;
		int rightMargin  = m_cardWidth  / 4;
		int topMargin    = m_cardHeight / 3;
		int bottomMargin = m_cardHeight / 3 + m_bottomMarginExternal;

		if (h < 4.5 * m_cardHeight) {
			// Landscape on a small device
			topMargin    = m_cardHeight / 4;
			bottomMargin = m_cardHeight / 4 + m_bottomMarginExternal;
		}

		Rect textBounds = new Rect();
		m_paintScoreText.getTextBounds("0", 0, 1, textBounds);

		m_cardSpacing      = m_cardWidth / 2;
		m_cardSpacingSouth = 2 * m_cardWidth / 3;

		// Max cards — layout 1: N/S cards live between E/W cards
		int humanArea1     = w - 2 * m_cardWidth - 2 * leftMargin - 2 * rightMargin;
		int maxHuman1      = ((humanArea1 - m_cardWidth) / m_cardSpacingSouth) + 1;
		int computerArea1  = h - topMargin - bottomMargin - (int)(textBounds.height() * 1.2);
		int maxComputer1   = ((computerArea1 - m_cardHeight) / m_cardSpacing) + 1;

		// Max cards — layout 2: E/W cards live between N/S cards
		int humanArea2     = w - leftMargin - rightMargin;
		int maxHuman2      = ((humanArea2 - m_cardWidth) / m_cardSpacingSouth) + 1;
		int computerArea2  = h - 2 * m_cardHeight - 2 * topMargin - 2 * bottomMargin;
		int maxComputer2   = ((computerArea2 - m_cardHeight) / m_cardSpacing) + 1;

		m_maxCardsDisplay = max(min(maxComputer1, maxHuman1), min(maxComputer2, maxHuman2));

		Log.d(TAG, "[onSizeChanged] m_maxCardsDisplay: " + m_maxCardsDisplay);

		int maxWidthHand      = (m_maxCardsDisplay - 1) * m_cardSpacing      + m_cardWidth;
		int maxHeightHand     = (m_maxCardsDisplay - 1) * m_cardSpacing      + m_cardHeight;
		int maxWidthHandHuman = (m_maxCardsDisplay - 1) * m_cardSpacingSouth + m_cardWidth;

		int vertCentre = (h - bottomMargin + topMargin) / 2;

		m_ptSeat[Game.SEAT_NORTH - 1] = new Point(w / 2,                         topMargin);
		m_ptSeat[Game.SEAT_EAST  - 1] = new Point(w - (m_cardWidth + rightMargin), vertCentre);
		m_ptSeat[Game.SEAT_SOUTH - 1] = new Point(w / 2,                         h - (m_cardHeight + bottomMargin));
		m_ptSeat[Game.SEAT_WEST  - 1] = new Point(leftMargin,                     vertCentre);

		// Pointer / direction / color-chooser bitmaps — recreated at the right size
		int pointerSize = 4 * m_cardWidth;
		m_bmpPointer      = decodeSvgDrawable(R.drawable.pointer,       pointerSize);
		m_bmpDirection    = decodeSvgDrawable(R.drawable.ring_segment,  pointerSize);
		m_bmpColorChooser = decodeSvgDrawable(R.drawable.colorchooser,  pointerSize);

		m_ptPointer      = new Point(w / 2, vertCentre);
		m_ptDrawPile     = new Point(w / 2 - m_cardWidth * 5 / 4, (vertCentre - m_cardHeight) / 2 + topMargin / 2);
		m_ptDiscardPile  = new Point(w / 2 + m_cardWidth / 4,     m_ptDrawPile.y);

		// Recalculate draw/discard pile positions to match original formula
		m_ptDrawPile    = new Point(w / 2 - m_cardWidth * 5 / 4, (h - bottomMargin + topMargin - m_cardHeight) / 2);
		m_ptDiscardPile = new Point(w / 2 + m_cardWidth / 4,     m_ptDrawPile.y);
		m_ptDiscardBadge = new Point(
				m_ptDiscardPile.x + m_cardWidth  - m_bmpCardBadge.getWidth()  / 2,
				m_ptDiscardPile.y + m_cardHeight - m_bmpCardBadge.getHeight() / 2);

		Point ptSouth = m_ptSeat[Game.SEAT_SOUTH - 1];
		m_ptWinningMessage = new Point(
				ptSouth.x - m_bmpWinningMessage[0].getWidth() / 2,
				ptSouth.y - m_cardHeight / 2 * 3 - m_bmpWinningMessage[0].getHeight() * 5 / 4);

		layoutEmoticonPoints();
		layoutBadgePoints(maxWidthHand, maxHeightHand, maxWidthHandHuman);
		layoutScoreTextPoints(maxHeightHand, textBounds);

		m_ptMessages = new Point(ptSouth.x, ptSouth.y - 3 * m_cardHeight / 4);

		super.onSizeChanged(w, h, oldw, oldh);

		m_readyToStartGame = true;
		if (m_waitingToStartGame) {
			m_waitingToStartGame = false;
			m_game.start();
		}
	}

	/** Creates a Bitmap by drawing a VectorDrawable into a square canvas. */
	private Bitmap decodeSvgDrawable(int resId, int size) {
		Drawable d = getContext().getResources().getDrawable(resId);
		Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
		d.setBounds(0, 0, size, size);
		d.draw(new Canvas(bmp));
		return bmp;
	}

	private void layoutEmoticonPoints() {
		m_ptEmoticon[Game.SEAT_NORTH - 1] = new Point(
				m_ptSeat[Game.SEAT_NORTH - 1].x - m_emoticonWidth / 2,
				m_ptSeat[Game.SEAT_NORTH - 1].y + m_cardHeight * 11 / 10);
		m_ptEmoticon[Game.SEAT_EAST - 1] = new Point(
				m_ptSeat[Game.SEAT_EAST - 1].x - m_emoticonWidth - m_cardWidth / 10,
				m_ptSeat[Game.SEAT_EAST - 1].y - m_emoticonHeight / 2);
		m_ptEmoticon[Game.SEAT_SOUTH - 1] = new Point(
				m_ptSeat[Game.SEAT_SOUTH - 1].x - m_emoticonWidth / 2,
				m_ptSeat[Game.SEAT_SOUTH - 1].y - m_emoticonHeight - m_cardHeight / 10);
		m_ptEmoticon[Game.SEAT_WEST - 1] = new Point(
				m_ptSeat[Game.SEAT_WEST - 1].x + m_cardWidth * 11 / 10,
				m_ptSeat[Game.SEAT_WEST - 1].y - m_emoticonHeight / 2);
	}

	private void layoutBadgePoints(int maxWidthHand, int maxHeightHand, int maxWidthHandHuman) {
		int bw = m_bmpCardBadge.getWidth();
		int bh = m_bmpCardBadge.getHeight();

		// NORTH
		int x = m_ptSeat[Game.SEAT_NORTH - 1].x - maxWidthHand / 2 - bw / 2;
		int y = m_ptSeat[Game.SEAT_NORTH - 1].y - bh / 2;
		m_ptHeldCardsOffsetBadge[Game.SEAT_NORTH - 1]    = new Point(x, y);
		m_ptTableCardsOffsetBadge[Game.SEAT_NORTH - 1]   = new Point(x, y + m_cardHeight * 3 / 2);
		x = m_ptSeat[Game.SEAT_NORTH - 1].x + maxWidthHand / 2 - bw / 2;
		m_ptHeldCardsOverflowBadge[Game.SEAT_NORTH - 1]  = new Point(x, y);
		m_ptTableCardsOverflowBadge[Game.SEAT_NORTH - 1] = new Point(x, y + m_cardHeight * 3 / 2);

		// EAST
		x = m_ptSeat[Game.SEAT_EAST - 1].x + m_cardWidth - bw / 2;
		y = m_ptSeat[Game.SEAT_EAST - 1].y - maxHeightHand / 2 - bh / 2;
		m_ptHeldCardsOffsetBadge[Game.SEAT_EAST - 1]    = new Point(x, y);
		m_ptTableCardsOffsetBadge[Game.SEAT_EAST - 1]   = new Point(x - m_cardWidth * 3 / 2, y);
		y = m_ptSeat[Game.SEAT_EAST - 1].y + maxHeightHand / 2 - bh / 2;
		m_ptHeldCardsOverflowBadge[Game.SEAT_EAST - 1]  = new Point(x, y);
		m_ptTableCardsOverflowBadge[Game.SEAT_EAST - 1] = new Point(x - m_cardWidth * 3 / 2, y);

		// SOUTH
		x = m_ptSeat[Game.SEAT_SOUTH - 1].x - maxWidthHandHuman / 2 - bw / 2;
		y = m_ptSeat[Game.SEAT_SOUTH - 1].y + m_cardHeight - bh / 2;
		m_ptHeldCardsOffsetBadge[Game.SEAT_SOUTH - 1]    = new Point(x, y);
		m_ptTableCardsOffsetBadge[Game.SEAT_SOUTH - 1]   = new Point(x, y - m_cardHeight * 5 / 3);
		x = m_ptSeat[Game.SEAT_SOUTH - 1].x + maxWidthHandHuman / 2 - bw / 2;
		m_ptHeldCardsOverflowBadge[Game.SEAT_SOUTH - 1]  = new Point(x, y);
		m_ptTableCardsOverflowBadge[Game.SEAT_SOUTH - 1] = new Point(x, y - m_cardHeight * 5 / 3);

		// WEST
		x = m_ptSeat[Game.SEAT_WEST - 1].x - bw / 2;
		y = m_ptSeat[Game.SEAT_WEST - 1].y - maxHeightHand / 2 - bh / 2;
		m_ptHeldCardsOffsetBadge[Game.SEAT_WEST - 1]    = new Point(x, y);
		m_ptTableCardsOffsetBadge[Game.SEAT_WEST - 1]   = new Point(x + m_cardWidth * 3 / 2, y);
		y = m_ptSeat[Game.SEAT_WEST - 1].y + maxHeightHand / 2 - bh / 2;
		m_ptHeldCardsOverflowBadge[Game.SEAT_WEST - 1]  = new Point(x, y);
		m_ptTableCardsOverflowBadge[Game.SEAT_WEST - 1] = new Point(x + m_cardWidth * 3 / 2, y);
	}

	private void layoutScoreTextPoints(int maxHeightHand, Rect textBounds) {
		int th = (int)(textBounds.height() * 1.1);
		m_ptScoreText[Game.SEAT_NORTH - 1] = new Point(
				m_ptSeat[Game.SEAT_NORTH - 1].x,
				m_ptSeat[Game.SEAT_NORTH - 1].y - th);
		m_ptScoreText[Game.SEAT_EAST - 1] = new Point(
				m_ptSeat[Game.SEAT_EAST - 1].x + m_cardWidth,
				m_ptSeat[Game.SEAT_EAST - 1].y - maxHeightHand / 2 - th);
		m_ptScoreText[Game.SEAT_SOUTH - 1] = new Point(
				m_ptSeat[Game.SEAT_SOUTH - 1].x,
				m_ptSeat[Game.SEAT_SOUTH - 1].y + m_cardHeight + (int)(textBounds.height() * 1.5));
		m_ptScoreText[Game.SEAT_WEST - 1] = new Point(
				m_ptSeat[Game.SEAT_WEST - 1].x,
				m_ptSeat[Game.SEAT_WEST - 1].y - maxHeightHand / 2 - th);
	}

	// =========================================================================
	// Card animation helpers (unchanged logic, just cleaner delegation)
	// =========================================================================

	public void moveCardToPlayer(Card card, Player player, int speed) {
		m_discardPileOnTop = false;
		card.setX(m_ptDrawPile.x);
		card.setY(m_ptDrawPile.y);
		startCardAnimation(card, Card.CardState.HAND,
				m_ptSeat[player.getSeat() - 1].x, m_ptSeat[player.getSeat() - 1].y,
				0, player.getHand().isFaceUp(), m_game.getDelay() / 4);
		m_game.waitABit(speed);
	}

	public void swapCheatCard(Card c1, Card c2, int speed) {
		m_discardPileOnTop = false;
		c1.setX(m_ptDrawPile.x);
		c1.setY(m_ptDrawPile.y);
		startCardAnimation(c1, Card.CardState.HAND, m_ptSeat[Game.SEAT_SOUTH - 1].x, m_ptSeat[Game.SEAT_SOUTH - 1].y,
				0, true, m_game.getDelay() / 4);
		startCardAnimation(c2, Card.CardState.DRAW_PILE, m_ptDrawPile.x, m_ptDrawPile.y,
				0, m_go.getFaceUp(), m_game.getDelay() / 4);
		m_game.waitABit(speed);
	}

	public void dealCard(Card card, int dealer, Player player, int speed) {
		m_discardPileOnTop = false;
		int d = dealer - 1;
		card.setX(m_ptSeat[d].x - m_cardWidth  / 2 * (1 - d % 2) + m_cardWidth  * 2 * (d % 2 == 1 ? d - 2 : 0));
		card.setY(m_ptSeat[d].y - m_cardHeight / 2 * (d % 2)      + m_cardHeight * 2 * (d % 2 == 0 ? 1 - d : 0));
		startCardAnimation(card, Card.CardState.HAND,
				m_ptSeat[player.getSeat() - 1].x, m_ptSeat[player.getSeat() - 1].y,
				0, player.getHand().isFaceUp(), m_game.getDelay() / 4);
		m_game.waitABit(speed);
	}

	public void moveCardToTable(Card card, int speed) {
		startCardAnimation(card, Card.CardState.HAND, card.getX(), card.getY(), 0, true, m_game.getDelay() / 4);
		m_game.waitABit(speed);
	}

	public void moveCardToDiscardPile(Card card) {
		m_discardPileOnTop = true;
		startCardAnimation(card, Card.CardState.DISCARD_PILE,
				m_ptDiscardPile.x, m_ptDiscardPile.y, 0, true, m_game.getDelay() / 4);
		m_game.waitABit(2);
		startDirectionIndicatorAnimation(m_game.getDirection(), m_game.getCurrColor());
	}

	private void startCardAnimation(Card card, Card.CardState toState,
									float toX, float toY, float toRot,
									boolean faceUp, long duration) {
		card.setState(Card.CardState.MOVING);
		m_animationManager.startAnimation(card,
				new AnimationParams().setCardParams(toState, toX, toY, toRot, faceUp, 0, duration));
	}

	public void startPointerAnimation(float toRot, int direction) {
		m_animationManager.startAnimation(Pointer.getInstance(),
				new AnimationParams().setPointerParams(toRot, direction, 0, m_game.getDelay() / 4));
		m_game.waitABit(2);
	}

	public void startDirectionIndicatorAnimation(int toDirection, int toColor) {
		if (toDirection != DirectionIndicator.getInstance().getDirection()
				|| getColorRgb(toColor) != DirectionIndicator.getInstance().getSegmentColor(0)) {
			m_animationManager.startAnimation(DirectionIndicator.getInstance(),
					new AnimationParams().setDirectionIndicatorParams(
							toDirection, getColorRgb(toColor), 0, m_game.getDelay() / 4));
			m_game.waitABit(2);
		}
	}

	public void startColorChooserAnimation(int toDirection, boolean show) {
		m_animationManager.startAnimation(ColorChooser.getInstance(),
				new AnimationParams().setColorChooserParams(toDirection, show, 0, m_game.getDelay() / 4));
	}

	// =========================================================================
	// Game-flow helpers
	// =========================================================================

	public void startGameWhenReady() {
		if (m_readyToStartGame) {
			m_game.start();
		} else {
			m_waitingToStartGame = true;
		}
	}

	public void showNextRoundButton(boolean show) {
		setBtnVisibility(show, ((GameActivity) getContext()).getBtnNextRound());
	}

	public void showFastForwardButton(boolean show) {
		setBtnVisibility(show, ((GameActivity) getContext()).getBtnFastForward());
	}

	private static void setBtnVisibility(boolean show, android.widget.Button btn) {
		btn.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
	}

	public void showMenuButton(boolean show) {
		GameActivity a = (GameActivity) getContext();
		if (show) a.showMenuButtons();
		else      a.hideMenuButtons();
	}

	public void RedrawTable() { invalidate(); }

	// =========================================================================
	// Touch handling
	// =========================================================================

	private final Runnable m_touchAndHoldTask = () -> {
		if (!m_waitingForTouchAndHold) return;

		m_touchAndHold = true;

		Player p = m_game.getCurrPlayer();
		if (!(p instanceof HumanPlayer) && !m_game.getRoundComplete()) return;

		Card c = findTouchedCard(m_ptTouchDown);
		if (c == null || !c.isFaceUp()) return;

		vibrateShort();
		ShowCardHelp(c);
	};

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		switch (event.getAction()) {
			case MotionEvent.ACTION_CANCEL:
				m_handler.removeCallbacks(m_touchAndHoldTask);
				m_waitingForTouchAndHold = false;
				return true;

			case MotionEvent.ACTION_DOWN:
				return handleTouchDown(event);

			case MotionEvent.ACTION_UP:
				return handleTouchUp(event);

			case MotionEvent.ACTION_MOVE:
				return handleTouchMove(event);
		}
		return super.onTouchEvent(event);
	}

	private boolean handleTouchDown(MotionEvent event) {
		int x = (int) event.getX();
		int y = (int) event.getY();
		m_ptTouchDown    = new Point(x, y);
		m_touchAndHold   = false;
		m_heldSteady     = true;
		m_touchDiscardPile = false;
		m_touchDrawPile    = false;
		m_touchHeldCardsSeat  = 0;
		m_touchTableCardsSeat = 0;

		// Identify which area was touched (priority order: South → West → North → East)
		for (int seat : new int[]{Game.SEAT_SOUTH, Game.SEAT_WEST, Game.SEAT_NORTH, Game.SEAT_EAST}) {
			int idx = seat - 1;
			if (m_heldCardsBoundingRect[idx] != null && m_heldCardsBoundingRect[idx].contains(x, y)) {
				m_touchHeldCardsSeat = seat; break;
			}
			if (m_tableCardsBoundingRect[idx] != null && m_tableCardsBoundingRect[idx].contains(x, y)) {
				m_touchTableCardsSeat = seat; break;
			}
		}

		if (m_touchHeldCardsSeat != 0 || m_touchTableCardsSeat != 0) {
			scheduleHoldTask();
			return true;
		}

		if (m_waitingForColor) {
			Point ptCenter = m_ptSeat[0]; // NORTH x used as center-x
			Point ptMid    = m_ptSeat[1]; // EAST  y used as center-y
			double dist = Math.hypot(x - ptCenter.x, y - ptMid.y);
			if (dist <= 1.5 * m_cardWidth) {
				colorChooserTapped(m_ptTouchDown);
			}
		}

		if (m_drawPileBoundingRect != null && m_drawPileBoundingRect.contains(x, y)) {
			m_touchDrawPile = true;
			scheduleHoldTask();
		}
		if (m_discardPileBoundingRect != null && m_discardPileBoundingRect.contains(x, y)) {
			m_touchDiscardPile = true;
			scheduleHoldTask();
		}
		return true;
	}

	private boolean handleTouchUp(MotionEvent event) {
		if (m_touchAndHold) return true;

		m_waitingForTouchAndHold = false;

		if (heldSteadyHand()) {
			handCardTapped(max(m_touchHeldCardsSeat, m_touchTableCardsSeat), m_ptTouchDown);
			return true;
		}
		if (heldSteadyDraw()) {
			drawPileTapped();
			return true;
		}
		if (heldSteadyDiscard()) {
			discardPileTapped();
			return true;
		}

		// Commit a pending drag
		int seat = max(m_touchHeldCardsSeat, m_touchTableCardsSeat);
		if (seat != 0) {
			int idx = seat - 1;
			commitDrag(idx, m_heldCardsDrag,  m_heldCardsOffset,
					m_game.getPlayer(idx).getHand().getHeldCards().size());
			commitDrag(idx, m_tableCardsDrag, m_tableCardsOffset,
					m_game.getPlayer(idx).getHand().getTableCards().size());
			m_touchHeldCardsSeat  = 0;
			m_touchTableCardsSeat = 0;
		}
		return true;
	}

	private void commitDrag(int idx, int[] drag, int[] offset, int cardCount) {
		if (drag[idx] != 0) {
			offset[idx] = clamp(offset[idx] + drag[idx], 0, cardCount - m_maxCardsDisplay);
			drag[idx] = 0;
		}
	}

	/** Clamps value to [lo, hi]. */
	private static int clamp(int v, int lo, int hi) { return max(lo, min(v, hi)); }

	private boolean handleTouchMove(MotionEvent event) {
		int seat = max(m_touchHeldCardsSeat, m_touchTableCardsSeat);
		if (seat == 0) return false;

		int spacing = (seat == Game.SEAT_SOUTH) ? m_cardSpacingSouth : m_cardSpacing;
		int cardOffset;
		if (seat == Game.SEAT_NORTH || seat == Game.SEAT_SOUTH) {
			cardOffset = ((int) event.getX() - m_ptTouchDown.x) / (spacing / 2);
		} else {
			cardOffset = ((int) event.getY() - m_ptTouchDown.y) / spacing;
		}

		if (cardOffset != 0 && m_heldSteady) {
			m_waitingForTouchAndHold = false;
			m_handler.removeCallbacks(m_touchAndHoldTask);
			m_heldSteady = false;
		}

		if (m_touchHeldCardsSeat == seat) {
			m_heldCardsDrag[seat - 1]  = -cardOffset;
		} else if (m_touchTableCardsSeat == seat) {
			m_tableCardsDrag[seat - 1] = -cardOffset;
		}
		invalidate();
		return true;
	}

	private void scheduleHoldTask() {
		m_waitingForTouchAndHold = true;
		m_handler.postDelayed(m_touchAndHoldTask, 1000);
	}

	private boolean heldSteadyHand()    { return (m_touchHeldCardsSeat != 0 || m_touchTableCardsSeat != 0) && m_heldSteady; }
	private boolean heldSteadyDraw()    { return m_touchDrawPile    && m_heldSteady; }
	private boolean heldSteadyDiscard() { return m_touchDiscardPile && m_heldSteady; }

	private void colorChooserTapped(Point pt) {
		int color;
		if (pt.y < m_ptSeat[1].y) {
			color = (pt.x < m_ptSeat[0].x) ? 1 : 2;
		} else {
			color = (pt.x > m_ptSeat[0].x) ? 3 : 4;
		}
		((HumanPlayer) m_game.getCurrPlayer()).setColor(color);
		m_waitingForColor = false;
		startColorChooserAnimation(m_game.getDirection(), false);
	}

	private void drawPileTapped()    { m_game.drawPileTapped(); }
	private void discardPileTapped() { m_game.discardPileTapped(); }

	private void handCardTapped(int seat, Point pt) {
		if (!m_game.roundIsActive()) return;
		Player p = m_game.getPlayer(seat - 1);
		if (p instanceof HumanPlayer) {
			Card c = findTouchedCardHand(seat, pt);
			if (c != null) ((HumanPlayer) p).turnDecisionPlayCard(c);
		}
	}

	// =========================================================================
	// Card finding
	// =========================================================================

	private Card findTouchedCard(Point pt) {
		if (m_touchDiscardPile) return findTouchedCardDiscardPile(pt);
		if (m_touchDrawPile)    return findTouchedCardDrawPile(pt);
		int seat = max(m_touchHeldCardsSeat, m_touchTableCardsSeat);
		if (seat != 0)          return findTouchedCardHand(seat, pt);
		return null;
	}

	private Card findTouchedCardHand(int seat, Point pt) {
		int spacing = (seat == Game.SEAT_SOUTH) ? m_cardSpacingSouth : m_cardSpacing;
		Hand h = m_game.getPlayer(seat - 1).getHand();

		Rect heldRect  = m_heldCardsBoundingRect[seat - 1];
		Rect tableRect = m_tableCardsBoundingRect[seat - 1];

		if (heldRect != null && heldRect.contains(pt.x, pt.y)) {
			int idx = cardIndexFromPoint(pt, heldRect, seat, spacing);
			idx = clamp(idx, 0, h.getHeldCards().size() - m_heldCardsOffset[seat - 1] - 1);
			return h.getHeldCards().get(idx + m_heldCardsOffset[seat - 1]);
		}
		if (tableRect != null && tableRect.contains(pt.x, pt.y)) {
			int idx = cardIndexFromPoint(pt, tableRect, seat, spacing);
			idx = clamp(idx, 0, h.getTableCards().size() - m_tableCardsOffset[seat - 1] - 1);
			return h.getTableCards().get(idx + m_tableCardsOffset[seat - 1]);
		}
		return null;
	}

	/** Converts a touch point into a card-list index within a bounding rect. */
	private static int cardIndexFromPoint(Point pt, Rect rect, int seat, int spacing) {
		if (seat == Game.SEAT_NORTH || seat == Game.SEAT_SOUTH) {
			return (pt.x - rect.left) / spacing;
		} else {
			return (pt.y - rect.top) / spacing;
		}
	}

	private Card findTouchedCardDiscardPile(Point pt) {
		if (m_discardPileBoundingRect.contains(pt.x, pt.y)) {
			int n = m_game.getDiscardPile().getNumCards();
			if (n > 0) return m_game.getDiscardPile().getCard(n - 1);
		}
		return null;
	}

	private Card findTouchedCardDrawPile(Point pt) {
		if (m_drawPileBoundingRect.contains(pt.x, pt.y)) {
			int n = m_game.getDrawPile().getNumCards();
			if (n > 0) return m_game.getDrawPile().getCard(n - 1);
		}
		return null;
	}

	// =========================================================================
	// Drawing
	// =========================================================================

	@Override
	protected void onDraw(Canvas canvas) {
		displayScore(canvas);

		Player currPlayer = m_game.getCurrPlayer();
		if (currPlayer != null && !m_game.getRoundComplete()) {
			drawDirectionIndicator(canvas, currPlayer);
		}

		if (currPlayer == null || m_game.getFastForward()) return;

		m_drawPileBoundingRect = drawPile(m_game.getDrawPile(), canvas, m_ptDrawPile);

		if (!m_discardPileOnTop) {
			m_discardPileBoundingRect = drawPile(m_game.getDiscardPile(), canvas, m_ptDiscardPile);
		}

		int startSeat = currPlayer.getSeat() - 1;
		for (int i = startSeat; i < startSeat + 4; i++) {
			Player p = m_game.getPlayer(i % 4);
			if (p.getActive()) RedrawHand(canvas, i % 4 + 1);
		}

		if (m_discardPileOnTop) {
			m_discardPileBoundingRect = drawPile(m_game.getDiscardPile(), canvas, m_ptDiscardPile);
		}

		drawColorChooser(canvas);

		if (m_game.getWinner() != 0) {
			m_drawMatrix.reset();
			m_drawMatrix.setTranslate(m_ptWinningMessage.x, m_ptWinningMessage.y);
			canvas.drawBitmap(m_bmpWinningMessage[m_game.getWinner() - 1], m_drawMatrix, null);
		}

		drawPenalty(canvas);
	}

	private void drawDirectionIndicator(Canvas canvas, Player currPlayer) {
		for (int i = 1; i <= DirectionIndicator.numSegments; i++) {
			m_drawMatrix.setTranslate(-m_bmpPointer.getWidth() / 2f, -m_bmpPointer.getHeight() / 2f);
			m_drawMatrix.postRotate((currPlayer.getSeat() - 1) * 90f);
			if (DirectionIndicator.getInstance().getDirection() == Game.DIR_CCLOCKWISE) {
				if (currPlayer.getSeat() % 2 == 0) m_drawMatrix.postScale(1, -1);
				else                                m_drawMatrix.postScale(-1, 1);
			}
			float segAngle = (i - 1) * (360f / DirectionIndicator.numSegments)
					* (DirectionIndicator.getInstance().getDirection() == Game.DIR_CLOCKWISE ? 1 : -1);
			m_drawMatrix.postRotate(segAngle);
			m_drawMatrix.postTranslate(m_ptPointer.x, m_ptPointer.y);
			m_paintPointer.setColorFilter(new PorterDuffColorFilter(
					DirectionIndicator.getInstance().getSegmentColor(i - 1), PorterDuff.Mode.MULTIPLY));
			canvas.drawBitmap(m_bmpDirection, m_drawMatrix, m_paintPointer);
		}

		m_drawMatrix.reset();
		m_drawMatrix.postTranslate(-m_bmpPointer.getWidth() / 2f, -m_bmpPointer.getHeight() / 2f);
		m_drawMatrix.postRotate(Pointer.getInstance().getRot());
		m_drawMatrix.postScale(Pointer.getInstance().getScale(), Pointer.getInstance().getScale());
		m_paintPointer.setColorFilter(new PorterDuffColorFilter(getColorRgb(Card.COLOR_WILD), PorterDuff.Mode.MULTIPLY));
		m_drawMatrix.postTranslate(m_ptPointer.x, m_ptPointer.y);
		canvas.drawBitmap(m_bmpPointer, m_drawMatrix, m_paintPointer);
	}

	private void drawColorChooser(Canvas canvas) {
		for (int i = 1; i <= ColorChooser.numSegments; i++) {
			float scale = ColorChooser.getInstance().getSegmentScale(i - 1);
			m_drawMatrix.setTranslate(-m_bmpPointer.getWidth() / 2f, -m_bmpPointer.getHeight() / 2f);
			m_drawMatrix.postRotate((i - 1) * (360f / ColorChooser.numSegments));
			m_drawMatrix.postScale(scale, scale);
			m_drawMatrix.postTranslate(m_ptPointer.x, m_ptPointer.y);
			m_paintPointer.setColorFilter(new PorterDuffColorFilter(
					ColorChooser.getInstance().getSegmentColor(i - 1), PorterDuff.Mode.MULTIPLY));
			canvas.drawBitmap(m_bmpColorChooser, m_drawMatrix, m_paintPointer);
		}
	}

	private Rect drawPile(CardPile pile, Canvas canvas, Point pt) {
		if (pile == null) return new Rect();

		int numCards = pile.getNumCards();
		if (numCards == 0) return new Rect(pt.x, pt.y, pt.x + m_cardWidth, pt.y + m_cardHeight);

		CardDeck deck = m_game.getDeck();
		int skip = (deck != null && deck.getNumCards() > 108) ? 32 : 16;

		int x = pt.x, y = pt.y;
		for (int i = 0; i < numCards; i++) {
			Card c = pile.getCard(i);
			if (c != null) {
                if (c.isAnimating()) {
                    this.drawCard(canvas, c);
                }
                else if (i % skip == 0 || i >= numCards - 2 ) {
                    // FIXME -- make resolution independent
                    x = pt.x + (int)((float)i / (float)skip) * 2;
                    y = pt.y + (int)((float)i / (float)skip) * 2;
                    this.drawCard (canvas, c, x, y);
                }
			}
		}
		return new Rect(pt.x, pt.y, x + m_cardWidth, y + m_cardHeight);
	}

	private void RedrawHand(Canvas cv, int seat) {
		Hand h = m_game.getPlayer(seat - 1).getHand();
		if (h == null) return;

		List<Card> tableCards = h.getTableCards();
		List<Card> heldCards  = h.getHeldCards();
		int numTable = tableCards.size();
		int numHeld  = heldCards.size();

		// Clamp offsets
		m_heldCardsOffset[seat-1]  = clamp(m_heldCardsOffset[seat-1],  0, numHeld  - m_maxCardsDisplay);
		m_tableCardsOffset[seat-1] = clamp(m_tableCardsOffset[seat-1], 0, numTable - m_maxCardsDisplay);

		int heldOffset  = clamp(m_heldCardsOffset[seat-1]  + m_heldCardsDrag[seat-1],  0, numHeld  - m_maxCardsDisplay);
		int tableOffset = clamp(m_tableCardsOffset[seat-1] + m_tableCardsDrag[seat-1], 0, numTable - m_maxCardsDisplay);

		int numHeldShowing  = min(numHeld  - m_heldCardsOffset[seat-1],  m_maxCardsDisplay);
		int numTableShowing = min(numTable - m_tableCardsOffset[seat-1], m_maxCardsDisplay);

		int spacing = (seat == Game.SEAT_SOUTH) ? m_cardSpacingSouth : m_cardSpacing;

		// --- Table cards ---
		HandLayout tableLayout = buildHandLayout(seat, true, numTableShowing, spacing);
		m_tableCardsBoundingRect[seat - 1] = tableLayout.boundingRect;
		drawCards(cv, tableCards, tableOffset, tableLayout, spacing, seat);

		// --- Held cards ---
		HandLayout heldLayout = buildHandLayout(seat, false, numHeldShowing, spacing);
		m_heldCardsBoundingRect[seat - 1] = heldLayout.boundingRect;
		drawCards(cv, heldCards, heldOffset, heldLayout, spacing, seat);

		// --- Overflow / offset badges ---
		drawBadgeIfNeeded(cv, tableOffset,                              m_ptTableCardsOffsetBadge[seat-1]);
		drawBadgeIfNeeded(cv, heldOffset,                               m_ptHeldCardsOffsetBadge[seat-1]);
		drawBadgeIfNeeded(cv, numTable - tableOffset - m_maxCardsDisplay, m_ptTableCardsOverflowBadge[seat-1]);
		drawBadgeIfNeeded(cv, numHeld  - heldOffset  - m_maxCardsDisplay, m_ptHeldCardsOverflowBadge[seat-1]);
	}

	/** Simple value object returned by buildHandLayout(). */
	private static final class HandLayout {
		int x, y, dx, dy;
		Rect boundingRect;
	}

	/**
	 * Calculates the starting position, step direction, and bounding rect for
	 * a player's hand.  Replaces the duplicated switch blocks that appeared
	 * twice in the original RedrawHand().
	 */
	private HandLayout buildHandLayout(int seat, boolean tableCards, int numShowing, int spacing) {
		HandLayout l = new HandLayout();

		switch (seat) {
			case Game.SEAT_SOUTH:
				l.dx = spacing; l.dy = 0;
				int wSouth = (numShowing < 1) ? 0 : (numShowing - 1) * spacing + m_cardWidth;
				l.x = m_ptSeat[Game.SEAT_SOUTH - 1].x - wSouth / 2;
				l.y = tableCards
						? m_ptSeat[Game.SEAT_SOUTH - 1].y - m_cardHeight * 2 / 3
						: m_ptSeat[Game.SEAT_SOUTH - 1].y;
				l.boundingRect = new Rect(l.x, l.y, l.x + wSouth, l.y + m_cardHeight);
				break;
			case Game.SEAT_WEST:
				l.dx = 0; l.dy = spacing;
				int hWest = (numShowing < 1) ? 0 : (numShowing - 1) * spacing + m_cardHeight;
				l.x = tableCards
						? m_ptSeat[Game.SEAT_WEST - 1].x + m_cardWidth / 2
						: m_ptSeat[Game.SEAT_WEST - 1].x;
				l.y = m_ptSeat[Game.SEAT_WEST - 1].y - hWest / 2;
				l.boundingRect = new Rect(l.x, l.y, l.x + m_cardWidth, l.y + hWest);
				break;
			case Game.SEAT_NORTH:
				l.dx = spacing; l.dy = 0;
				int wNorth = (numShowing < 1) ? 0 : (numShowing - 1) * spacing + m_cardWidth;
				l.x = m_ptSeat[Game.SEAT_NORTH - 1].x - wNorth / 2;
				l.y = tableCards
						? m_ptSeat[Game.SEAT_NORTH - 1].y + m_cardHeight / 2
						: m_ptSeat[Game.SEAT_NORTH - 1].y;
				l.boundingRect = new Rect(l.x, l.y, l.x + wNorth, l.y + m_cardHeight);
				break;
			case Game.SEAT_EAST:
			default:
				l.dx = 0; l.dy = spacing;
				int hEast = (numShowing < 1) ? 0 : (numShowing - 1) * spacing + m_cardHeight;
				l.x = tableCards
						? m_ptSeat[Game.SEAT_EAST - 1].x - m_cardWidth / 2
						: m_ptSeat[Game.SEAT_EAST - 1].x;
				l.y = m_ptSeat[Game.SEAT_EAST - 1].y - hEast / 2;
				l.boundingRect = new Rect(l.x, l.y, l.x + m_cardWidth, l.y + hEast);
				break;
		}
		return l;
	}

	private void drawCards(Canvas cv, List<Card> cards, int offset, HandLayout layout, int spacing, int seat) {
		int x = layout.x, y = layout.y;
		int stop = min(offset + m_maxCardsDisplay, cards.size());
		for (int j = 0; j < cards.size(); j++) {
			Card c = cards.get(j);
			if (c == null) continue;
			if (c.getState() != Card.CardState.HAND) {
				c.setTargetX(x); c.setTargetY(y);
			} else {
				c.setX(x); c.setY(y);
			}
			boolean inView = offset <= j && j < stop;
			if (inView || c.getState() != Card.CardState.HAND) {
				drawCard(cv, c);
				if (inView) { x += layout.dx; y += layout.dy; }
			}
		}
	}

	private void drawBadgeIfNeeded(Canvas cv, int count, Point pt) {
		if (count <= 0 || pt == null) return;
		m_drawMatrix.reset();
		m_drawMatrix.setTranslate(pt.x, pt.y);
		cv.drawBitmap(m_bmpCardBadge, m_drawMatrix, null);

		String text = String.valueOf(count);
		Rect bounds = new Rect();
		m_paintCardBadgeText.getTextBounds(text, 0, text.length(), bounds);
		float fx = pt.x + m_bmpCardBadge.getWidth()  / 2f;
		float fy = pt.y + m_bmpCardBadge.getHeight() / 2f + bounds.height() / 2f;
		cv.drawText(text, fx, fy, m_paintCardBadgeText);
	}

	// -------------------------------------------------------------------------
	// Card drawing
	// -------------------------------------------------------------------------

	private void drawCard(Canvas cv, Card c) {
		drawCard(cv, c, (int) c.getX(), (int) c.getY(), c.getFlip());
	}

	private void drawCard(Canvas cv, Card c, int x, int y) {
		drawCard(cv, c, x, y, c.getFlip());
	}

	private void drawCard(Canvas cv, Card c, int x, int y, float flip) {
		Camera camera = new Camera();
		m_drawMatrix.reset();
		camera.save();
		camera.rotateY(flip);
		camera.getMatrix(m_drawMatrix);
		camera.restore();
		m_drawMatrix.preTranslate(-m_cardWidth / 2f, -m_cardHeight / 2f);
		m_drawMatrix.postTranslate(x + m_cardWidth / 2f, y + m_cardHeight / 2f);

		CardInfo info = m_cardInfo.get(c.getID());
		Bitmap b = (c.isFaceUp() && info != null) ? info.bitmap : m_bmpCardBack;
		cv.drawBitmap(b, m_drawMatrix, null);
	}

	// -------------------------------------------------------------------------
	// Penalty drawing
	// -------------------------------------------------------------------------

	private void drawPenalty(Canvas cv) {
		Penalty p = m_game.getPenalty();
		if (p == null || p.getType() == Penalty.PENTYPE_NONE) return;

		if (p.getType() == Penalty.PENTYPE_CARD) {
			drawBadgeWithText(cv, m_ptDiscardBadge, "+" + p.getNumCards());
		} else if (p.getOrigCard().getID() != m_game.getLastPlayedCard().getID()) {
			Bitmap b = getCardBitmap(p.getOrigCard().getID());
			if (b != null) {
				float scale = (float) m_bmpCardBadge.getWidth() / b.getWidth();
				m_drawMatrix.reset();
				m_drawMatrix.postScale(scale, scale);
				m_drawMatrix.postTranslate(m_ptDiscardBadge.x, m_ptDiscardBadge.y);
				cv.drawBitmap(b, m_drawMatrix, null);
			}
		}

		drawEmoticon(cv, p.getVictim(),           m_bmpEmoticonVictim);
		drawEmoticon(cv, p.getGeneratingPlayer(), m_bmpEmoticonAggressor);
	}

	private void drawBadgeWithText(Canvas cv, Point pt, String text) {
		m_drawMatrix.reset();
		m_drawMatrix.setTranslate(pt.x, pt.y);
		cv.drawBitmap(m_bmpCardBadge, m_drawMatrix, null);

		Rect bounds = new Rect();
		m_paintCardBadgeText.getTextBounds(text, 0, text.length(), bounds);
		float fx = pt.x + m_bmpCardBadge.getWidth()  / 2f;
		float fy = pt.y + m_bmpCardBadge.getHeight() / 2f + bounds.height() / 2f;
		cv.drawText(text, fx, fy, m_paintCardBadgeText);
	}

	private void drawEmoticon(Canvas cv, Player player, Bitmap emoticon) {
		if (player == null) return;
		Point pt = m_ptEmoticon[player.getSeat() - 1];
		int dx = 0, dy = 0;
		if (!player.getHand().getTableCards().isEmpty()) {
			switch (player.getSeat()) {
				case Game.SEAT_SOUTH: dy -= m_cardHeight * 2 / 3; break;
				case Game.SEAT_WEST:  dx += m_cardWidth  / 2;     break;
				case Game.SEAT_NORTH: dy += m_cardHeight / 2;     break;
				case Game.SEAT_EAST:  dx -= m_cardWidth  / 2;     break;
			}
		}
		m_drawMatrix.reset();
		m_drawMatrix.setTranslate(pt.x + dx, pt.y + dy);
		cv.drawBitmap(emoticon, m_drawMatrix, null);
	}

	// =========================================================================
	// Scores
	// =========================================================================

	public void displayScore(Canvas canvas) {
		for (int i = 0; i < 4; i++) {
			Paint.Align align;
			if (i == Game.SEAT_SOUTH - 1 || i == Game.SEAT_NORTH - 1) align = Paint.Align.CENTER;
			else if (i == Game.SEAT_WEST - 1)                          align = Paint.Align.LEFT;
			else                                                        align = Paint.Align.RIGHT;
			m_paintScoreText.setTextAlign(align);

			String msg = buildScoreMessage(i);
			canvas.drawText(msg, m_ptScoreText[i].x, m_ptScoreText[i].y, m_paintScoreText);
		}
	}

	private String buildScoreMessage(int playerIndex) {
		if (!m_game.getRoundComplete()) {
			return String.valueOf(m_game.getPlayer(playerIndex).getTotalScore());
		}
		Player p = m_game.getPlayer(playerIndex);
		int lastScore    = p.getLastScore();
		int virusPenalty = p.getLastVirusPenalty();
		int totalScore   = p.getTotalScore();
		int baseScore    = totalScore - lastScore - virusPenalty;
		int templateId   = (lastScore < 0) ? R.string.msg_round_score_negative : R.string.msg_round_score_positive;
		return String.format(m_game.getString(templateId), baseScore, Math.abs(lastScore), virusPenalty, totalScore);
	}

	// =========================================================================
	// Dialogs / Prompts
	// =========================================================================

	public void ShowCardHelp(Card c) {
		m_helpCardID = c.getID();
		((GameActivity) getContext()).showCardHelp();
	}

	public void Toast(String msg) {
		if (m_toast == null) {
			m_toast = android.widget.Toast.makeText(getContext(), msg, (int)(m_game.getDelay() - 500));
			m_toast.setGravity(Gravity.TOP | Gravity.CENTER, 0, m_ptMessages.y);
		} else {
			m_toast.setText(msg);
		}
		m_toast.show();
	}

	public void PromptForVictim() {
		// Build the list of active opponents only
		int[] activeSeats = {Game.SEAT_WEST, Game.SEAT_NORTH, Game.SEAT_EAST};
		int count = 0;
		for (int seat : activeSeats) {
			if (m_game.getPlayer(seat - 1).getActive()) count++;
		}

		CharSequence[] items = new CharSequence[count];
		int[] seatMap = new int[count]; // maps item index → seat number
		count = 0;
		int[] labelIds = {R.string.seat_west, R.string.seat_north, R.string.seat_east};
		for (int k = 0; k < activeSeats.length; k++) {
			if (m_game.getPlayer(activeSeats[k] - 1).getActive()) {
				items[count]   = m_game.getString(labelIds[k]);
				seatMap[count] = activeSeats[k];
				count++;
			}
		}

		new AlertDialog.Builder(getContext())
				.setCancelable(false)
				.setTitle(R.string.prompt_victim)
				.setItems(items, (dialog, i) -> {
					Player p = m_game.getCurrPlayer();
					if (p instanceof HumanPlayer) {
						((HumanPlayer) p).setVictim(seatMap[i]);
					}
				})
				.show();
	}

	public void PromptForNumCardsToDeal() {
		new AlertDialog.Builder(getContext())
				.setCancelable(false)
				.setTitle(R.string.prompt_deal)
				.setItems(R.array.deal_values, (dialog, i) -> {
					Player p = m_game.getDealer();
					if (p instanceof HumanPlayer) {
						((HumanPlayer) p).setNumCardsToDeal(i + 5);
					}
				})
				.show();
	}

	public void PromptForColor() {
		m_waitingForColor = true;
		startColorChooserAnimation(m_game.getDirection(), true);
	}

	// =========================================================================
	// Colour utilities
	// =========================================================================

	public static int getColorRgb(int gameColor) {
		switch (gameColor) {
			case Card.COLOR_RED:    return Color.rgb(203,  13,  40);
			case Card.COLOR_GREEN:  return Color.rgb(  4, 133,  64);
			case Card.COLOR_BLUE:   return Color.rgb(  4,  86, 165);
			case Card.COLOR_YELLOW: return Color.rgb(233, 146,   6);
			case Card.COLOR_WILD:   return Color.rgb(221, 220, 215);
			default:                return Color.TRANSPARENT;
		}
	}

	// =========================================================================
	// Paint factory
	// =========================================================================

	private Paint buildPaint(int colorRes, float textSize, Typeface typeface, Paint.Align align) {
		Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
		p.setColor(getResources().getColor(colorRes));
		p.setTextSize(textSize);
		p.setTypeface(typeface);
		p.setTextAlign(align);
		return p;
	}

	// =========================================================================
	// Vibration helper (API-level aware)
	// =========================================================================

	private void vibrateShort() {
		Vibrator v = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
		if (v == null || !v.hasVibrator()) return;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			v.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
		} else {
			//noinspection deprecation
			v.vibrate(100);
		}
	}

	// =========================================================================
	// initCards() — formerly ~600 lines; now driven by a data table
	// =========================================================================

	/**
	 * Registers a card into m_cardInfo.  The "family-friendly" variant (if any)
	 * is handled by the caller passing the correct resId/helpId.
	 */
	private void registerCard(int id, int imageResId, int helpResId,
							  int color, int value) {
		BitmapFactory.Options opt = new BitmapFactory.Options();
		Bitmap bmp = BitmapFactory.decodeResource(getContext().getResources(), imageResId, opt);
		Card card  = m_game.getDeck().getCard(color, value);
		if (card != null) { m_cardInfo.put(id, new CardInfo(card, imageResId, bmp, helpResId)); }
	}

	private void initCards() {
		boolean ff = m_go.getFamilyFriendly();

		// ----- Standard numeric & action cards (4 colors × 13 cards = 52) -----
		// Each colour block: 0-9, Draw (D), Skip (S), Reverse (R)
		int[][] colourBlocks = {
				// { colorConst, id_0, id_0_hd, id_1..id_9 (sequential), id_D, id_S, id_R,
				//   img_0, img_0hd, img_1..img_9, img_D, img_S, img_R }
				// We use registerStandardColorBlock() below instead of inlining here
		};
		// Standard colors handled by helper to avoid repeating the same pattern 4×
		registerStandardColorBlock(
				Card.COLOR_RED,
				new int[]{Card.ID_RED_0, Card.ID_RED_1, Card.ID_RED_2, Card.ID_RED_3, Card.ID_RED_4,
						Card.ID_RED_5, Card.ID_RED_6, Card.ID_RED_7, Card.ID_RED_8, Card.ID_RED_9,
						Card.ID_RED_D, Card.ID_RED_S, Card.ID_RED_R},
				new int[]{R.drawable.card_red_0, R.drawable.card_red_1, R.drawable.card_red_2,
						R.drawable.card_red_3, R.drawable.card_red_4, R.drawable.card_red_5,
						R.drawable.card_red_6, R.drawable.card_red_7, R.drawable.card_red_8,
						R.drawable.card_red_9, R.drawable.card_red_d, R.drawable.card_red_s,
						R.drawable.card_red_r});

		registerStandardColorBlock(
				Card.COLOR_GREEN,
				new int[]{Card.ID_GREEN_0, Card.ID_GREEN_1, Card.ID_GREEN_2, Card.ID_GREEN_3, Card.ID_GREEN_4,
						Card.ID_GREEN_5, Card.ID_GREEN_6, Card.ID_GREEN_7, Card.ID_GREEN_8, Card.ID_GREEN_9,
						Card.ID_GREEN_D, Card.ID_GREEN_S, Card.ID_GREEN_R},
				new int[]{R.drawable.card_green_0, R.drawable.card_green_1, R.drawable.card_green_2,
						R.drawable.card_green_3, R.drawable.card_green_4, R.drawable.card_green_5,
						R.drawable.card_green_6, R.drawable.card_green_7, R.drawable.card_green_8,
						R.drawable.card_green_9, R.drawable.card_green_d, R.drawable.card_green_s,
						R.drawable.card_green_r});

		registerStandardColorBlock(
				Card.COLOR_BLUE,
				new int[]{Card.ID_BLUE_0, Card.ID_BLUE_1, Card.ID_BLUE_2, Card.ID_BLUE_3, Card.ID_BLUE_4,
						Card.ID_BLUE_5, Card.ID_BLUE_6, Card.ID_BLUE_7, Card.ID_BLUE_8, Card.ID_BLUE_9,
						Card.ID_BLUE_D, Card.ID_BLUE_S, Card.ID_BLUE_R},
				new int[]{R.drawable.card_blue_0, R.drawable.card_blue_1, R.drawable.card_blue_2,
						R.drawable.card_blue_3, R.drawable.card_blue_4, R.drawable.card_blue_5,
						R.drawable.card_blue_6, R.drawable.card_blue_7, R.drawable.card_blue_8,
						R.drawable.card_blue_9, R.drawable.card_blue_d, R.drawable.card_blue_s,
						R.drawable.card_blue_r});

		registerStandardColorBlock(
				Card.COLOR_YELLOW,
				new int[]{Card.ID_YELLOW_0, Card.ID_YELLOW_1, Card.ID_YELLOW_2, Card.ID_YELLOW_3, Card.ID_YELLOW_4,
						Card.ID_YELLOW_5, Card.ID_YELLOW_6, Card.ID_YELLOW_7, Card.ID_YELLOW_8, Card.ID_YELLOW_9,
						Card.ID_YELLOW_D, Card.ID_YELLOW_S, Card.ID_YELLOW_R},
				new int[]{R.drawable.card_yellow_0, R.drawable.card_yellow_1, R.drawable.card_yellow_2,
						R.drawable.card_yellow_3, R.drawable.card_yellow_4, R.drawable.card_yellow_5,
						R.drawable.card_yellow_6, R.drawable.card_yellow_7, R.drawable.card_yellow_8,
						R.drawable.card_yellow_9, R.drawable.card_yellow_d, R.drawable.card_yellow_s,
						R.drawable.card_yellow_r});

		// ----- Wild cards -----
		registerCard(Card.ID_WILD,           R.drawable.card_wild,            R.string.cardhelp_wild,           Card.COLOR_WILD, Card.VAL_WILD);
		registerCard(Card.ID_WILD_DRAW_FOUR, R.drawable.card_wild_drawfour,   R.string.cardhelp_wild_drawfour,  Card.COLOR_WILD, Card.VAL_WILD_DRAW);
		registerCard(Card.ID_WILD_HOS,       R.drawable.card_wild_hos,        R.string.cardhelp_wild_hos,       Card.COLOR_WILD, Card.VAL_WILD_DRAW);
		registerCard(Card.ID_WILD_HD,        R.drawable.card_wild_hd,         R.string.cardhelp_wild_hd,        Card.COLOR_WILD, Card.VAL_WILD_DRAW);
		registerCard(Card.ID_WILD_MYSTERY,   R.drawable.card_wild_mystery,    R.string.cardhelp_wild_mystery,   Card.COLOR_WILD, Card.VAL_WILD_DRAW);
		registerCard(Card.ID_WILD_DB,        R.drawable.card_wild_db,         R.string.cardhelp_wild_db,        Card.COLOR_WILD, Card.VAL_WILD_DRAW);

		// ----- Special / variant cards -----
		registerCard(Card.ID_RED_0_HD,        R.drawable.card_red_0_hd,
				ff ? R.string.cardhelp_red_0_hd_ff : R.string.cardhelp_red_0_hd,
				Card.COLOR_RED, 0);
		registerCard(Card.ID_RED_2_GLASNOST,  R.drawable.card_red_2_glasnost,  R.string.cardhelp_red_2_glasnost, Card.COLOR_RED, 2);
		registerCard(Card.ID_RED_5_MAGIC,     R.drawable.card_red_5_magic,     R.string.cardhelp_red_5_magic,    Card.COLOR_RED, 5);
		registerCard(Card.ID_RED_D_SPREADER,  R.drawable.card_red_d_spreader,  R.string.cardhelp_d_spread,       Card.COLOR_RED, Card.VAL_D_SPREAD);
		registerCard(Card.ID_RED_S_DOUBLE,    R.drawable.card_red_s_double,    R.string.cardhelp_s_double,       Card.COLOR_RED, Card.VAL_S_DOUBLE);
		registerCard(Card.ID_RED_R_SKIP,      R.drawable.card_red_r_skip,      R.string.cardhelp_r_skip,         Card.COLOR_RED, Card.VAL_R_SKIP);

		registerCard(Card.ID_GREEN_0_QUITTER, R.drawable.card_green_0_quitter,
				ff ? R.string.cardhelp_green_0_quitter_ff : R.string.cardhelp_green_0_quitter,
				Card.COLOR_GREEN, 0);
		registerCard(Card.ID_GREEN_3_AIDS,
				ff ? R.drawable.card_green_3_aids_ff : R.drawable.card_green_3_aids,
				ff ? R.string.cardhelp_green_3_aids_ff : R.string.cardhelp_green_3_aids,
				Card.COLOR_GREEN, 3);
		registerCard(Card.ID_GREEN_4_IRISH,   R.drawable.card_green_4_irish,   R.string.cardhelp_green_4_irish,  Card.COLOR_GREEN, 4);
		registerCard(Card.ID_GREEN_D_SPREADER,R.drawable.card_green_d_spreader,R.string.cardhelp_d_spread,       Card.COLOR_GREEN, Card.VAL_D_SPREAD);
		registerCard(Card.ID_GREEN_S_DOUBLE,  R.drawable.card_green_s_double,  R.string.cardhelp_s_double,       Card.COLOR_GREEN, Card.VAL_S_DOUBLE);
		registerCard(Card.ID_GREEN_R_SKIP,    R.drawable.card_green_r_skip,    R.string.cardhelp_r_skip,         Card.COLOR_GREEN, Card.VAL_R_SKIP);

		registerCard(Card.ID_BLUE_0_FUCK_YOU,
				ff ? R.drawable.card_blue_0_fuckyou_ff : R.drawable.card_blue_0_fuckyou,
				ff ? R.string.cardhelp_blue_0_fuck_you_ff : R.string.cardhelp_blue_0_fuck_you,
				Card.COLOR_BLUE, 0);
		registerCard(Card.ID_BLUE_2_SHIELD,   R.drawable.card_blue_2_shield,   R.string.cardhelp_blue_2_shield,  Card.COLOR_BLUE, 2);
		registerCard(Card.ID_BLUE_D_SPREADER, R.drawable.card_blue_d_spreader, R.string.cardhelp_d_spread,       Card.COLOR_BLUE, Card.VAL_D_SPREAD);
		registerCard(Card.ID_BLUE_S_DOUBLE,   R.drawable.card_blue_s_double,   R.string.cardhelp_s_double,       Card.COLOR_BLUE, Card.VAL_S_DOUBLE);
		registerCard(Card.ID_BLUE_R_SKIP,     R.drawable.card_blue_r_skip,     R.string.cardhelp_r_skip,         Card.COLOR_BLUE, Card.VAL_R_SKIP);

		registerCard(Card.ID_YELLOW_0_SHITTER,
				ff ? R.drawable.card_yellow_0_shitter_ff : R.drawable.card_yellow_0_shitter,
				ff ? R.string.cardhelp_yellow_0_shitter_ff : R.string.cardhelp_yellow_0_shitter,
				Card.COLOR_YELLOW, 0);
		registerCard(Card.ID_YELLOW_1_MAD,    R.drawable.card_yellow_1_mad,    R.string.cardhelp_yellow_1_mad,   Card.COLOR_YELLOW, 1);
		registerCard(Card.ID_YELLOW_69,       R.drawable.card_yellow_69,
				ff ? R.string.cardhelp_yellow_69_ff : R.string.cardhelp_yellow_69,
				Card.COLOR_YELLOW, 6);
		registerCard(Card.ID_YELLOW_D_SPREADER,R.drawable.card_yellow_d_spreader,R.string.cardhelp_d_spread,     Card.COLOR_YELLOW, Card.VAL_D_SPREAD);
		registerCard(Card.ID_YELLOW_S_DOUBLE, R.drawable.card_yellow_s_double, R.string.cardhelp_s_double,       Card.COLOR_YELLOW, Card.VAL_S_DOUBLE);
		registerCard(Card.ID_YELLOW_R_SKIP,   R.drawable.card_yellow_r_skip,   R.string.cardhelp_r_skip,         Card.COLOR_YELLOW, Card.VAL_R_SKIP);

		// ----- v3 new cards -----
		/*
		// Backstab (one per colour — Reverse variant, ×4 in deck)
		registerCard(Card.ID_RED_R_BACKSTAB,    R.drawable.card_red_r_backstab,    R.string.cardhelp_backstab, Card.COLOR_RED,    Card.VAL_R_BACKSTAB, Card.ID_RED_R_BACKSTAB,    20);
		registerCard(Card.ID_GREEN_R_BACKSTAB,  R.drawable.card_green_r_backstab,  R.string.cardhelp_backstab, Card.COLOR_GREEN,  Card.VAL_R_BACKSTAB, Card.ID_GREEN_R_BACKSTAB,  20);
		registerCard(Card.ID_BLUE_R_BACKSTAB,   R.drawable.card_blue_r_backstab,   R.string.cardhelp_backstab, Card.COLOR_BLUE,   Card.VAL_R_BACKSTAB, Card.ID_BLUE_R_BACKSTAB,   20);
		registerCard(Card.ID_YELLOW_R_BACKSTAB, R.drawable.card_yellow_r_backstab, R.string.cardhelp_backstab, Card.COLOR_YELLOW, Card.VAL_R_BACKSTAB, Card.ID_YELLOW_R_BACKSTAB, 20);

		// Dodge (one per colour — 8 variant, ×4 in deck)
		registerCard(Card.ID_RED_8_DODGE,    R.drawable.card_red_8_dodge,    R.string.cardhelp_dodge, Card.COLOR_RED,    Card.VAL_DODGE, Card.ID_RED_8_DODGE,    8);
		registerCard(Card.ID_GREEN_8_DODGE,  R.drawable.card_green_8_dodge,  R.string.cardhelp_dodge, Card.COLOR_GREEN,  Card.VAL_DODGE, Card.ID_GREEN_8_DODGE,  8);
		registerCard(Card.ID_BLUE_8_DODGE,   R.drawable.card_blue_8_dodge,   R.string.cardhelp_dodge, Card.COLOR_BLUE,   Card.VAL_DODGE, Card.ID_BLUE_8_DODGE,   8);
		registerCard(Card.ID_YELLOW_8_DODGE, R.drawable.card_yellow_8_dodge, R.string.cardhelp_dodge, Card.COLOR_YELLOW, Card.VAL_DODGE, Card.ID_YELLOW_8_DODGE, 8);

		// Clone (Green 2, Yellow 2 — ×2 in deck)
		registerCard(Card.ID_GREEN_2_CLONE,  R.drawable.card_green_2_clone,  R.string.cardhelp_clone, Card.COLOR_GREEN,  Card.VAL_CLONE, Card.ID_GREEN_2_CLONE,  20);
		registerCard(Card.ID_YELLOW_2_CLONE, R.drawable.card_yellow_2_clone, R.string.cardhelp_clone, Card.COLOR_YELLOW, Card.VAL_CLONE, Card.ID_YELLOW_2_CLONE, 20);

		// Ping (Blue 1 — directed, ×1 in deck)
		registerCard(Card.ID_BLUE_1_PING, R.drawable.card_blue_1_ping, R.string.cardhelp_ping, Card.COLOR_BLUE, Card.VAL_PING, Card.ID_BLUE_1_PING, 1);

		// Swap (Green Reverse, Yellow Reverse — ×2 in deck)
		registerCard(Card.ID_GREEN_R_SWAP,  R.drawable.card_green_r_swap,  R.string.cardhelp_swap, Card.COLOR_GREEN,  Card.VAL_SWAP, Card.ID_GREEN_R_SWAP,  20);
		registerCard(Card.ID_YELLOW_R_SWAP, R.drawable.card_yellow_r_swap, R.string.cardhelp_swap, Card.COLOR_YELLOW, Card.VAL_SWAP, Card.ID_YELLOW_R_SWAP, 20);
		*/

		// Backstab (one per colour — Reverse variant, ×4 in deck)
		registerCard(Card.ID_RED_R_BACKSTAB,    R.drawable.card_back,    R.string.cardhelp_backstab, Card.COLOR_RED,    Card.VAL_R_BACKSTAB);
		registerCard(Card.ID_GREEN_R_BACKSTAB,  R.drawable.card_back,  R.string.cardhelp_backstab, Card.COLOR_GREEN,  Card.VAL_R_BACKSTAB);
		registerCard(Card.ID_BLUE_R_BACKSTAB,   R.drawable.card_back,   R.string.cardhelp_backstab, Card.COLOR_BLUE,   Card.VAL_R_BACKSTAB);
		registerCard(Card.ID_YELLOW_R_BACKSTAB, R.drawable.card_back, R.string.cardhelp_backstab, Card.COLOR_YELLOW, Card.VAL_R_BACKSTAB);

		// Dodge (one per colour — 8 variant, ×4 in deck)
		//registerCard(Card.ID_RED_8_DODGE,    R.drawable.card_back,    R.string.cardhelp_dodge, Card.COLOR_RED,    Card.VAL_DODGE, Card.ID_RED_8_DODGE,    8);
		//registerCard(Card.ID_GREEN_8_DODGE,  R.drawable.card_back,  R.string.cardhelp_dodge, Card.COLOR_GREEN,  Card.VAL_DODGE, Card.ID_GREEN_8_DODGE,  8);
		//registerCard(Card.ID_BLUE_8_DODGE,   R.drawable.card_back,   R.string.cardhelp_dodge, Card.COLOR_BLUE,   Card.VAL_DODGE, Card.ID_BLUE_8_DODGE,   8);
		//registerCard(Card.ID_YELLOW_8_DODGE, R.drawable.card_back, R.string.cardhelp_dodge, Card.COLOR_YELLOW, Card.VAL_DODGE, Card.ID_YELLOW_8_DODGE, 8);

		// Clone (Green 2, Yellow 2 — ×2 in deck)
		registerCard(Card.ID_GREEN_2_CLONE,  R.drawable.card_back,  R.string.cardhelp_clone, Card.COLOR_GREEN,  Card.VAL_2_CLONE);
		registerCard(Card.ID_YELLOW_2_CLONE, R.drawable.card_back, R.string.cardhelp_clone, Card.COLOR_YELLOW, Card.VAL_2_CLONE);

		// Ping (Blue 1 — directed, ×1 in deck)
		registerCard(Card.ID_BLUE_1_PING, R.drawable.card_blue_1_ping, R.string.cardhelp_ping, Card.COLOR_BLUE, 1);

		// Swap (Green Reverse, Yellow Reverse — ×2 in deck)
		registerCard(Card.ID_GREEN_R_SWAP,  R.drawable.card_back,  R.string.cardhelp_swap, Card.COLOR_GREEN,  Card.VAL_R_SWAP);
		registerCard(Card.ID_YELLOW_R_SWAP, R.drawable.card_back, R.string.cardhelp_swap, Card.COLOR_YELLOW, Card.VAL_R_SWAP);

		// ----- Shared bitmaps -----
		BitmapFactory.Options opt = new BitmapFactory.Options();
		m_bmpCardBack         = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.card_back, opt);
		m_bmpEmoticonAggressor = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.emoticon_aggressor, opt);
		m_bmpEmoticonVictim   = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.emoticon_victim, opt);
		m_bmpCardBadge        = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.card_badge, opt);

		m_bmpWinningMessage[Game.SEAT_SOUTH - 1] = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.winner_south, opt);
		m_bmpWinningMessage[Game.SEAT_WEST  - 1] = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.winner_west,  opt);
		m_bmpWinningMessage[Game.SEAT_NORTH - 1] = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.winner_north, opt);
		m_bmpWinningMessage[Game.SEAT_EAST  - 1] = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.winner_east,  opt);

		// ----- Build ordered card-ID array for the catalog grid -----
		m_cardIDs = m_cardInfo.keySet().toArray(new Integer[0]);
		// Sort to match the original display order
		java.util.Arrays.sort(m_cardIDs, (id1, id2) -> {
			Card c1 = m_cardInfo.get(id1).card;
			Card c2 = m_cardInfo.get(id2).card;
			return Integer.compare(c1.getDeckIndex(), c2.getDeckIndex());
		});
	}

	/**
	 * Registers the 13 standard cards for one color (0-9, Draw, Skip, Reverse).
	 * The special-ID used in the Card constructor mirrors the original code:
	 * 0 → ID_x_0_HD for RED, ID_x_0_QUITTER for GREEN, etc.; the caller already
	 * passed the correct cardIds array so we just use id[0] as the special-id for 0-cards.
	 */
	private void registerStandardColorBlock(int color, int[] ids, int[] drawables) {
		// value sequence: 0,1,2,3,4,5,6,7,8,9, VAL_D, VAL_S, VAL_R
		int[] values  = {0,1,2,3,4,5,6,7,8,9, Card.VAL_D, Card.VAL_S, Card.VAL_R};
		int[] scores  = {0,1,2,3,4,5,6,7,8,9,      20,       20,       20};
		int[] helps   = {R.string.cardhelp_0, R.string.cardhelp_1, R.string.cardhelp_2,
				R.string.cardhelp_3, R.string.cardhelp_4, R.string.cardhelp_5,
				R.string.cardhelp_6, R.string.cardhelp_7, R.string.cardhelp_8,
				R.string.cardhelp_9, R.string.cardhelp_d, R.string.cardhelp_s,
				R.string.cardhelp_r};

		for (int i = 0; i < ids.length; i++) {
			registerCard(ids[i], drawables[i], helps[i], color, values[i]);
		}
	}
}