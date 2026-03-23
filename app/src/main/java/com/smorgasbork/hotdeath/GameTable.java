package com.smorgasbork.hotdeath;

import static java.lang.Math.*;

import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.Log;
import java.util.List;

import android.app.AlertDialog;

import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;
import android.content.Context;
import java.util.HashMap;

import android.graphics.*;
import android.content.res.Resources;


public class GameTable extends View 
{
	private static final int ID = 42;  
	
	private final int[] m_heldCardsOffset;
	private final int[] m_tableCardsOffset;
	private final int[] m_heldCardsDrag;
	private final int[] m_tableCardsDrag;

	private int m_maxCardsDisplay = 7;
	
	private final Matrix m_drawMatrix;
	
	private Point m_ptDiscardPile;
	private Point m_ptDrawPile;

	private Point m_ptDiscardBadge;
		
	private final Point[] m_ptSeat;
	private final Point[] m_ptEmoticon;
	//private final Point[] m_ptPlayerIndicator;
	private final Point[] m_ptHeldCardsOffsetBadge;
	private final Point[] m_ptTableCardsOffsetBadge;
	private final Point[] m_ptHeldCardsOverflowBadge;
	private final Point[] m_ptTableCardsOverflowBadge;
	private final Point[] m_ptScoreText;
	private Point m_ptPointer;
	private Point m_ptWinningMessage;	
	private Point m_ptMessages;
	
	private final Rect[] m_heldCardsBoundingRect;
	private final Rect[] m_tableCardsBoundingRect;
	private Rect m_drawPileBoundingRect;
	private Rect m_discardPileBoundingRect;

    private int m_bottomMarginExternal = 0;
	
	private int m_cardSpacing = 0;
	private int m_cardSpacingSouth = 0;

    // FIXME: make resolution independent (at least just query the bitmaps for their width and height)
	/*  LDPI
	private int m_cardWidth = 43;
	private int m_cardHeight = 59;
	*/
	private int m_cardWidth = 0;
	private int m_cardHeight = 0;
	
	private int m_emoticonWidth = 0;
	private int m_emoticonHeight = 0;
	
	private Point m_ptTouchDown = null;
	private boolean m_heldSteady = false;
	private boolean m_waitingForTouchAndHold = false;
	private boolean m_touchAndHold = false;
	private boolean m_touchDrawPile = false;
	private boolean m_touchDiscardPile = false;
	private int m_touchHeldCardsSeat = 0;
	private int m_touchTableCardsSeat = 0;
	
	private Integer[] m_cardIDs;
	private HashMap<Integer, Card> m_cardLookup;
	private HashMap<Integer, Integer> m_imageIDLookup;
	private HashMap<Integer, Bitmap> m_imageLookup;
	private HashMap<Integer, Integer> m_cardHelpLookup;
	
	private Bitmap m_bmpCardBack;
	private Bitmap m_bmpPointer;
	private Bitmap m_bmpDirection;
	private Bitmap m_bmpColorChooser;
	
//	private Bitmap m_bmpDirColorCCW, m_bmpDirColorCCWRed, m_bmpDirColorCCWGreen, m_bmpDirColorCCWBlue, m_bmpDirColorCCWYellow;
//	private Bitmap m_bmpDirColorCW, m_bmpDirColorCWRed, m_bmpDirColorCWGreen, m_bmpDirColorCWBlue, m_bmpDirColorCWYellow;
	private Bitmap m_bmpEmoticonAggressor, m_bmpEmoticonVictim;
//	private final Bitmap[][] m_bmpPlayerIndicator;
	private final Bitmap[] m_bmpWinningMessage;
	private Bitmap m_bmpCardBadge;

    private final Paint m_paintScoreText;
	private final Paint m_paintCardBadgeText;
	private final Paint m_paintPointer;

	private boolean m_readyToStartGame = false;
	private boolean m_waitingToStartGame = false;
	
	private final Handler m_handler = new Handler();
	
	private Toast m_toast = null;
	
	private int m_helpCardID = -1;
	
	private Game m_game;
	private GameOptions m_go;

	private final AnimationManager animationManager;
	private boolean m_discardPileOnTop = false;
	private boolean m_waitingForColor;

	public void setHelpCardID (int id)
	{
		m_helpCardID = id;
	}

	public int getHelpCardID ()
	{
		return m_helpCardID;
	}
	
	public Card getCardByID (int id)
	{
		return m_cardLookup.get(id);
	}
	
	public int getCardImageID(int id)
	{
		return m_imageIDLookup.get(id);
	}	
	
	public int getCardHelpText (int id)
	{
		return m_cardHelpLookup.get(id);
	}

	public Bitmap getCardBitmap (int id)
	{
		return m_imageLookup.get(id);
	}
	
	public Integer[] getCardIDs()
	{
		return m_cardIDs;
	}

	
	public GameTable(Context context, Game g, GameOptions go) 
	{
		super(context);

		this.animationManager = new AnimationManager(this);

		this.setBackgroundResource(R.drawable.table_background);
		
		m_drawMatrix = new Matrix();
		
		setFocusable(true);
		setFocusableInTouchMode(true);
		setId(ID);

		m_go = go;
		m_game = g;
		m_game.setGameTable (this);
		
		m_heldCardsOffset = new int[4];
		m_tableCardsOffset = new int[4];
		m_heldCardsDrag = new int[4];
		m_tableCardsDrag = new int[4];
		for (int i = 0; i < 4; i++)
		{
			m_heldCardsOffset[i] = 0;
			m_tableCardsOffset[i] = 0;
			m_heldCardsDrag[i] = 0;
			m_tableCardsDrag[i] = 0;
		}

		final float scale = getContext().getResources().getDisplayMetrics().density;

        Paint paintTable = new Paint();
		paintTable.setColor(getResources().getColor(
				R.color.table_background));

        Paint paintTableText = new Paint(Paint.ANTI_ALIAS_FLAG);
		paintTableText.setColor(getResources().getColor(
				R.color.table_text));
		paintTableText.setTextAlign(Paint.Align.CENTER);
		paintTableText.setTextSize(12 * scale);
		paintTableText.setTypeface(Typeface.DEFAULT);
        
		m_paintScoreText = new Paint(Paint.ANTI_ALIAS_FLAG);
		m_paintScoreText.setColor(getResources().getColor(
				R.color.score_text));
		m_paintScoreText.setTextSize(12 * scale);
		m_paintScoreText.setTypeface(Typeface.DEFAULT_BOLD);
		
		m_paintCardBadgeText = new Paint(Paint.ANTI_ALIAS_FLAG);
		m_paintCardBadgeText.setColor(getResources().getColor(
				R.color.card_badge_text));
		m_paintCardBadgeText.setTextAlign(Paint.Align.CENTER);
		m_paintCardBadgeText.setTextSize(14 * scale);
		m_paintCardBadgeText.setTypeface(Typeface.DEFAULT_BOLD);

		m_paintPointer = new Paint(Paint.ANTI_ALIAS_FLAG);

		m_ptSeat = new Point[4];
		m_ptEmoticon = new Point[4];
		//m_ptPlayerIndicator = new Point[4];
		m_ptHeldCardsOffsetBadge = new Point[4];
		m_ptTableCardsOffsetBadge = new Point[4];
		m_ptHeldCardsOverflowBadge = new Point[4];
		m_ptTableCardsOverflowBadge = new Point[4];
		m_ptScoreText = new Point[4];
		
		m_heldCardsBoundingRect = new Rect[4];
		m_tableCardsBoundingRect = new Rect[4];

//		m_bmpPlayerIndicator = new Bitmap[5][4];
		m_bmpWinningMessage = new Bitmap[4];
		
		initCards();
		
		m_cardHeight = m_bmpCardBack.getHeight();
		m_cardWidth = m_bmpCardBack.getWidth();
		
		m_emoticonHeight = m_bmpEmoticonAggressor.getHeight();
		m_emoticonWidth = m_bmpEmoticonAggressor.getWidth();
		
	}
	
	public void shutdown ()
	{
		m_game = null;
		m_go = null;
	}
	
	
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) 
	{
        int leftMargin = m_cardWidth / 4;
        int rightMargin = m_cardWidth / 4;
        int topMargin = m_cardHeight / 3;
        int bottomMargin = m_cardHeight / 3 + m_bottomMarginExternal;
		
		if (h < 4.5 * m_cardHeight)
		{
			// probably landscape on a small device...
			topMargin = m_cardHeight / 4;
			bottomMargin = m_cardHeight / 4 + m_bottomMarginExternal;
//			m_ptDrawPile = new Point (w / 2 - 5 * m_cardWidth / 4, h / 2 - m_cardHeight / 2);
//			m_ptDiscardPile = new Point (w / 2 + m_cardWidth / 4, h / 2 - m_cardHeight / 2);
//			m_ptDirColor = new Point (m_ptDiscardPile.x + 2 * m_cardWidth + m_bmpDirColorCCW.getWidth() / 4 - m_bmpPlayerIndicator[0][0].getWidth(), h / 2 - m_bmpDirColorCCW.getWidth() / 2);
		}
//		else
//		{
//			// portrait
//			m_ptDrawPile = new Point (w / 2 - 5 * m_cardWidth / 4, h / 2 - m_cardHeight);
//			m_ptDiscardPile = new Point (w / 2 + m_cardWidth / 4, h / 2 - m_cardHeight);
//			m_ptDirColor = new Point (w /2 - m_bmpDirColorCCW.getWidth() / 2, h / 2 + m_cardHeight / 4);
//		}

//		m_ptDiscardBadge = new Point (m_ptDiscardPile.x + m_cardWidth - m_bmpCardBadge.getWidth() / 2, m_ptDiscardPile.y + m_cardHeight - m_bmpCardBadge.getHeight() / 2);
//
//		m_ptPlayerIndicator[Game.SEAT_NORTH - 1] = new Point (m_ptDirColor.x + m_bmpDirColorCCW.getWidth() / 2 - m_bmpPlayerIndicator[0][0].getWidth() / 2, m_ptDirColor.y - m_bmpPlayerIndicator[0][0].getHeight());
//		m_ptPlayerIndicator[Game.SEAT_EAST - 1] = new Point (m_ptDirColor.x + m_bmpDirColorCCW.getWidth(), m_ptDirColor.y + m_bmpDirColorCCW.getHeight() / 2 -  m_bmpPlayerIndicator[0][0].getHeight() / 2);
//		m_ptPlayerIndicator[Game.SEAT_SOUTH - 1] = new Point (m_ptDirColor.x + m_bmpDirColorCCW.getWidth() / 2 - m_bmpPlayerIndicator[0][0].getWidth() / 2, m_ptDirColor.y + m_bmpDirColorCCW.getHeight());
//		m_ptPlayerIndicator[Game.SEAT_WEST - 1] = new Point (m_ptDirColor.x - m_bmpPlayerIndicator[0][0].getWidth(), m_ptDirColor.y + m_bmpDirColorCCW.getHeight() / 2 -  m_bmpPlayerIndicator[0][0].getHeight() / 2);

		String numstr = "0";
		Rect textBounds = new Rect();
		m_paintScoreText.getTextBounds(numstr, 0, numstr.length(), textBounds);
		
		m_cardSpacing = (int)(m_cardWidth / 2.0);
		m_cardSpacingSouth = 2 * (int)(m_cardWidth / 3.0);
		
		// figure out what the maximum number of cards you can display will be
		
		// calculate max cards in layout 1 (N/S cards live between E/W cards)
		
		int humanPlayerArea = w - 2 * m_cardWidth - 2 * leftMargin - 2 * rightMargin;
		int maxNumHumanCards = ((humanPlayerArea - m_cardWidth) / m_cardSpacingSouth) + 1;

		int computerPlayerArea = h - topMargin - bottomMargin - (int)(textBounds.height() * 1.2);
		int maxNumComputerCards = ((computerPlayerArea - m_cardHeight) / m_cardSpacing) + 1;
		
		int maxCardsLayout1 = Math.min(maxNumComputerCards, maxNumHumanCards);

		// calculate max cards in layout 2 (E/W cards live between N/S cards)
		
		humanPlayerArea = w - leftMargin - rightMargin;
		maxNumHumanCards = ((humanPlayerArea - m_cardWidth) / m_cardSpacingSouth) + 1;

		computerPlayerArea = h - 2 * m_cardHeight - 2 * topMargin - 2 * bottomMargin;
		maxNumComputerCards = ((computerPlayerArea - m_cardHeight) / m_cardSpacing) + 1;
			
		int maxCardsLayout2 = Math.min(maxNumComputerCards, maxNumHumanCards);
		
		m_maxCardsDisplay = Math.max(maxCardsLayout1, maxCardsLayout2);

		Log.d("HDU", "[onSizeChanged] maxCardsLayout1: " + maxCardsLayout1);
		Log.d("HDU", "[onSizeChanged] maxCardsLayout2: " + maxCardsLayout2);
		Log.d("HDU", "[onSizeChanged] m_maxCardsDisplay: " + m_maxCardsDisplay);


        int maxWidthHand = (m_maxCardsDisplay - 1) * m_cardSpacing + m_cardWidth;
        int maxHeightHand = (m_maxCardsDisplay - 1) * m_cardSpacing + m_cardHeight;

        int maxWidthHandHuman = (m_maxCardsDisplay - 1) * m_cardSpacingSouth + m_cardWidth;
		
		m_ptSeat[Game.SEAT_NORTH - 1] = new Point (w / 2, topMargin);
		m_ptSeat[Game.SEAT_EAST - 1] = new Point (w - (m_cardWidth + rightMargin), (h - bottomMargin + topMargin) / 2);
		m_ptSeat[Game.SEAT_SOUTH - 1] = new Point (w / 2, h - (m_cardHeight + bottomMargin));
		m_ptSeat[Game.SEAT_WEST - 1] = new Point (leftMargin, (h - bottomMargin + topMargin) / 2);

		int pointerSize = 4 * m_cardWidth;
		Resources res = this.getContext().getResources();
		Drawable drawable = res.getDrawable(R.drawable.pointer);
		m_bmpPointer = Bitmap.createBitmap(pointerSize, pointerSize, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(m_bmpPointer);
		drawable.setBounds(0, 0, pointerSize, pointerSize);
		drawable.draw(canvas);

		drawable = res.getDrawable(R.drawable.ring_segment);
		m_bmpDirection = Bitmap.createBitmap(pointerSize, pointerSize, Bitmap.Config.ARGB_8888);
		canvas = new Canvas(m_bmpDirection);
		drawable.setBounds(0, 0, pointerSize, pointerSize);
		drawable.draw(canvas);

		drawable = res.getDrawable(R.drawable.colorchooser);
		m_bmpColorChooser = Bitmap.createBitmap(pointerSize, pointerSize, Bitmap.Config.ARGB_8888);
		canvas = new Canvas(m_bmpColorChooser);
		drawable.setBounds(0, 0, pointerSize, pointerSize);
		drawable.draw(canvas);

		m_ptPointer = new Point(w / 2, (h - bottomMargin + topMargin) / 2);
		m_ptDrawPile = new Point(w / 2 - m_cardWidth * 5 / 4, (h - bottomMargin + topMargin - m_cardHeight) / 2);
		m_ptDiscardPile = new Point(w / 2 + m_cardWidth / 4, (h - bottomMargin + topMargin - m_cardHeight) / 2);
		m_ptDiscardBadge = new Point (m_ptDiscardPile.x + m_cardWidth - m_bmpCardBadge.getWidth() / 2, m_ptDiscardPile.y + m_cardHeight - m_bmpCardBadge.getHeight() / 2);
		
		m_ptWinningMessage = new Point (m_ptSeat[Game.SEAT_SOUTH - 1].x - m_bmpWinningMessage[0].getWidth() / 2, m_ptSeat[Game.SEAT_SOUTH - 1].y - m_cardHeight / 2 * 3 - m_bmpWinningMessage[0].getHeight() * 5 / 4);
		
		m_ptEmoticon[Game.SEAT_NORTH - 1] = new Point (m_ptSeat[Game.SEAT_NORTH - 1].x - m_emoticonWidth / 2, m_ptSeat[Game.SEAT_NORTH - 1].y + m_cardHeight * 11 / 10);
		m_ptEmoticon[Game.SEAT_EAST - 1] = new Point (m_ptSeat[Game.SEAT_EAST - 1].x - m_emoticonWidth - m_cardWidth / 10, m_ptSeat[Game.SEAT_EAST - 1].y - m_emoticonHeight / 2);
		m_ptEmoticon[Game.SEAT_SOUTH - 1] = new Point (m_ptSeat[Game.SEAT_SOUTH - 1].x - m_emoticonWidth / 2, m_ptSeat[Game.SEAT_SOUTH - 1].y - m_emoticonHeight - m_cardHeight / 10);
		m_ptEmoticon[Game.SEAT_WEST - 1] = new Point (m_ptSeat[Game.SEAT_WEST - 1].x + m_cardWidth * 11 / 10, m_ptSeat[Game.SEAT_WEST - 1].y - m_emoticonHeight / 2);

		int x = m_ptSeat[Game.SEAT_NORTH - 1].x - maxWidthHand / 2 - m_bmpCardBadge.getWidth() / 2;
		int y = m_ptSeat[Game.SEAT_NORTH - 1].y - m_bmpCardBadge.getHeight()  / 2;
		m_ptHeldCardsOffsetBadge[Game.SEAT_NORTH - 1] = new Point (x,y);
		y += m_cardHeight * 3 / 2;
		m_ptTableCardsOffsetBadge[Game.SEAT_NORTH - 1] = new Point (x, y);

		x = m_ptSeat[Game.SEAT_NORTH - 1].x + maxWidthHand / 2 - m_bmpCardBadge.getWidth() / 2;
		y = m_ptSeat[Game.SEAT_NORTH - 1].y - m_bmpCardBadge.getHeight()  / 2;
		m_ptHeldCardsOverflowBadge[Game.SEAT_NORTH - 1] = new Point (x,y);
		y += m_cardHeight * 3 / 2;
		m_ptTableCardsOverflowBadge[Game.SEAT_NORTH - 1] = new Point (x, y);

		x = m_ptSeat[Game.SEAT_EAST - 1].x + m_cardWidth - m_bmpCardBadge.getWidth() / 2;
		y = m_ptSeat[Game.SEAT_EAST - 1].y - maxHeightHand / 2 - m_bmpCardBadge.getHeight() / 2;
		m_ptHeldCardsOffsetBadge[Game.SEAT_EAST - 1] = new Point (x, y);
		x -= m_cardWidth * 3 / 2;
		m_ptTableCardsOffsetBadge[Game.SEAT_EAST - 1] = new Point (x, y);

		x = m_ptSeat[Game.SEAT_EAST - 1].x + m_cardWidth - m_bmpCardBadge.getWidth() / 2;
		y = m_ptSeat[Game.SEAT_EAST - 1].y + maxHeightHand / 2 - m_bmpCardBadge.getHeight() / 2;
		m_ptHeldCardsOverflowBadge[Game.SEAT_EAST - 1] = new Point (x, y);
		x -= m_cardWidth * 3 / 2;
		m_ptTableCardsOverflowBadge[Game.SEAT_EAST - 1] = new Point (x, y);

		x = m_ptSeat[Game.SEAT_SOUTH - 1].x - maxWidthHandHuman / 2 - m_bmpCardBadge.getWidth() / 2;
		y = m_ptSeat[Game.SEAT_SOUTH - 1].y + m_cardHeight - m_bmpCardBadge.getHeight() / 2;
		m_ptHeldCardsOffsetBadge[Game.SEAT_SOUTH - 1] = new Point (x, y);
		y -= m_cardHeight * 5 / 3;
		m_ptTableCardsOffsetBadge[Game.SEAT_SOUTH - 1] = new Point (x, y);

		x = m_ptSeat[Game.SEAT_SOUTH - 1].x + maxWidthHandHuman / 2 - m_bmpCardBadge.getWidth() / 2;
		y = m_ptSeat[Game.SEAT_SOUTH - 1].y + m_cardHeight - m_bmpCardBadge.getHeight() / 2;
		m_ptHeldCardsOverflowBadge[Game.SEAT_SOUTH - 1] = new Point (x, y);
		y -= m_cardHeight * 5 / 3;
		m_ptTableCardsOverflowBadge[Game.SEAT_SOUTH - 1] = new Point (x, y);

		x = m_ptSeat[Game.SEAT_WEST - 1].x - m_bmpCardBadge.getWidth() / 2;
		y = m_ptSeat[Game.SEAT_WEST - 1].y - maxHeightHand / 2 - m_bmpCardBadge.getHeight() / 2;
		m_ptHeldCardsOffsetBadge[Game.SEAT_WEST - 1] = new Point (x, y);
		x += m_cardWidth * 3 / 2;
		m_ptTableCardsOffsetBadge[Game.SEAT_WEST - 1] = new Point (x, y);

		x = m_ptSeat[Game.SEAT_WEST - 1].x - m_bmpCardBadge.getWidth() / 2;
		y = m_ptSeat[Game.SEAT_WEST - 1].y + maxHeightHand / 2 - m_bmpCardBadge.getHeight() / 2;
		m_ptHeldCardsOverflowBadge[Game.SEAT_WEST - 1] = new Point (x, y);
		x += m_cardWidth * 3 / 2;
		m_ptTableCardsOverflowBadge[Game.SEAT_WEST - 1] = new Point (x, y);

		m_ptScoreText[Game.SEAT_NORTH - 1] = new Point (m_ptSeat[Game.SEAT_NORTH - 1].x,
				m_ptSeat[Game.SEAT_NORTH - 1].y - (int)(textBounds.height() * 1.1));
		m_ptScoreText[Game.SEAT_EAST - 1] = new Point (m_ptSeat[Game.SEAT_EAST - 1].x + m_cardWidth,
			m_ptSeat[Game.SEAT_EAST - 1].y - maxHeightHand / 2 - (int)(textBounds.height() * 1.1));
		m_ptScoreText[Game.SEAT_SOUTH - 1] = new Point (m_ptSeat[Game.SEAT_SOUTH - 1].x,
				m_ptSeat[Game.SEAT_SOUTH - 1].y + m_cardHeight + (int)(textBounds.height() * 1.5));
		m_ptScoreText[Game.SEAT_WEST - 1] = new Point (m_ptSeat[Game.SEAT_WEST - 1].x,
				m_ptSeat[Game.SEAT_WEST - 1].y - maxHeightHand / 2 - (int)(textBounds.height() * 1.1));

		m_ptMessages = new Point (m_ptSeat[Game.SEAT_SOUTH - 1].x, m_ptSeat[Game.SEAT_SOUTH - 1].y - 3 * m_cardHeight / 4);
		
		super.onSizeChanged(w, h, oldw, oldh);
		
		m_readyToStartGame = true;
		if (m_waitingToStartGame)
		{
			m_waitingToStartGame = false;
			m_game.start ();
		}
	}

	public void setBottomMargin (int m) {
		m_bottomMarginExternal = m;
	}

	public void moveCardToPlayer(Card card, Player player, int speed)
	{
		m_discardPileOnTop = false;
		card.setX(m_ptDrawPile.x);
		card.setY(m_ptDrawPile.y);
		startCardAnimation(card, Card.CardState.HAND, m_ptSeat[player.getSeat() -1].x, m_ptSeat[player.getSeat() -1].y, 0, player.getHand().isFaceUp(), m_game.getDelay() / 4);
		m_game.waitABit(speed);
	}

	public void swapCheatCard(Card c1, Card c2, int speed) {
		m_discardPileOnTop = false;
		c1.setX(m_ptDrawPile.x);
		c1.setY(m_ptDrawPile.y);
		startCardAnimation(c1, Card.CardState.HAND, m_ptSeat[Game.SEAT_SOUTH - 1].x, m_ptSeat[Game.SEAT_SOUTH - 1].y, 0, true, m_game.getDelay() / 4);
		startCardAnimation(c2, Card.CardState.DRAW_PILE, m_ptDrawPile.x, m_ptDrawPile.y, 0, m_go.getFaceUp(), m_game.getDelay() / 4);
		m_game.waitABit(speed);
	}

	public void dealCard(Card card, int dealer, Player player, int speed)
	{
		m_discardPileOnTop = false;
		dealer -= 1;
		card.setX(m_ptSeat[dealer].x - m_cardWidth / 2 * (1 - dealer % 2) + m_cardWidth * 2 * (dealer % 2 == 1 ? dealer - 2 : 0));
		card.setY(m_ptSeat[dealer].y - m_cardHeight / 2 * (dealer % 2) + m_cardHeight * 2 * (dealer % 2 == 0 ? 1 - dealer : 0));
		startCardAnimation(card, Card.CardState.HAND, m_ptSeat[player.getSeat() -1].x, m_ptSeat[player.getSeat() -1].y, 0, player.getHand().isFaceUp(), m_game.getDelay() / 4);
		m_game.waitABit(speed);
	}

	public void moveCardToTable(Card card, int speed)
	{
		startCardAnimation(card, Card.CardState.HAND, card.getX(), card.getY(), 0, true, m_game.getDelay() / 4);
		m_game.waitABit(speed);
	}

	public void moveCardToDiscardPile(Card card)
	{
		m_discardPileOnTop = true;
		startCardAnimation(card, Card.CardState.DISCARD_PILE, m_ptDiscardPile.x, m_ptDiscardPile.y, 0, true, m_game.getDelay() / 4);
		m_game.waitABit(2);
		startDirectionIndicatorAnimation(m_game.getDirection(), m_game.getCurrColor());
	}

	private void startCardAnimation(Card card, Card.CardState toState, float toX, float toY, float toRot, boolean faceUp, long duration) {
		card.setState(Card.CardState.MOVING);
		animationManager.startAnimation(card, new AnimationParams().setCardParams(toState, toX, toY, toRot, faceUp, 0, duration));
	}

	public void startPointerAnimation(float toRot, int direction) 	{
		animationManager.startAnimation(Pointer.getInstance(), new AnimationParams().setPointerParams(toRot, direction, 0, m_game.getDelay() / 4 ));
		m_game.waitABit(2);
	}

	public void startDirectionIndicatorAnimation(int toDirection, int toColor) 	{
		if (toDirection != DirectionIndicator.getInstance().getDirection() || getColorRgb(toColor) != DirectionIndicator.getInstance().getSegmentColor(0)) {
			animationManager.startAnimation(DirectionIndicator.getInstance(), new AnimationParams().setDirectionIndicatorParams(toDirection, getColorRgb(toColor), 0, m_game.getDelay() / 4 ));
			m_game.waitABit(2);
		}
	}

	public void startColorChooserAnimation(int toDirection, boolean show) 	{
		animationManager.startAnimation(ColorChooser.getInstance(), new AnimationParams().setColorChooserParams(toDirection, show, 0, m_game.getDelay() / 4 ));
	}

	public void startGameWhenReady ()
	{
		if (m_readyToStartGame)
		{
			m_game.start ();
			return;
		}
		
		m_waitingToStartGame = true;
	}

	public void showNextRoundButton (boolean show)
	{
		GameActivity a = (GameActivity)(getContext());
		if (show)
		{
			a.getBtnNextRound().setVisibility(View.VISIBLE);
		}
		else
		{
			a.getBtnNextRound().setVisibility(View.INVISIBLE);
		}
	}

	public void showFastForwardButton (boolean show)
	{
		GameActivity a = (GameActivity)(getContext());
		if (show)
		{
			a.getBtnFastForward().setVisibility(View.VISIBLE);
		}
		else
		{
			a.getBtnFastForward().setVisibility(View.INVISIBLE);
		}
	}

	public void showMenuButton (boolean show)
	{
		GameActivity a = (GameActivity)(getContext());
		if (show)
		{
			a.showMenuButtons();
		}
		else
		{
			a.hideMenuButtons();
		}
	}

	private final Runnable m_touchAndHoldTask = new Runnable()
	{
		public void run() {

			// if something cancelled the wait (like ACTION_UP, ACTION_CANCEL, or a 
			// large enough ACTION_MOVE), we don't show card help
			if (!m_waitingForTouchAndHold)
			{
				return;
			}
			
			m_touchAndHold = true;

			// only show card help while it's the human player's turn or the
			// round is complete
			Player p = m_game.getCurrPlayer();
			if (!((p instanceof HumanPlayer)
					|| (m_game.getRoundComplete())))
			{
				return;
			}

			// only show card help for face-up cards!
			Card c = findTouchedCard (m_ptTouchDown);
			if (c == null)
			{
				return;
			}
			if (!c.isFaceUp())
			{
				return;
			}

			// TODO: Replace with VibrationEffect when minSdk >= 26
			android.os.Vibrator v = (android.os.Vibrator) GameTable.this.getContext().getSystemService(Context.VIBRATOR_SERVICE);
			if (v != null && v.hasVibrator()) {
				v.vibrate(100); // Simple 100ms vibration, works on all APIs
			}

			ShowCardHelp(c);
		}
	};
		
		
	private boolean heldSteadyHand()
	{
		if (m_touchHeldCardsSeat == 0 && m_touchTableCardsSeat == 0)
		{
			return false;
		}
		
		return m_heldSteady;
	}
	
	private boolean heldSteadyDraw()
	{
		// check for draw (DOWN/UP in the draw pile)
		if (!m_touchDrawPile)
		{
			return false;
		}
		
		return m_heldSteady;
	}
	
	private boolean heldSteadyDiscard()
	{
		// check for draw (DOWN/UP in the draw pile)
		if (!m_touchDiscardPile)
		{
			return false;
		}
		
		return m_heldSteady;
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event) 
	{
		if (event.getAction() == MotionEvent.ACTION_CANCEL)
		{
			m_handler.removeCallbacks(m_touchAndHoldTask);
			m_waitingForTouchAndHold = false;
			return true;
		}
		
		if (event.getAction() == MotionEvent.ACTION_DOWN)
		{
			int x = (int)(event.getX());
			int y = (int)(event.getY());

			m_ptTouchDown = new Point (x, y);
			m_touchAndHold = false;
			m_heldSteady = true;

			m_touchDiscardPile = false;
			m_touchDrawPile = false;
			m_touchHeldCardsSeat = 0;
			m_touchTableCardsSeat = 0;
			if (m_heldCardsBoundingRect[Game.SEAT_SOUTH - 1] != null
					&& m_heldCardsBoundingRect[Game.SEAT_SOUTH - 1].contains(x, y))
			{
				m_touchHeldCardsSeat = Game.SEAT_SOUTH;
			} else if (m_tableCardsBoundingRect[Game.SEAT_SOUTH - 1] != null
					&& m_tableCardsBoundingRect[Game.SEAT_SOUTH - 1].contains(x, y))
			{
				m_touchTableCardsSeat = Game.SEAT_SOUTH;
			} else if (m_heldCardsBoundingRect[Game.SEAT_WEST - 1] != null
					&& m_heldCardsBoundingRect[Game.SEAT_WEST - 1].contains(x, y))
			{
				m_touchHeldCardsSeat = Game.SEAT_WEST;
			} else if (m_tableCardsBoundingRect[Game.SEAT_WEST - 1] != null
					&& m_tableCardsBoundingRect[Game.SEAT_WEST - 1].contains(x, y))
			{
				m_touchTableCardsSeat = Game.SEAT_WEST;
			} else if (m_heldCardsBoundingRect[Game.SEAT_NORTH - 1] != null
					&& m_heldCardsBoundingRect[Game.SEAT_NORTH - 1].contains(x, y))
			{
				m_touchHeldCardsSeat = Game.SEAT_NORTH;
			} else if (m_tableCardsBoundingRect[Game.SEAT_NORTH - 1] != null
					&& m_tableCardsBoundingRect[Game.SEAT_NORTH - 1].contains(x, y)) {
				m_touchTableCardsSeat = Game.SEAT_NORTH;
			} else if (m_heldCardsBoundingRect[Game.SEAT_EAST - 1] != null
					&& m_heldCardsBoundingRect[Game.SEAT_EAST - 1].contains(x, y))
			{
				m_touchHeldCardsSeat = Game.SEAT_EAST;
			} else if (m_tableCardsBoundingRect[Game.SEAT_EAST - 1] != null
					&& m_tableCardsBoundingRect[Game.SEAT_EAST - 1].contains(x, y)) {
				m_touchTableCardsSeat = Game.SEAT_EAST;
			}

			if (m_touchHeldCardsSeat != 0 || m_touchTableCardsSeat != 0)
			{
				m_waitingForTouchAndHold = true;
				m_handler.postDelayed (m_touchAndHoldTask, 1000);

//				m_ptTouchDown = new Point (x, y);
				return true;
			}

			if (m_waitingForColor && Math.pow(x - m_ptSeat[0].x, 2) + Math.pow(y - m_ptSeat[1].y, 2) <= Math.pow(1.5 * m_cardWidth, 2))
			{
				colorChooserTapped(m_ptTouchDown);
			}

			if (m_drawPileBoundingRect != null && m_drawPileBoundingRect.contains (x, y))
			{
				m_waitingForTouchAndHold = true;
				m_handler.postDelayed (m_touchAndHoldTask, 1000);

				m_touchDrawPile = true;
			}
			
			if (m_discardPileBoundingRect != null && m_discardPileBoundingRect.contains (x, y))
			{
				m_waitingForTouchAndHold = true;
				m_handler.postDelayed (m_touchAndHoldTask, 1000);

				m_touchDiscardPile = true;
			}
						
			return true;
		}
		else if (event.getAction() == MotionEvent.ACTION_UP)
		{
			if (m_touchAndHold)
			{
				return true;
			}
			
			m_waitingForTouchAndHold = false;

			// if we haven't moved from the card we originally touched down on, 
			// we'll play that card.
			if (this.heldSteadyHand())
			{
				handCardTapped (max(m_touchHeldCardsSeat, m_touchTableCardsSeat), m_ptTouchDown);
				return true;
			}

			if (this.heldSteadyDraw())
			{
				drawPileTapped ();
				return true;
			}

			if (this.heldSteadyDiscard())
			{
				discardPileTapped ();
				return true;
			}
			
			// if we're letting up on a drag, commit the drag value
			if (m_touchHeldCardsSeat != 0 || m_touchTableCardsSeat != 0)
			{
				int idx = max(m_touchHeldCardsSeat, m_touchTableCardsSeat) - 1;
				if (m_heldCardsDrag[idx] != 0)
				{
					m_heldCardsOffset[idx] += m_heldCardsDrag[idx];

					// set bounds properly
					Player p = m_game.getPlayer(idx);
					int ncards = p.getHand().getHeldCards().size();
					
					if (m_heldCardsOffset[idx] >= ncards - m_maxCardsDisplay)
					{
						m_heldCardsOffset[idx] = ncards - m_maxCardsDisplay;
					}
					
					if (m_heldCardsOffset[idx] < 0)
					{
						m_heldCardsOffset[idx] = 0;
					}
					
					m_heldCardsDrag[idx] = 0;
				}
				if (m_tableCardsDrag[idx] != 0)
				{
					m_tableCardsOffset[idx] += m_tableCardsDrag[idx];

					// set bounds properly
					Player p = m_game.getPlayer(idx);
					int ncards = p.getHand().getTableCards().size();

					if (m_tableCardsOffset[idx] >= ncards - m_maxCardsDisplay)
					{
						m_tableCardsOffset[idx] = ncards - m_maxCardsDisplay;
					}

					if (m_tableCardsOffset[idx] < 0)
					{
						m_tableCardsOffset[idx] = 0;
					}

					m_tableCardsDrag[idx] = 0;
				}
				m_touchHeldCardsSeat = 0;
				m_touchTableCardsSeat = 0;
				return true;
			}
			
			return true;
		}
		else if (event.getAction() == MotionEvent.ACTION_MOVE)
		{
			int seat = max(m_touchHeldCardsSeat, m_touchTableCardsSeat);
			if (seat != 0)
			{
				int spacing = (seat == Game.SEAT_SOUTH) ? m_cardSpacingSouth : m_cardSpacing;
				
				int cardoffset;
				
				if (seat == Game.SEAT_NORTH || seat == Game.SEAT_SOUTH)
				{
					int distx = (int)(event.getX()) - m_ptTouchDown.x;
					cardoffset = distx / (spacing / 2);
				}
				else
				{
					int disty = (int)(event.getY()) - m_ptTouchDown.y;
					cardoffset = disty / spacing;
				}
				
				if (cardoffset != 0)
				{
					if (m_heldSteady)
					{
						Log.d("HDU", "[ACTION_MOVE] cardoffset = " + cardoffset + ", m_heldSteady=false now");
						m_waitingForTouchAndHold = false;
						m_handler.removeCallbacks(m_touchAndHoldTask);
						m_heldSteady = false;
					}
				}
				
				// invert the offset, as a slide to the left means increase the offset
				if (m_touchHeldCardsSeat == seat)
				{
					m_heldCardsDrag[m_touchHeldCardsSeat - 1] = -cardoffset;
				} else if (m_touchTableCardsSeat == seat) {
					m_tableCardsDrag[m_touchTableCardsSeat - 1] = -cardoffset;
				}
				this.invalidate();
				
				return true;
			}
		}
		return super.onTouchEvent(event);
	}

	private void colorChooserTapped(Point pt) {
		int color;
		if (pt.y < m_ptSeat[1].y) {
			if  (pt.x < m_ptSeat[0].x) {
				color = 1;
			}
			else {
				color = 2;
			}
		} else {
			if (pt.x > m_ptSeat[0].x) {
				color = 3;
			} else {
				color = 4;
			}
		}
		((HumanPlayer) m_game.getCurrPlayer()).setColor(color);
		m_waitingForColor = false;
		startColorChooserAnimation(m_game.getDirection(), false);
	}

	private void drawPileTapped ()
	{
		m_game.drawPileTapped();
	}
	
	private void discardPileTapped ()
	{
		m_game.discardPileTapped();
	}
	
	private Card findTouchedCardHand (int seat, Point pt)
	{
		int spacing = (seat == Game.SEAT_SOUTH) ? m_cardSpacingSouth : m_cardSpacing;

		Player p = m_game.getPlayer(seat - 1);
		Hand h = p.getHand();

		Rect ru = m_heldCardsBoundingRect[seat - 1];
		Rect rr = m_tableCardsBoundingRect[seat - 1];

		int idx = 0;

		if (ru != null && ru.contains(pt.x, pt.y))
		{
			switch (seat) {
				case Game.SEAT_NORTH:
				case Game.SEAT_SOUTH:
					idx = (pt.x - ru.left) / spacing;
					break;

				case Game.SEAT_WEST:
				case Game.SEAT_EAST:
					idx = (pt.y - ru.top) / spacing;
					break;
			}

			int numcardsshowing = h.getHeldCards().size() - m_heldCardsOffset[seat - 1];
			numcardsshowing = Math.min(numcardsshowing, m_maxCardsDisplay);

			if (idx >= numcardsshowing) {
				idx = numcardsshowing - 1;
			}
			idx += m_heldCardsOffset[seat - 1];
            return h.getHeldCards().get(idx);

		} else if (rr != null && rr.contains(pt.x, pt.y))
		{
			switch (seat) {
				case Game.SEAT_NORTH:
				case Game.SEAT_SOUTH:
					idx = (pt.x - rr.left) / spacing;
					break;

				case Game.SEAT_WEST:
				case Game.SEAT_EAST:
					idx = (pt.y - rr.top) / spacing;
					break;
			}

			int numcardsshowing = h.getTableCards().size() - m_tableCardsOffset[seat - 1];
			numcardsshowing = Math.min(numcardsshowing, m_maxCardsDisplay);

			if (idx >= numcardsshowing) {
				idx = numcardsshowing - 1;
			}
			idx += m_tableCardsOffset[seat - 1];
			return h.getTableCards().get(idx);
		}
		return null;
	}
	
	private Card findTouchedCardDiscardPile (Point pt)
	{
		if (m_discardPileBoundingRect.contains(pt.x, pt.y))
		{
			int numcards = m_game.getDiscardPile().getNumCards();
			if (numcards > 0)
			{
				return m_game.getDiscardPile().getCard(numcards - 1);
			}
		}
		
		return null;
	}

	private Card findTouchedCardDrawPile (Point pt)
	{
		if (m_drawPileBoundingRect.contains(pt.x, pt.y))
		{
			int numcards = m_game.getDrawPile().getNumCards();
			if (numcards > 0)
			{
				return m_game.getDrawPile().getCard(numcards - 1);
			}
		}

		return null;
	}
	
	private Card findTouchedCard (Point pt)
	{
		if (m_touchDiscardPile)
		{
			return findTouchedCardDiscardPile (pt);
		}
		if (m_touchDrawPile)
		{
			return findTouchedCardDrawPile (pt);
		}
		if (max(m_touchHeldCardsSeat, m_touchTableCardsSeat) != 0)
		{
			return findTouchedCardHand (max(m_touchHeldCardsSeat, m_touchTableCardsSeat), pt);
		}
		
		return null;
	}
	
	private void handCardTapped (int seat, Point pt)
	{
		if (!m_game.roundIsActive())
		{
			return;
		}
		
		Player p = m_game.getPlayer(seat - 1);
		if (p instanceof HumanPlayer)
		{
			Card c = findTouchedCardHand (seat, pt);
			
			if (c != null)
			{
				((HumanPlayer)p).turnDecisionPlayCard (c);
			}
		}
	}

	
	public void RedrawTable ()
	{
		this.invalidate();
	}
	
	@Override
	protected void onDraw(Canvas canvas)
	{	
		int i;

		displayScore (canvas);
				
		int x = 0;
		int y = 0;

		Player p = m_game.getCurrPlayer();
		if (p != null && !m_game.getRoundComplete())
		{

//			//Point pt = m_ptPlayerIndicator[p.getSeat() - 1];
//			if (DirectionIndicator.getInstance().getDirection() == Game.DIR_CCLOCKWISE)
			for (i = 1; i <= DirectionIndicator.numSegments; i++) {

				m_drawMatrix.setTranslate(-m_bmpPointer.getWidth() / 2f, -m_bmpPointer.getHeight() / 2f);
				m_drawMatrix.postRotate((p.getSeat() -1) * 90);
				if (DirectionIndicator.getInstance().getDirection() == Game.DIR_CCLOCKWISE)
				{
					if (p.getSeat() % 2 == 0) {
						m_drawMatrix.postScale(1, -1);
					}
					else {
						m_drawMatrix.postScale(-1, 1);
					}
				}
				m_drawMatrix.postRotate((i - 1) * (360f / DirectionIndicator.numSegments) * (DirectionIndicator.getInstance().getDirection() == Game.DIR_CLOCKWISE?1:-1));
				m_drawMatrix.postTranslate(m_ptPointer.x, m_ptPointer.y);
				m_paintPointer.setColorFilter(new PorterDuffColorFilter(DirectionIndicator.getInstance().getSegmentColor(i-1), PorterDuff.Mode.MULTIPLY));
				canvas.drawBitmap(m_bmpDirection, m_drawMatrix, m_paintPointer);;
			}

			int color = getColorRgb(Card.COLOR_WILD);
			m_drawMatrix.reset();
			m_drawMatrix.postTranslate(-m_bmpPointer.getWidth() / 2f, -m_bmpPointer.getHeight() / 2f);
			m_drawMatrix.postRotate(Pointer.getInstance().getRot());
			m_drawMatrix.postScale(Pointer.getInstance().getScale(), Pointer.getInstance().getScale());
			m_paintPointer.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
			m_drawMatrix.postTranslate(m_ptPointer.x, m_ptPointer.y);
			canvas.drawBitmap(m_bmpPointer, m_drawMatrix, m_paintPointer);


//			canvas.drawBitmap(m_bmpPlayerIndicator[curr_color - 1][p.getSeat() - 1], m_drawMatrix, null);
		}

		if (m_game.getCurrPlayer() == null || m_game.getFastForward())
		{
			return;
		}

		// draw the draw pile

		m_drawPileBoundingRect = drawPile(m_game.getDrawPile(), canvas, m_ptDrawPile);

		// draw the discard pile

		if (!m_discardPileOnTop)
		{
			m_discardPileBoundingRect = drawPile(m_game.getDiscardPile(), canvas, m_ptDiscardPile);
		}

		// draw the hands
		int seat = m_game.getCurrPlayer().getSeat()-1;

		for (i = seat; i < seat + 4; i++)
		{
			p = m_game.getPlayer(i % 4);


			// don't draw ejected players' cards

			if (p.getActive())
			{
				RedrawHand (canvas, i % 4 + 1);
			}
		}

		if (m_discardPileOnTop)
		{
			m_discardPileBoundingRect = drawPile(m_game.getDiscardPile(), canvas,m_ptDiscardPile);
		}

		for (i = 1; i <= ColorChooser.numSegments; i++) {

			m_drawMatrix.setTranslate(-m_bmpPointer.getWidth() / 2f, -m_bmpPointer.getHeight() / 2f);
			m_drawMatrix.postRotate((i - 1) * (360f / ColorChooser.numSegments));
			m_drawMatrix.postScale(ColorChooser.getInstance().getSegmentScale(i-1), ColorChooser.getInstance().getSegmentScale(i-1));
			m_drawMatrix.postTranslate(m_ptPointer.x, m_ptPointer.y);
			m_paintPointer.setColorFilter(new PorterDuffColorFilter(ColorChooser.getInstance().getSegmentColor(i-1), PorterDuff.Mode.MULTIPLY));
			canvas.drawBitmap(m_bmpColorChooser, m_drawMatrix, m_paintPointer);;
		}


		if (m_game.getWinner() != 0)
		{
			m_drawMatrix.reset();
			m_drawMatrix.setScale(1, 1);
			m_drawMatrix.setTranslate(m_ptWinningMessage.x, m_ptWinningMessage.y);

			canvas.drawBitmap(m_bmpWinningMessage[m_game.getWinner() - 1], m_drawMatrix, null);
		}

		drawPenalty(canvas);
	}

	private Rect drawPile(CardPile pile, Canvas canvas, Point pt)
	{
		if (pile != null)
		{
			Card c;
			CardDeck deck = m_game.getDeck();

			int skip = 16;
			if (deck != null)
			{
				if (deck.getNumCards () > 108)
				{
					skip = 32;
				}
			}

			int x = pt.x;
			int y = pt.y;
			int numCardsInPile = pile.getNumCards();

			for (int i = 0; i < numCardsInPile; i++)
			{
				c = pile.getCard(i);
				if (c != null) {
					if (c.isAnimating()) {
						this.drawCard(canvas, c);
					}
					else if (i % skip == 0 || i >= numCardsInPile - 2 ) {
						// FIXME -- make resolution independent
						x = pt.x + (int)((float)i / (float)skip) * 2;
						y = pt.y + (int)((float)i / (float)skip) * 2;
						this.drawCard (canvas, c, x, y);
					}
				}
			}
			return new Rect(pt.x, pt.y, x + m_cardWidth, y + m_cardHeight);
		}
		return new Rect();
	}
	
	private void RedrawHand (Canvas cv, int seat)
	{
		Hand h = m_game.getPlayer(seat - 1).getHand();
		if (h == null)
		{
			return;
		}

		List<Card> tableCards = h.getTableCards();
		List<Card> heldCards = h.getHeldCards();

		int x = 0;
		int y = 0;
		int dx = 0;
		int dy = 0;
		int numTableCards = tableCards.size();
		int numHeldCards = heldCards.size();

		// keep the offsets sane
		m_heldCardsOffset[seat-1] = max(0, min(m_heldCardsOffset[seat-1], numHeldCards - m_maxCardsDisplay));
		m_tableCardsOffset[seat-1] = max(0, min(m_tableCardsOffset[seat-1], numTableCards - m_maxCardsDisplay));

		// apply the current drag
		int heldCardsOffset = m_heldCardsOffset[seat - 1] + m_heldCardsDrag[seat - 1];
		int tableCardsOffset = m_tableCardsOffset[seat - 1] + m_tableCardsDrag[seat - 1];
		heldCardsOffset = max(0, min(heldCardsOffset, numHeldCards - m_maxCardsDisplay));
		tableCardsOffset = max(0, min(tableCardsOffset, numTableCards - m_maxCardsDisplay));

		int numHeldCardsShowing = min(numHeldCards - m_heldCardsOffset[seat - 1], m_maxCardsDisplay);
		int numTableCardsShowing = min(numTableCards - m_tableCardsOffset[seat - 1], m_maxCardsDisplay);

		int heldCardsWidth;
		int heldCardsHeight;
		int tableCardsWidth;
		int tableCardsHeight;


		int spacing = (seat == Game.SEAT_SOUTH) ? m_cardSpacingSouth : m_cardSpacing;
		
		switch (seat) {
		case Game.SEAT_SOUTH:
			dx = spacing;
			dy = 0;
			tableCardsWidth = (numTableCardsShowing - 1) * spacing + m_cardWidth;
			x = m_ptSeat[Game.SEAT_SOUTH - 1].x - tableCardsWidth / 2;
			y = m_ptSeat[Game.SEAT_SOUTH - 1].y - m_cardHeight * 2 / 3;
			m_tableCardsBoundingRect[Game.SEAT_SOUTH - 1] = new Rect(x, y, x + tableCardsWidth, y + m_cardHeight);
			break;
		case Game.SEAT_WEST:
			dx = 0;
			dy = spacing;
			tableCardsHeight = (numTableCardsShowing - 1) * spacing + m_cardHeight;
			x = m_ptSeat[Game.SEAT_WEST - 1].x + m_cardWidth / 2;
			y = m_ptSeat[Game.SEAT_WEST - 1].y - tableCardsHeight / 2;
			m_tableCardsBoundingRect[Game.SEAT_WEST - 1] = new Rect(x, y, x + m_cardWidth, y + tableCardsHeight);
			break;
		case Game.SEAT_NORTH:
			dx = spacing;
			dy = 0;
			tableCardsWidth = (numTableCardsShowing - 1) * spacing + m_cardWidth;
			x = m_ptSeat[Game.SEAT_NORTH - 1].x - tableCardsWidth / 2;
			y = m_ptSeat[Game.SEAT_NORTH - 1].y + m_cardHeight / 2;
			m_tableCardsBoundingRect[Game.SEAT_NORTH - 1] = new Rect(x, y, x + tableCardsWidth, y + m_cardHeight);
			break;
		case Game.SEAT_EAST:
			dx = 0;
			dy = spacing;
			tableCardsHeight = (numTableCardsShowing - 1) * spacing + m_cardHeight;
			x = m_ptSeat[Game.SEAT_EAST - 1].x - m_cardWidth / 2;
			y = m_ptSeat[Game.SEAT_EAST - 1].y - tableCardsHeight / 2;
			m_tableCardsBoundingRect[Game.SEAT_EAST - 1] = new Rect(x, y, x + m_cardWidth, y + tableCardsHeight);
			break;
		}

		// draw the cards that are on the table

        int stop = Math.min(tableCardsOffset + m_maxCardsDisplay, numTableCards);

		int j;
		for (j = 0; j < numTableCards; j++)
		{
			Card c = tableCards.get(j);
			if (c == null) 
			{
				continue;
			}

			if (c.getState() != Card.CardState.HAND) {
				c.setTargetX(x);
				c.setTargetY(y);
			} else {
				c.setX(x);
				c.setY(y);
			}
			if (tableCardsOffset <= j && j < stop) {
				this.drawCard(cv, c);
				x += dx;
				y += dy;
			}
		}

		switch (seat) {
			case Game.SEAT_SOUTH:
				dx = spacing;
				dy = 0;
				if (numHeldCardsShowing == 0)
				{
					heldCardsWidth = 0;
				} else {
					heldCardsWidth = (numHeldCardsShowing - 1) * spacing + m_cardWidth;
				}
				x = m_ptSeat[Game.SEAT_SOUTH - 1].x - heldCardsWidth / 2;
				y = m_ptSeat[Game.SEAT_SOUTH - 1].y;
				m_heldCardsBoundingRect[Game.SEAT_SOUTH - 1] = new Rect(x, y, x + heldCardsWidth, y + m_cardHeight);
				break;
			case Game.SEAT_WEST:
				dx = 0;
				dy = spacing;
				heldCardsHeight = (numHeldCardsShowing - 1) * spacing + m_cardHeight;
				x = m_ptSeat[Game.SEAT_WEST - 1].x;
				y = m_ptSeat[Game.SEAT_WEST - 1].y - heldCardsHeight / 2;
				m_heldCardsBoundingRect[Game.SEAT_WEST - 1] = new Rect(x, y, x + m_cardWidth, y + heldCardsHeight);
				break;
			case Game.SEAT_NORTH:
				dx = spacing;
				dy = 0;
				heldCardsWidth = (numHeldCardsShowing - 1) * spacing + m_cardWidth;
				x = m_ptSeat[Game.SEAT_NORTH - 1].x - heldCardsWidth / 2;
				y = m_ptSeat[Game.SEAT_NORTH - 1].y;
				m_heldCardsBoundingRect[Game.SEAT_NORTH - 1] = new Rect(x, y, x + heldCardsWidth, y + m_cardHeight);
				break;
			case Game.SEAT_EAST:
				dx = 0;
				dy = spacing;
				heldCardsHeight = (numHeldCardsShowing - 1) * spacing + m_cardHeight;
				x = m_ptSeat[Game.SEAT_EAST - 1].x;
				y = m_ptSeat[Game.SEAT_EAST - 1].y - heldCardsHeight / 2;
				m_heldCardsBoundingRect[Game.SEAT_EAST - 1] = new Rect(x, y, x + m_cardWidth, y + heldCardsHeight);
				break;
		}

		// draw the cards that are in the Hand

		stop = Math.min(heldCardsOffset + m_maxCardsDisplay, numHeldCards);

		for (j = 0; j < numHeldCards; j++)
		{
			Card c = heldCards.get(j);
			if (c == null)
			{
				continue;
			}

            if (c.getState() != Card.CardState.HAND) {
                c.setTargetX(x);
                c.setTargetY(y);
            } else {
                c.setX(x);
                c.setY(y);
            }
            if (heldCardsOffset <= j && j < stop || c.getState() != Card.CardState.HAND) {
				this.drawCard(cv, c);
				if (heldCardsOffset <= j && j < stop)
				{
					x += dx;
					y += dy;
				}
			}
		}

		// draw the badges if necessary
		if (tableCardsOffset > 0)
		{
			Point pt = m_ptTableCardsOffsetBadge[seat - 1];

			m_drawMatrix.reset();
			m_drawMatrix.setScale(1, 1);
			m_drawMatrix.setTranslate(pt.x, pt.y);

			cv.drawBitmap(m_bmpCardBadge, m_drawMatrix, null);

			float fx = (float)(pt.x + m_bmpCardBadge.getWidth() / 2);
			Rect textBounds = new Rect();
			String numstr = "" + tableCardsOffset;

			m_paintCardBadgeText.getTextBounds(numstr, 0, numstr.length(), textBounds);
			float fy = (float)(pt.y + m_bmpCardBadge.getHeight() / 2 + (textBounds.height() / 2));

			cv.drawText(numstr, fx, fy, m_paintCardBadgeText);
		}

		if (heldCardsOffset > 0)
		{
			Point pt = m_ptHeldCardsOffsetBadge[seat - 1];

			m_drawMatrix.reset();
			m_drawMatrix.setScale(1, 1);
			m_drawMatrix.setTranslate(pt.x, pt.y);

			cv.drawBitmap(m_bmpCardBadge, m_drawMatrix, null);

			float fx = (float)(pt.x + m_bmpCardBadge.getWidth() / 2);
			Rect textBounds = new Rect();
			String numstr = "" + heldCardsOffset;

			m_paintCardBadgeText.getTextBounds(numstr, 0, numstr.length(), textBounds);
			float fy = (float)(pt.y + m_bmpCardBadge.getHeight() / 2 + (textBounds.height() / 2));

			cv.drawText(numstr, fx, fy, m_paintCardBadgeText);
		}

		if (numTableCards > m_maxCardsDisplay + tableCardsOffset)
		{
			Point pt = m_ptTableCardsOverflowBadge[seat - 1];

			m_drawMatrix.reset();
			m_drawMatrix.setScale(1, 1);
			m_drawMatrix.setTranslate(pt.x, pt.y);

			cv.drawBitmap(m_bmpCardBadge, m_drawMatrix, null);

			float fx = (float)(pt.x + m_bmpCardBadge.getWidth() / 2);
			Rect textBounds = new Rect();
			String numstr = "" + (numTableCards - tableCardsOffset - m_maxCardsDisplay);

			m_paintCardBadgeText.getTextBounds(numstr, 0, numstr.length(), textBounds);
			float fy = (float)(pt.y + m_bmpCardBadge.getHeight() / 2 + (textBounds.height() / 2));

			cv.drawText(numstr, fx, fy, m_paintCardBadgeText);
		}

		if (numHeldCards > m_maxCardsDisplay + heldCardsOffset)
		{
			Point pt = m_ptHeldCardsOverflowBadge[seat - 1];

			m_drawMatrix.reset();
			m_drawMatrix.setScale(1, 1);
			m_drawMatrix.setTranslate(pt.x, pt.y);

			cv.drawBitmap(m_bmpCardBadge, m_drawMatrix, null);

			float fx = (float)(pt.x + m_bmpCardBadge.getWidth() / 2);
			Rect textBounds = new Rect();
			String numstr = "" + (numHeldCards - heldCardsOffset - m_maxCardsDisplay);

			m_paintCardBadgeText.getTextBounds(numstr, 0, numstr.length(), textBounds);
			float fy = (float)(pt.y + m_bmpCardBadge.getHeight() / 2 + (textBounds.height() / 2));

			cv.drawText(numstr, fx, fy, m_paintCardBadgeText);
		}
	}
	
	private void initCards ()
	{
		/*
		 * I admit -- this code is nasty; it started with a simple lookup HashMap,
		 * and gradually grew into 4 separate ones.  This could be a LOT cleaner.
		 * I also don't like that I have to create all these card objects when there
		 * are already card objects in the card deck.  But this was more convenient,
		 * and it's hard to imagine that these objects are really taking up a lot of
		 * RAM in the grand scheme of things.
		 */
		m_cardLookup = new HashMap<>();
		m_imageIDLookup = new HashMap<>();
		m_imageLookup = new HashMap<>();
		m_cardHelpLookup = new HashMap<>();
		m_cardIDs = new Integer[81];
		
	    Resources res = this.getContext().getResources ();

	    BitmapFactory.Options opt = new BitmapFactory.Options();
	    //opt.inScaled = false;

		m_bmpCardBack = BitmapFactory.decodeResource(res, R.drawable.card_back, opt);

		m_imageIDLookup.put (Card.ID_RED_0, R.drawable.card_red_0);
		m_imageLookup.put (Card.ID_RED_0, BitmapFactory.decodeResource(res, R.drawable.card_red_0, opt));
		m_cardHelpLookup.put (Card.ID_RED_0, R.string.cardhelp_0);
		m_cardLookup.put (Card.ID_RED_0, new Card(-1, Card.COLOR_RED, 0, Card.ID_RED_0_HD, 0));

		m_imageIDLookup.put (Card.ID_RED_1, R.drawable.card_red_1);
		m_imageLookup.put (Card.ID_RED_1, BitmapFactory.decodeResource(res, R.drawable.card_red_1, opt));
		m_cardHelpLookup.put (Card.ID_RED_1, R.string.cardhelp_1);
		m_cardLookup.put (Card.ID_RED_1, new Card(-1, Card.COLOR_RED, 1, Card.ID_RED_1, 1));

		m_imageIDLookup.put (Card.ID_RED_2, R.drawable.card_red_2);
		m_imageLookup.put (Card.ID_RED_2, BitmapFactory.decodeResource(res, R.drawable.card_red_2, opt));
		m_cardHelpLookup.put (Card.ID_RED_2, R.string.cardhelp_2);
		m_cardLookup.put (Card.ID_RED_2, new Card(-1, Card.COLOR_RED, 2, Card.ID_RED_2, 2));

		m_imageIDLookup.put (Card.ID_RED_3, R.drawable.card_red_3);
		m_imageLookup.put (Card.ID_RED_3, BitmapFactory.decodeResource(res, R.drawable.card_red_3, opt));
		m_cardHelpLookup.put (Card.ID_RED_3, R.string.cardhelp_3);
        m_cardLookup.put (Card.ID_RED_3, new Card(-1, Card.COLOR_RED, 3, Card.ID_RED_3, 3));
		
		m_imageIDLookup.put (Card.ID_RED_4, R.drawable.card_red_4);
		m_imageLookup.put (Card.ID_RED_4, BitmapFactory.decodeResource(res, R.drawable.card_red_4, opt));
		m_cardHelpLookup.put (Card.ID_RED_4, R.string.cardhelp_4);
        m_cardLookup.put (Card.ID_RED_4, new Card(-1, Card.COLOR_RED, 4, Card.ID_RED_4, 4));
		
		m_imageIDLookup.put (Card.ID_RED_5, R.drawable.card_red_5);
		m_imageLookup.put (Card.ID_RED_5, BitmapFactory.decodeResource(res, R.drawable.card_red_5, opt));
		m_cardHelpLookup.put (Card.ID_RED_5, R.string.cardhelp_5);
        m_cardLookup.put (Card.ID_RED_5, new Card(-1, Card.COLOR_RED, 5, Card.ID_RED_5, 5));
		
		m_imageIDLookup.put (Card.ID_RED_6, R.drawable.card_red_6);
		m_imageLookup.put (Card.ID_RED_6, BitmapFactory.decodeResource(res, R.drawable.card_red_6, opt));
		m_cardHelpLookup.put (Card.ID_RED_6, R.string.cardhelp_6);
        m_cardLookup.put (Card.ID_RED_6, new Card(-1, Card.COLOR_RED, 6, Card.ID_RED_6, 6));
		
		m_imageIDLookup.put (Card.ID_RED_7, R.drawable.card_red_7);
		m_imageLookup.put (Card.ID_RED_7, BitmapFactory.decodeResource(res, R.drawable.card_red_7, opt));
		m_cardHelpLookup.put (Card.ID_RED_7, R.string.cardhelp_7);
        m_cardLookup.put (Card.ID_RED_7, new Card(-1, Card.COLOR_RED, 7, Card.ID_RED_7, 7));
		
		m_imageIDLookup.put (Card.ID_RED_8, R.drawable.card_red_8);
		m_imageLookup.put (Card.ID_RED_8, BitmapFactory.decodeResource(res, R.drawable.card_red_8, opt));
		m_cardHelpLookup.put (Card.ID_RED_8, R.string.cardhelp_8);
        m_cardLookup.put (Card.ID_RED_8, new Card(-1, Card.COLOR_RED, 8, Card.ID_RED_8, 8));
		
		m_imageIDLookup.put (Card.ID_RED_9, R.drawable.card_red_9);
		m_imageLookup.put (Card.ID_RED_9, BitmapFactory.decodeResource(res, R.drawable.card_red_9, opt));
		m_cardHelpLookup.put (Card.ID_RED_9, R.string.cardhelp_9);
        m_cardLookup.put (Card.ID_RED_9, new Card(-1, Card.COLOR_RED, 9, Card.ID_RED_9, 9));
		
		m_imageIDLookup.put (Card.ID_RED_D, R.drawable.card_red_d);
		m_imageLookup.put (Card.ID_RED_D, BitmapFactory.decodeResource(res, R.drawable.card_red_d, opt));
		m_cardHelpLookup.put (Card.ID_RED_D, R.string.cardhelp_d);
        m_cardLookup.put (Card.ID_RED_D, new Card(-1, Card.COLOR_RED, Card.VAL_D, Card.ID_RED_D, 20));

		m_imageIDLookup.put (Card.ID_RED_S, R.drawable.card_red_s);
		m_imageLookup.put (Card.ID_RED_S, BitmapFactory.decodeResource(res, R.drawable.card_red_s, opt));
		m_cardHelpLookup.put (Card.ID_RED_S, R.string.cardhelp_s);
        m_cardLookup.put (Card.ID_RED_S, new Card(-1, Card.COLOR_RED, Card.VAL_S, Card.ID_RED_S, 20));

		m_imageIDLookup.put (Card.ID_RED_R, R.drawable.card_red_r);
		m_imageLookup.put (Card.ID_RED_R, BitmapFactory.decodeResource(res, R.drawable.card_red_r, opt));
		m_cardHelpLookup.put (Card.ID_RED_R, R.string.cardhelp_r);
        m_cardLookup.put (Card.ID_RED_R, new Card(-1, Card.COLOR_RED, Card.VAL_R, Card.ID_RED_R, 20));

		m_imageIDLookup.put (Card.ID_GREEN_0, R.drawable.card_green_0);
		m_imageLookup.put (Card.ID_GREEN_0, BitmapFactory.decodeResource(res, R.drawable.card_green_0, opt));
		m_cardHelpLookup.put (Card.ID_GREEN_0, R.string.cardhelp_0);
        m_cardLookup.put (Card.ID_GREEN_0, new Card(-1, Card.COLOR_GREEN, 0, Card.ID_GREEN_0_QUITTER, 0));
		
		m_imageIDLookup.put (Card.ID_GREEN_1, R.drawable.card_green_1);
		m_imageLookup.put (Card.ID_GREEN_1, BitmapFactory.decodeResource(res, R.drawable.card_green_1, opt));
		m_cardHelpLookup.put (Card.ID_GREEN_1, R.string.cardhelp_1);
        m_cardLookup.put (Card.ID_GREEN_1, new Card(-1, Card.COLOR_GREEN, 1, Card.ID_GREEN_1, 1));

		m_imageIDLookup.put (Card.ID_GREEN_2, R.drawable.card_green_2);
		m_imageLookup.put (Card.ID_GREEN_2, BitmapFactory.decodeResource(res, R.drawable.card_green_2, opt));
		m_cardHelpLookup.put (Card.ID_GREEN_2, R.string.cardhelp_2);
        m_cardLookup.put (Card.ID_GREEN_2, new Card(-1, Card.COLOR_GREEN, 2, Card.ID_GREEN_2, 2));
		
		m_imageIDLookup.put (Card.ID_GREEN_3, R.drawable.card_green_3);
		m_imageLookup.put (Card.ID_GREEN_3, BitmapFactory.decodeResource(res, R.drawable.card_green_3, opt));
		m_cardHelpLookup.put (Card.ID_GREEN_3, R.string.cardhelp_3);
        m_cardLookup.put (Card.ID_GREEN_3, new Card(-1, Card.COLOR_GREEN, 3, Card.ID_GREEN_3, 3));
		
		m_imageIDLookup.put (Card.ID_GREEN_4, R.drawable.card_green_4);
		m_imageLookup.put (Card.ID_GREEN_4, BitmapFactory.decodeResource(res, R.drawable.card_green_4, opt));
		m_cardHelpLookup.put (Card.ID_GREEN_4, R.string.cardhelp_4);
        m_cardLookup.put (Card.ID_GREEN_4, new Card(-1, Card.COLOR_GREEN, 4, Card.ID_GREEN_4, 4));

		m_imageIDLookup.put (Card.ID_GREEN_5, R.drawable.card_green_5);
		m_imageLookup.put (Card.ID_GREEN_5, BitmapFactory.decodeResource(res, R.drawable.card_green_5, opt));
		m_cardHelpLookup.put (Card.ID_GREEN_5, R.string.cardhelp_5);
        m_cardLookup.put (Card.ID_GREEN_5, new Card(-1, Card.COLOR_GREEN, 5, Card.ID_GREEN_5, 5));
		
		m_imageIDLookup.put (Card.ID_GREEN_6, R.drawable.card_green_6);
		m_imageLookup.put (Card.ID_GREEN_6, BitmapFactory.decodeResource(res, R.drawable.card_green_6, opt));
		m_cardHelpLookup.put (Card.ID_GREEN_6, R.string.cardhelp_6);
        m_cardLookup.put (Card.ID_GREEN_6, new Card(-1, Card.COLOR_GREEN, 6, Card.ID_GREEN_6, 6));
		
		m_imageIDLookup.put (Card.ID_GREEN_7, R.drawable.card_green_7);
		m_imageLookup.put (Card.ID_GREEN_7, BitmapFactory.decodeResource(res, R.drawable.card_green_7, opt));
		m_cardHelpLookup.put (Card.ID_GREEN_7, R.string.cardhelp_7);
        m_cardLookup.put (Card.ID_GREEN_7, new Card(-1, Card.COLOR_GREEN, 7, Card.ID_GREEN_7, 7));
		
		m_imageIDLookup.put (Card.ID_GREEN_8, R.drawable.card_green_8);
		m_imageLookup.put (Card.ID_GREEN_8, BitmapFactory.decodeResource(res, R.drawable.card_green_8, opt));
		m_cardHelpLookup.put (Card.ID_GREEN_8, R.string.cardhelp_8);
        m_cardLookup.put (Card.ID_GREEN_8, new Card(-1, Card.COLOR_GREEN, 8, Card.ID_GREEN_8, 8));
		
		m_imageIDLookup.put (Card.ID_GREEN_9, R.drawable.card_green_9);
		m_imageLookup.put (Card.ID_GREEN_9, BitmapFactory.decodeResource(res, R.drawable.card_green_9, opt));
		m_cardHelpLookup.put (Card.ID_GREEN_9, R.string.cardhelp_9);
        m_cardLookup.put (Card.ID_GREEN_9, new Card(-1, Card.COLOR_GREEN, 9, Card.ID_GREEN_9, 9));
		
		m_imageIDLookup.put (Card.ID_GREEN_D, R.drawable.card_green_d);
		m_imageLookup.put (Card.ID_GREEN_D, BitmapFactory.decodeResource(res, R.drawable.card_green_d, opt));
		m_cardHelpLookup.put (Card.ID_GREEN_D, R.string.cardhelp_d);
        m_cardLookup.put (Card.ID_GREEN_D, new Card(-1, Card.COLOR_GREEN, Card.VAL_D, Card.ID_GREEN_D, 20));

		m_imageIDLookup.put (Card.ID_GREEN_S, R.drawable.card_green_s);
		m_imageLookup.put (Card.ID_GREEN_S, BitmapFactory.decodeResource(res, R.drawable.card_green_s, opt));
		m_cardHelpLookup.put (Card.ID_GREEN_S, R.string.cardhelp_s);
        m_cardLookup.put (Card.ID_GREEN_S, new Card(-1, Card.COLOR_GREEN, Card.VAL_S, Card.ID_GREEN_S, 20));

		m_imageIDLookup.put (Card.ID_GREEN_R, R.drawable.card_green_r);
		m_imageLookup.put (Card.ID_GREEN_R, BitmapFactory.decodeResource(res, R.drawable.card_green_r, opt));
		m_cardHelpLookup.put (Card.ID_GREEN_R, R.string.cardhelp_r);
        m_cardLookup.put (Card.ID_GREEN_R, new Card(-1, Card.COLOR_GREEN, Card.VAL_R, Card.ID_GREEN_R, 20));
				
		m_imageIDLookup.put (Card.ID_BLUE_0, R.drawable.card_blue_0);
		m_imageLookup.put (Card.ID_BLUE_0, BitmapFactory.decodeResource(res, R.drawable.card_blue_0, opt));
		m_cardHelpLookup.put (Card.ID_BLUE_0, R.string.cardhelp_0);
        m_cardLookup.put (Card.ID_BLUE_0, new Card(-1, Card.COLOR_BLUE, 0, Card.ID_BLUE_0, 0));
		
		m_imageIDLookup.put (Card.ID_BLUE_1, R.drawable.card_blue_1);
		m_imageLookup.put (Card.ID_BLUE_1, BitmapFactory.decodeResource(res, R.drawable.card_blue_1, opt));
		m_cardHelpLookup.put (Card.ID_BLUE_1, R.string.cardhelp_1);
        m_cardLookup.put (Card.ID_BLUE_1, new Card(-1, Card.COLOR_BLUE, 1, Card.ID_BLUE_1, 1));

		m_imageIDLookup.put (Card.ID_BLUE_2, R.drawable.card_blue_2);
		m_imageLookup.put (Card.ID_BLUE_2, BitmapFactory.decodeResource(res, R.drawable.card_blue_2, opt));
		m_cardHelpLookup.put (Card.ID_BLUE_2, R.string.cardhelp_2);
        m_cardLookup.put (Card.ID_BLUE_2, new Card(-1, Card.COLOR_BLUE, 2, Card.ID_BLUE_2, 2));
		
		m_imageIDLookup.put (Card.ID_BLUE_3, R.drawable.card_blue_3);
		m_imageLookup.put (Card.ID_BLUE_3, BitmapFactory.decodeResource(res, R.drawable.card_blue_3, opt));
		m_cardHelpLookup.put (Card.ID_BLUE_3, R.string.cardhelp_3);
        m_cardLookup.put (Card.ID_BLUE_3, new Card(-1, Card.COLOR_BLUE, 3, Card.ID_BLUE_3, 3));
		
		m_imageIDLookup.put (Card.ID_BLUE_4, R.drawable.card_blue_4);
		m_imageLookup.put (Card.ID_BLUE_4, BitmapFactory.decodeResource(res, R.drawable.card_blue_4, opt));
		m_cardHelpLookup.put (Card.ID_BLUE_4, R.string.cardhelp_4);
        m_cardLookup.put (Card.ID_BLUE_4, new Card(-1, Card.COLOR_BLUE, 4, Card.ID_BLUE_4, 4));
		
		m_imageIDLookup.put (Card.ID_BLUE_5, R.drawable.card_blue_5);
		m_imageLookup.put (Card.ID_BLUE_5, BitmapFactory.decodeResource(res, R.drawable.card_blue_5, opt));
		m_cardHelpLookup.put (Card.ID_BLUE_5, R.string.cardhelp_5);
        m_cardLookup.put (Card.ID_BLUE_5, new Card(-1, Card.COLOR_BLUE, 5, Card.ID_BLUE_5, 5));
		
		m_imageIDLookup.put (Card.ID_BLUE_6, R.drawable.card_blue_6);
		m_imageLookup.put (Card.ID_BLUE_6, BitmapFactory.decodeResource(res, R.drawable.card_blue_6, opt));
		m_cardHelpLookup.put (Card.ID_BLUE_6, R.string.cardhelp_6);
        m_cardLookup.put (Card.ID_BLUE_6, new Card(-1, Card.COLOR_BLUE, 6, Card.ID_BLUE_6, 6));
		
		m_imageIDLookup.put (Card.ID_BLUE_7, R.drawable.card_blue_7);
		m_imageLookup.put (Card.ID_BLUE_7, BitmapFactory.decodeResource(res, R.drawable.card_blue_7, opt));
		m_cardHelpLookup.put (Card.ID_BLUE_7, R.string.cardhelp_7);
        m_cardLookup.put (Card.ID_BLUE_7, new Card(-1, Card.COLOR_BLUE, 7, Card.ID_BLUE_7, 7));
		
		m_imageIDLookup.put (Card.ID_BLUE_8, R.drawable.card_blue_8);
		m_imageLookup.put (Card.ID_BLUE_8, BitmapFactory.decodeResource(res, R.drawable.card_blue_8, opt));
		m_cardHelpLookup.put (Card.ID_BLUE_8, R.string.cardhelp_8);
        m_cardLookup.put (Card.ID_BLUE_8, new Card(-1, Card.COLOR_BLUE, 8, Card.ID_BLUE_8, 8));
		
		m_imageIDLookup.put (Card.ID_BLUE_9, R.drawable.card_blue_9);
		m_imageLookup.put (Card.ID_BLUE_9, BitmapFactory.decodeResource(res, R.drawable.card_blue_9, opt));
		m_cardHelpLookup.put (Card.ID_BLUE_9, R.string.cardhelp_9);
        m_cardLookup.put (Card.ID_BLUE_9, new Card(-1, Card.COLOR_BLUE, 9, Card.ID_BLUE_9, 9));
		
		m_imageIDLookup.put (Card.ID_BLUE_D, R.drawable.card_blue_d);
		m_imageLookup.put (Card.ID_BLUE_D, BitmapFactory.decodeResource(res, R.drawable.card_blue_d, opt));
		m_cardHelpLookup.put (Card.ID_BLUE_D, R.string.cardhelp_d);
        m_cardLookup.put (Card.ID_BLUE_D, new Card(-1, Card.COLOR_BLUE, Card.VAL_D, Card.ID_BLUE_D, 20));

		m_imageIDLookup.put (Card.ID_BLUE_S, R.drawable.card_blue_s);
		m_imageLookup.put (Card.ID_BLUE_S, BitmapFactory.decodeResource(res, R.drawable.card_blue_s, opt));
		m_cardHelpLookup.put (Card.ID_BLUE_S, R.string.cardhelp_s);
        m_cardLookup.put (Card.ID_BLUE_S, new Card(-1, Card.COLOR_BLUE, Card.VAL_S, Card.ID_BLUE_S, 20));

		m_imageIDLookup.put (Card.ID_BLUE_R, R.drawable.card_blue_r);
		m_imageLookup.put (Card.ID_BLUE_R, BitmapFactory.decodeResource(res, R.drawable.card_blue_r, opt));
		m_cardHelpLookup.put (Card.ID_BLUE_R, R.string.cardhelp_r);
        m_cardLookup.put (Card.ID_BLUE_R, new Card(-1, Card.COLOR_BLUE, Card.VAL_R, Card.ID_BLUE_R, 20));
				
		m_imageIDLookup.put (Card.ID_YELLOW_0, R.drawable.card_yellow_0);
		m_imageLookup.put (Card.ID_YELLOW_0, BitmapFactory.decodeResource(res, R.drawable.card_yellow_0, opt));
		m_cardHelpLookup.put (Card.ID_YELLOW_0, R.string.cardhelp_0);
        m_cardLookup.put (Card.ID_YELLOW_0, new Card(-1, Card.COLOR_YELLOW, 0, Card.ID_YELLOW_0, 0));

		m_imageIDLookup.put (Card.ID_YELLOW_1, R.drawable.card_yellow_1);
		m_imageLookup.put (Card.ID_YELLOW_1, BitmapFactory.decodeResource(res, R.drawable.card_yellow_1, opt));
		m_cardHelpLookup.put (Card.ID_YELLOW_1, R.string.cardhelp_1);
        m_cardLookup.put (Card.ID_YELLOW_1, new Card(-1, Card.COLOR_YELLOW, 1, Card.ID_YELLOW_1, 1));
		
		m_imageIDLookup.put (Card.ID_YELLOW_2, R.drawable.card_yellow_2);
		m_imageLookup.put (Card.ID_YELLOW_2, BitmapFactory.decodeResource(res, R.drawable.card_yellow_2, opt));
		m_cardHelpLookup.put (Card.ID_YELLOW_2, R.string.cardhelp_2);
        m_cardLookup.put (Card.ID_YELLOW_2, new Card(-1, Card.COLOR_YELLOW, 2, Card.ID_YELLOW_2, 2));

		m_imageIDLookup.put (Card.ID_YELLOW_3, R.drawable.card_yellow_3);
		m_imageLookup.put (Card.ID_YELLOW_3, BitmapFactory.decodeResource(res, R.drawable.card_yellow_3, opt));
		m_cardHelpLookup.put (Card.ID_YELLOW_3, R.string.cardhelp_3);
        m_cardLookup.put (Card.ID_YELLOW_3, new Card(-1, Card.COLOR_YELLOW, 3, Card.ID_YELLOW_3, 3));
		
		m_imageIDLookup.put (Card.ID_YELLOW_4, R.drawable.card_yellow_4);
		m_imageLookup.put (Card.ID_YELLOW_4, BitmapFactory.decodeResource(res, R.drawable.card_yellow_4, opt));
		m_cardHelpLookup.put (Card.ID_YELLOW_4, R.string.cardhelp_4);
        m_cardLookup.put (Card.ID_YELLOW_4, new Card(-1, Card.COLOR_YELLOW, 4, Card.ID_YELLOW_4, 4));
		
		m_imageIDLookup.put (Card.ID_YELLOW_5, R.drawable.card_yellow_5);
		m_imageLookup.put (Card.ID_YELLOW_5, BitmapFactory.decodeResource(res, R.drawable.card_yellow_5, opt));
		m_cardHelpLookup.put (Card.ID_YELLOW_5, R.string.cardhelp_5);
        m_cardLookup.put (Card.ID_YELLOW_5, new Card(-1, Card.COLOR_YELLOW, 5, Card.ID_YELLOW_5, 5));
		
		m_imageIDLookup.put (Card.ID_YELLOW_6, R.drawable.card_yellow_6);
		m_imageLookup.put (Card.ID_YELLOW_6, BitmapFactory.decodeResource(res, R.drawable.card_yellow_6, opt));
		m_cardHelpLookup.put (Card.ID_YELLOW_6, R.string.cardhelp_6);
        m_cardLookup.put (Card.ID_YELLOW_6, new Card(-1, Card.COLOR_YELLOW, 6, Card.ID_YELLOW_6, 6));
		
		m_imageIDLookup.put (Card.ID_YELLOW_7, R.drawable.card_yellow_7);
		m_imageLookup.put (Card.ID_YELLOW_7, BitmapFactory.decodeResource(res, R.drawable.card_yellow_7, opt));
		m_cardHelpLookup.put (Card.ID_YELLOW_7, R.string.cardhelp_7);
        m_cardLookup.put (Card.ID_YELLOW_7, new Card(-1, Card.COLOR_YELLOW, 7, Card.ID_YELLOW_7, 7));

		m_imageIDLookup.put (Card.ID_YELLOW_8, R.drawable.card_yellow_8);
		m_imageLookup.put (Card.ID_YELLOW_8, BitmapFactory.decodeResource(res, R.drawable.card_yellow_8, opt));
		m_cardHelpLookup.put (Card.ID_YELLOW_8, R.string.cardhelp_8);
        m_cardLookup.put (Card.ID_YELLOW_8, new Card(-1, Card.COLOR_YELLOW, 8, Card.ID_YELLOW_8, 8));
		
		m_imageIDLookup.put (Card.ID_YELLOW_9, R.drawable.card_yellow_9);
		m_imageLookup.put (Card.ID_YELLOW_9, BitmapFactory.decodeResource(res, R.drawable.card_yellow_9, opt));
		m_cardHelpLookup.put (Card.ID_YELLOW_9, R.string.cardhelp_9);
        m_cardLookup.put (Card.ID_YELLOW_9, new Card(-1, Card.COLOR_YELLOW, 9, Card.ID_YELLOW_9, 9));
		
		m_imageIDLookup.put (Card.ID_YELLOW_D, R.drawable.card_yellow_d);
		m_imageLookup.put (Card.ID_YELLOW_D, BitmapFactory.decodeResource(res, R.drawable.card_yellow_d, opt));
		m_cardHelpLookup.put (Card.ID_YELLOW_D, R.string.cardhelp_d);
        m_cardLookup.put (Card.ID_YELLOW_D, new Card(-1, Card.COLOR_YELLOW, Card.VAL_D, Card.ID_YELLOW_D, 20));

		m_imageIDLookup.put (Card.ID_YELLOW_S, R.drawable.card_yellow_s);
		m_imageLookup.put (Card.ID_YELLOW_S, BitmapFactory.decodeResource(res, R.drawable.card_yellow_s, opt));
		m_cardHelpLookup.put (Card.ID_YELLOW_S, R.string.cardhelp_s);
        m_cardLookup.put (Card.ID_YELLOW_S, new Card(-1, Card.COLOR_YELLOW, Card.VAL_S, Card.ID_YELLOW_S, 20));

		m_imageIDLookup.put (Card.ID_YELLOW_R, R.drawable.card_yellow_r);
		m_imageLookup.put (Card.ID_YELLOW_R, BitmapFactory.decodeResource(res, R.drawable.card_yellow_r, opt));
		m_cardHelpLookup.put (Card.ID_YELLOW_R, R.string.cardhelp_r);
        m_cardLookup.put (Card.ID_YELLOW_R, new Card(-1, Card.COLOR_YELLOW, Card.VAL_R, Card.ID_YELLOW_R, 20));
		
		
		m_imageIDLookup.put (Card.ID_WILD, R.drawable.card_wild);
		m_imageLookup.put (Card.ID_WILD, BitmapFactory.decodeResource(res, R.drawable.card_wild, opt));
		m_cardHelpLookup.put (Card.ID_WILD, R.string.cardhelp_wild);
        m_cardLookup.put (Card.ID_WILD, new Card(-1, Card.COLOR_WILD, Card.VAL_WILD, Card.ID_WILD, 50));
		
		m_imageIDLookup.put (Card.ID_WILD_DRAW_FOUR, R.drawable.card_wild_drawfour);
		m_imageLookup.put (Card.ID_WILD_DRAW_FOUR, BitmapFactory.decodeResource(res, R.drawable.card_wild_drawfour, opt));
		m_cardHelpLookup.put (Card.ID_WILD_DRAW_FOUR, R.string.cardhelp_wild_drawfour);
        m_cardLookup.put (Card.ID_WILD_DRAW_FOUR, new Card(-1, Card.COLOR_WILD, Card.VAL_WILD_DRAW, Card.ID_WILD_DRAW_FOUR, 50));
		
		m_imageIDLookup.put (Card.ID_WILD_HOS, R.drawable.card_wild_hos);
		m_imageLookup.put (Card.ID_WILD_HOS, BitmapFactory.decodeResource(res, R.drawable.card_wild_hos, opt));
		m_cardHelpLookup.put (Card.ID_WILD_HOS, R.string.cardhelp_wild_hos);
        m_cardLookup.put (Card.ID_WILD_HOS, new Card(-1, Card.COLOR_WILD, Card.VAL_WILD_DRAW, Card.ID_WILD_HOS, 0));
		
		m_imageIDLookup.put (Card.ID_WILD_HD, R.drawable.card_wild_hd);
		m_imageLookup.put (Card.ID_WILD_HD, BitmapFactory.decodeResource(res, R.drawable.card_wild_hd, opt));
		m_cardHelpLookup.put (Card.ID_WILD_HD, R.string.cardhelp_wild_hd);
        m_cardLookup.put (Card.ID_WILD_HD, new Card(-1, Card.COLOR_WILD, Card.VAL_WILD_DRAW, Card.ID_WILD_HD, 100));
		
		m_imageIDLookup.put (Card.ID_WILD_MYSTERY, R.drawable.card_wild_mystery);
		m_imageLookup.put (Card.ID_WILD_MYSTERY, BitmapFactory.decodeResource(res, R.drawable.card_wild_mystery, opt));
		m_cardHelpLookup.put (Card.ID_WILD_MYSTERY, R.string.cardhelp_wild_mystery);
        m_cardLookup.put (Card.ID_WILD_MYSTERY, new Card(-1, Card.COLOR_WILD, Card.VAL_WILD_DRAW, Card.ID_WILD_MYSTERY, 0));
		
		m_imageIDLookup.put (Card.ID_WILD_DB, R.drawable.card_wild_db);
		m_imageLookup.put (Card.ID_WILD_DB, BitmapFactory.decodeResource(res, R.drawable.card_wild_db, opt));
		m_cardHelpLookup.put (Card.ID_WILD_DB, R.string.cardhelp_wild_db);
        m_cardLookup.put (Card.ID_WILD_DB, new Card(-1, Card.COLOR_WILD, Card.VAL_WILD_DRAW, Card.ID_WILD_DB, 100));
		
		m_imageIDLookup.put (Card.ID_RED_0_HD, R.drawable.card_red_0_hd);
		m_imageLookup.put (Card.ID_RED_0_HD, BitmapFactory.decodeResource(res, R.drawable.card_red_0_hd, opt));
        if (m_go.getFamilyFriendly())
		{
			m_cardHelpLookup.put (Card.ID_RED_0_HD, R.string.cardhelp_red_0_hd_ff);
		}
		else
		{
			m_cardHelpLookup.put (Card.ID_RED_0_HD, R.string.cardhelp_red_0_hd);
		}
        m_cardLookup.put (Card.ID_RED_0_HD, new Card(-1, Card.COLOR_RED, 0, Card.ID_RED_0_HD, 0));

		m_imageIDLookup.put (Card.ID_RED_2_GLASNOST, R.drawable.card_red_2_glasnost);
		m_imageLookup.put (Card.ID_RED_2_GLASNOST, BitmapFactory.decodeResource(res, R.drawable.card_red_2_glasnost, opt));
		m_cardHelpLookup.put (Card.ID_RED_2_GLASNOST, R.string.cardhelp_red_2_glasnost);
        m_cardLookup.put (Card.ID_RED_2_GLASNOST, new Card(-1, Card.COLOR_RED, 2, Card.ID_RED_2_GLASNOST, 75));
		
		m_imageIDLookup.put (Card.ID_RED_5_MAGIC, R.drawable.card_red_5_magic);
		m_imageLookup.put (Card.ID_RED_5_MAGIC, BitmapFactory.decodeResource(res, R.drawable.card_red_5_magic, opt));
		m_cardHelpLookup.put (Card.ID_RED_5_MAGIC, R.string.cardhelp_red_5_magic);
        m_cardLookup.put (Card.ID_RED_5_MAGIC, new Card(-1, Card.COLOR_RED, 5, Card.ID_RED_5_MAGIC, -5));
		
		m_imageIDLookup.put (Card.ID_RED_D_SPREADER, R.drawable.card_red_d_spreader);
		m_imageLookup.put (Card.ID_RED_D_SPREADER, BitmapFactory.decodeResource(res, R.drawable.card_red_d_spreader, opt));
		m_cardHelpLookup.put (Card.ID_RED_D_SPREADER, R.string.cardhelp_d_spread);
        m_cardLookup.put (Card.ID_RED_D_SPREADER, new Card(-1, Card.COLOR_RED, Card.VAL_D_SPREAD, Card.ID_RED_D_SPREADER, 60));

		m_imageIDLookup.put (Card.ID_RED_S_DOUBLE, R.drawable.card_red_s_double);
		m_imageLookup.put (Card.ID_RED_S_DOUBLE, BitmapFactory.decodeResource(res, R.drawable.card_red_s_double, opt));
		m_cardHelpLookup.put (Card.ID_RED_S_DOUBLE, R.string.cardhelp_s_double);
        m_cardLookup.put (Card.ID_RED_S_DOUBLE, new Card(-1, Card.COLOR_RED, Card.VAL_S_DOUBLE, Card.ID_RED_S_DOUBLE, 40));
		
		m_imageIDLookup.put (Card.ID_RED_R_SKIP, R.drawable.card_red_r_skip);
		m_imageLookup.put (Card.ID_RED_R_SKIP, BitmapFactory.decodeResource(res, R.drawable.card_red_r_skip, opt));
		m_cardHelpLookup.put (Card.ID_RED_R_SKIP, R.string.cardhelp_r_skip);
        m_cardLookup.put (Card.ID_RED_R_SKIP, new Card(-1, Card.COLOR_RED, Card.VAL_R_SKIP, Card.ID_RED_R_SKIP, 40));
		
		m_imageIDLookup.put (Card.ID_GREEN_0_QUITTER, R.drawable.card_green_0_quitter);
		m_imageLookup.put (Card.ID_GREEN_0_QUITTER, BitmapFactory.decodeResource(res, R.drawable.card_green_0_quitter, opt));
		if (m_go.getFamilyFriendly())
		{
			m_cardHelpLookup.put (Card.ID_GREEN_0_QUITTER, R.string.cardhelp_green_0_quitter_ff);
		}
		else
		{
			m_cardHelpLookup.put (Card.ID_GREEN_0_QUITTER, R.string.cardhelp_green_0_quitter);			
		}
        m_cardLookup.put (Card.ID_GREEN_0_QUITTER, new Card(-1, Card.COLOR_GREEN, 0, Card.ID_GREEN_0_QUITTER, 100));
		
        if (m_go.getFamilyFriendly())
		{
			m_imageIDLookup.put (Card.ID_GREEN_3_AIDS, R.drawable.card_green_3_aids_ff);
			m_imageLookup.put (Card.ID_GREEN_3_AIDS, BitmapFactory.decodeResource(res, R.drawable.card_green_3_aids_ff, opt));
			m_cardHelpLookup.put (Card.ID_GREEN_3_AIDS, R.string.cardhelp_green_3_aids_ff);
		}
		else
		{
			m_imageIDLookup.put (Card.ID_GREEN_3_AIDS, R.drawable.card_green_3_aids);
			m_imageLookup.put (Card.ID_GREEN_3_AIDS, BitmapFactory.decodeResource(res, R.drawable.card_green_3_aids, opt));
			m_cardHelpLookup.put (Card.ID_GREEN_3_AIDS, R.string.cardhelp_green_3_aids);
		}
        m_cardLookup.put (Card.ID_GREEN_3_AIDS, new Card(-1, Card.COLOR_GREEN, 3, Card.ID_GREEN_3_AIDS, 3));
		
		m_imageIDLookup.put (Card.ID_GREEN_4_IRISH, R.drawable.card_green_4_irish);
		m_imageLookup.put (Card.ID_GREEN_4_IRISH, BitmapFactory.decodeResource(res, R.drawable.card_green_4_irish, opt));
		m_cardHelpLookup.put (Card.ID_GREEN_4_IRISH, R.string.cardhelp_green_4_irish);
        m_cardLookup.put (Card.ID_GREEN_4_IRISH, new Card(-1, Card.COLOR_GREEN, 4, Card.ID_GREEN_4_IRISH, 75));
		
		m_imageIDLookup.put (Card.ID_GREEN_D_SPREADER, R.drawable.card_green_d_spreader);
		m_imageLookup.put (Card.ID_GREEN_D_SPREADER, BitmapFactory.decodeResource(res, R.drawable.card_green_d_spreader, opt));
		m_cardHelpLookup.put (Card.ID_GREEN_D_SPREADER, R.string.cardhelp_d_spread);
        m_cardLookup.put (Card.ID_GREEN_D_SPREADER, new Card(-1, Card.COLOR_GREEN, Card.VAL_D_SPREAD, Card.ID_GREEN_D_SPREADER, 60));
		
		m_imageIDLookup.put (Card.ID_GREEN_S_DOUBLE, R.drawable.card_green_s_double);
		m_imageLookup.put (Card.ID_GREEN_S_DOUBLE, BitmapFactory.decodeResource(res, R.drawable.card_green_s_double, opt));
		m_cardHelpLookup.put (Card.ID_GREEN_S_DOUBLE, R.string.cardhelp_s_double);
        m_cardLookup.put (Card.ID_GREEN_S_DOUBLE, new Card(-1, Card.COLOR_GREEN, Card.VAL_S_DOUBLE, Card.ID_GREEN_S_DOUBLE, 40));
		
		m_imageIDLookup.put (Card.ID_GREEN_R_SKIP, R.drawable.card_green_r_skip);
		m_imageLookup.put (Card.ID_GREEN_R_SKIP, BitmapFactory.decodeResource(res, R.drawable.card_green_r_skip, opt));
		m_cardHelpLookup.put (Card.ID_GREEN_R_SKIP, R.string.cardhelp_r_skip);
        m_cardLookup.put (Card.ID_GREEN_R_SKIP, new Card(-1, Card.COLOR_GREEN, Card.VAL_R_SKIP, Card.ID_GREEN_R_SKIP, 40));		
		
        if (m_go.getFamilyFriendly())
		{
			m_imageIDLookup.put (Card.ID_BLUE_0_FUCK_YOU, R.drawable.card_blue_0_fuckyou_ff);
			m_imageLookup.put (Card.ID_BLUE_0_FUCK_YOU, BitmapFactory.decodeResource(res, R.drawable.card_blue_0_fuckyou_ff, opt));
			m_cardHelpLookup.put (Card.ID_BLUE_0_FUCK_YOU, R.string.cardhelp_blue_0_fuck_you_ff);
		}
		else
		{
			m_imageIDLookup.put (Card.ID_BLUE_0_FUCK_YOU, R.drawable.card_blue_0_fuckyou);
			m_imageLookup.put (Card.ID_BLUE_0_FUCK_YOU, BitmapFactory.decodeResource(res, R.drawable.card_blue_0_fuckyou, opt));
			m_cardHelpLookup.put (Card.ID_BLUE_0_FUCK_YOU, R.string.cardhelp_blue_0_fuck_you);
		}
        m_cardLookup.put (Card.ID_BLUE_0_FUCK_YOU, new Card(-1, Card.COLOR_BLUE, 0, Card.ID_BLUE_0_FUCK_YOU, 0));
		
		m_imageIDLookup.put (Card.ID_BLUE_2_SHIELD, R.drawable.card_blue_2_shield);
		m_imageLookup.put (Card.ID_BLUE_2_SHIELD, BitmapFactory.decodeResource(res, R.drawable.card_blue_2_shield, opt));
		m_cardHelpLookup.put (Card.ID_BLUE_2_SHIELD, R.string.cardhelp_blue_2_shield);
        m_cardLookup.put (Card.ID_BLUE_2_SHIELD, new Card(-1, Card.COLOR_BLUE, 2, Card.ID_BLUE_2_SHIELD, 0));
		
		m_imageIDLookup.put (Card.ID_BLUE_D_SPREADER, R.drawable.card_blue_d_spreader);
		m_imageLookup.put (Card.ID_BLUE_D_SPREADER, BitmapFactory.decodeResource(res, R.drawable.card_blue_d_spreader, opt));
		m_cardHelpLookup.put (Card.ID_BLUE_D_SPREADER, R.string.cardhelp_d_spread);
        m_cardLookup.put (Card.ID_BLUE_D_SPREADER, new Card(-1, Card.COLOR_BLUE, Card.VAL_D_SPREAD, Card.ID_BLUE_D_SPREADER, 60));

		m_imageIDLookup.put (Card.ID_BLUE_S_DOUBLE, R.drawable.card_blue_s_double);
		m_imageLookup.put (Card.ID_BLUE_S_DOUBLE, BitmapFactory.decodeResource(res, R.drawable.card_blue_s_double, opt));
		m_cardHelpLookup.put (Card.ID_BLUE_S_DOUBLE, R.string.cardhelp_s_double);
        m_cardLookup.put (Card.ID_BLUE_S_DOUBLE, new Card(-1, Card.COLOR_BLUE, Card.VAL_S_DOUBLE, Card.ID_BLUE_S_DOUBLE, 40));

		m_imageIDLookup.put (Card.ID_BLUE_R_SKIP, R.drawable.card_blue_r_skip);
		m_imageLookup.put (Card.ID_BLUE_R_SKIP, BitmapFactory.decodeResource(res, R.drawable.card_blue_r_skip, opt));
		m_cardHelpLookup.put (Card.ID_BLUE_R_SKIP, R.string.cardhelp_r_skip);
        m_cardLookup.put (Card.ID_BLUE_R_SKIP, new Card(-1, Card.COLOR_BLUE, Card.VAL_R_SKIP, Card.ID_BLUE_R_SKIP, 40));		
		
        if (m_go.getFamilyFriendly())
		{
			m_imageIDLookup.put (Card.ID_YELLOW_0_SHITTER, R.drawable.card_yellow_0_shitter_ff);
			m_imageLookup.put (Card.ID_YELLOW_0_SHITTER, BitmapFactory.decodeResource(res, R.drawable.card_yellow_0_shitter_ff, opt));
			m_cardHelpLookup.put (Card.ID_YELLOW_0_SHITTER, R.string.cardhelp_yellow_0_shitter_ff);
		}
		else
		{
			m_imageIDLookup.put (Card.ID_YELLOW_0_SHITTER, R.drawable.card_yellow_0_shitter);
			m_imageLookup.put (Card.ID_YELLOW_0_SHITTER, BitmapFactory.decodeResource(res, R.drawable.card_yellow_0_shitter, opt));
			m_cardHelpLookup.put (Card.ID_YELLOW_0_SHITTER, R.string.cardhelp_yellow_0_shitter);
		}
        m_cardLookup.put (Card.ID_YELLOW_0_SHITTER, new Card(-1, Card.COLOR_YELLOW, 0, Card.ID_YELLOW_0_SHITTER, 0));

		m_imageIDLookup.put (Card.ID_YELLOW_1_MAD, R.drawable.card_yellow_1_mad);
		m_imageLookup.put (Card.ID_YELLOW_1_MAD, BitmapFactory.decodeResource(res, R.drawable.card_yellow_1_mad, opt));
		m_cardHelpLookup.put (Card.ID_YELLOW_1_MAD, R.string.cardhelp_yellow_1_mad);
        m_cardLookup.put (Card.ID_YELLOW_1_MAD, new Card(-1, Card.COLOR_YELLOW, 1, Card.ID_YELLOW_1_MAD, 100));

		m_imageIDLookup.put (Card.ID_YELLOW_69, R.drawable.card_yellow_69);
		m_imageLookup.put (Card.ID_YELLOW_69, BitmapFactory.decodeResource(res, R.drawable.card_yellow_69, opt));
		if (m_go.getFamilyFriendly())
		{
			m_cardHelpLookup.put (Card.ID_YELLOW_69, R.string.cardhelp_yellow_69_ff);
		}
		else
		{
			m_cardHelpLookup.put (Card.ID_YELLOW_69, R.string.cardhelp_yellow_69);	
		}
        m_cardLookup.put (Card.ID_YELLOW_69, new Card(-1, Card.COLOR_YELLOW, 6, Card.ID_YELLOW_69, 6));

		m_imageIDLookup.put (Card.ID_YELLOW_D_SPREADER, R.drawable.card_yellow_d_spreader);
		m_imageLookup.put (Card.ID_YELLOW_D_SPREADER, BitmapFactory.decodeResource(res, R.drawable.card_yellow_d_spreader, opt));
		m_cardHelpLookup.put (Card.ID_YELLOW_D_SPREADER, R.string.cardhelp_d_spread);
        m_cardLookup.put (Card.ID_YELLOW_D_SPREADER, new Card(-1, Card.COLOR_YELLOW, Card.VAL_D_SPREAD, Card.ID_YELLOW_D_SPREADER, 60));

		m_imageIDLookup.put (Card.ID_YELLOW_S_DOUBLE, R.drawable.card_yellow_s_double);
		m_imageLookup.put (Card.ID_YELLOW_S_DOUBLE, BitmapFactory.decodeResource(res, R.drawable.card_yellow_s_double, opt));
		m_cardHelpLookup.put (Card.ID_YELLOW_S_DOUBLE, R.string.cardhelp_s_double);
        m_cardLookup.put (Card.ID_YELLOW_S_DOUBLE, new Card(-1, Card.COLOR_YELLOW, Card.VAL_S_DOUBLE, Card.ID_YELLOW_S_DOUBLE, 40));

		m_imageIDLookup.put (Card.ID_YELLOW_R_SKIP, R.drawable.card_yellow_r_skip);
		m_imageLookup.put (Card.ID_YELLOW_R_SKIP, BitmapFactory.decodeResource(res, R.drawable.card_yellow_r_skip, opt));
		m_cardHelpLookup.put (Card.ID_YELLOW_R_SKIP, R.string.cardhelp_r_skip);
        m_cardLookup.put (Card.ID_YELLOW_R_SKIP, new Card(-1, Card.COLOR_YELLOW, Card.VAL_R_SKIP, Card.ID_YELLOW_R_SKIP, 40));		

//		m_bmpDirColorCCW = BitmapFactory.decodeResource(res, R.drawable.ccw, opt);
//		m_bmpDirColorCCWRed = BitmapFactory.decodeResource(res, R.drawable.ccw_red, opt);
//		m_bmpDirColorCCWBlue = BitmapFactory.decodeResource(res, R.drawable.ccw_blue, opt);
//		m_bmpDirColorCCWGreen = BitmapFactory.decodeResource(res, R.drawable.ccw_green, opt);
//		m_bmpDirColorCCWYellow = BitmapFactory.decodeResource(res, R.drawable.ccw_yellow, opt);
//
//		m_bmpDirColorCW = BitmapFactory.decodeResource(res, R.drawable.cw, opt);
//		m_bmpDirColorCWRed = BitmapFactory.decodeResource(res, R.drawable.cw_red, opt);
//		m_bmpDirColorCWBlue = BitmapFactory.decodeResource(res, R.drawable.cw_blue, opt);
//		m_bmpDirColorCWGreen = BitmapFactory.decodeResource(res, R.drawable.cw_green, opt);
//		m_bmpDirColorCWYellow = BitmapFactory.decodeResource(res, R.drawable.cw_yellow, opt);
//
//		m_bmpPlayerIndicator[Card.COLOR_RED - 1][Game.SEAT_SOUTH - 1] = BitmapFactory.decodeResource(res, R.drawable.player_red_south, opt);
//		m_bmpPlayerIndicator[Card.COLOR_GREEN - 1][Game.SEAT_SOUTH - 1] = BitmapFactory.decodeResource(res, R.drawable.player_green_south, opt);
//		m_bmpPlayerIndicator[Card.COLOR_BLUE - 1][Game.SEAT_SOUTH - 1] = BitmapFactory.decodeResource(res, R.drawable.player_blue_south, opt);
//		m_bmpPlayerIndicator[Card.COLOR_YELLOW - 1][Game.SEAT_SOUTH - 1] = BitmapFactory.decodeResource(res, R.drawable.player_yellow_south, opt);
//		m_bmpPlayerIndicator[Card.COLOR_WILD - 1][Game.SEAT_SOUTH - 1] = BitmapFactory.decodeResource(res, R.drawable.player_south, opt);
//
//		m_bmpPlayerIndicator[Card.COLOR_RED - 1][Game.SEAT_WEST - 1] = BitmapFactory.decodeResource(res, R.drawable.player_red_west, opt);
//		m_bmpPlayerIndicator[Card.COLOR_GREEN - 1][Game.SEAT_WEST - 1] = BitmapFactory.decodeResource(res, R.drawable.player_green_west, opt);
//		m_bmpPlayerIndicator[Card.COLOR_BLUE - 1][Game.SEAT_WEST - 1] = BitmapFactory.decodeResource(res, R.drawable.player_blue_west, opt);
//		m_bmpPlayerIndicator[Card.COLOR_YELLOW - 1][Game.SEAT_WEST - 1] = BitmapFactory.decodeResource(res, R.drawable.player_yellow_west, opt);
//		m_bmpPlayerIndicator[Card.COLOR_WILD - 1][Game.SEAT_WEST - 1] = BitmapFactory.decodeResource(res, R.drawable.player_west, opt);
//
//		m_bmpPlayerIndicator[Card.COLOR_RED - 1][Game.SEAT_NORTH - 1] = BitmapFactory.decodeResource(res, R.drawable.player_red_north, opt);
//		m_bmpPlayerIndicator[Card.COLOR_GREEN - 1][Game.SEAT_NORTH - 1] = BitmapFactory.decodeResource(res, R.drawable.player_green_north, opt);
//		m_bmpPlayerIndicator[Card.COLOR_BLUE - 1][Game.SEAT_NORTH - 1] = BitmapFactory.decodeResource(res, R.drawable.player_blue_north, opt);
//		m_bmpPlayerIndicator[Card.COLOR_YELLOW - 1][Game.SEAT_NORTH - 1] = BitmapFactory.decodeResource(res, R.drawable.player_yellow_north, opt);
//		m_bmpPlayerIndicator[Card.COLOR_WILD - 1][Game.SEAT_NORTH - 1] = BitmapFactory.decodeResource(res, R.drawable.player_north, opt);
//
//		m_bmpPlayerIndicator[Card.COLOR_RED - 1][Game.SEAT_EAST - 1] = BitmapFactory.decodeResource(res, R.drawable.player_red_east, opt);
//		m_bmpPlayerIndicator[Card.COLOR_GREEN - 1][Game.SEAT_EAST - 1] = BitmapFactory.decodeResource(res, R.drawable.player_green_east, opt);
//		m_bmpPlayerIndicator[Card.COLOR_BLUE - 1][Game.SEAT_EAST - 1] = BitmapFactory.decodeResource(res, R.drawable.player_blue_east, opt);
//		m_bmpPlayerIndicator[Card.COLOR_YELLOW - 1][Game.SEAT_EAST - 1] = BitmapFactory.decodeResource(res, R.drawable.player_yellow_east, opt);
//		m_bmpPlayerIndicator[Card.COLOR_WILD - 1][Game.SEAT_EAST - 1] = BitmapFactory.decodeResource(res, R.drawable.player_east, opt);
		
		m_bmpWinningMessage[Game.SEAT_SOUTH - 1] = BitmapFactory.decodeResource(res, R.drawable.winner_south, opt);
		m_bmpWinningMessage[Game.SEAT_WEST - 1] = BitmapFactory.decodeResource(res, R.drawable.winner_west, opt);
		m_bmpWinningMessage[Game.SEAT_NORTH - 1] = BitmapFactory.decodeResource(res, R.drawable.winner_north, opt);
		m_bmpWinningMessage[Game.SEAT_EAST - 1] = BitmapFactory.decodeResource(res, R.drawable.winner_east, opt);
		

		m_bmpCardBadge = BitmapFactory.decodeResource(res, R.drawable.card_badge, opt);
		
		m_bmpEmoticonAggressor = BitmapFactory.decodeResource(res, R.drawable.emoticon_aggressor, opt);
		m_bmpEmoticonVictim = BitmapFactory.decodeResource(res, R.drawable.emoticon_victim, opt);
		
		int i = 0;
		
	    m_cardIDs[i++] = Card.ID_RED_0;
	    m_cardIDs[i++] = Card.ID_RED_0_HD;		
	    m_cardIDs[i++] = Card.ID_RED_1;
	    m_cardIDs[i++] = Card.ID_RED_2;
	    m_cardIDs[i++] = Card.ID_RED_2_GLASNOST;		
	    m_cardIDs[i++] = Card.ID_RED_3;
	    m_cardIDs[i++] = Card.ID_RED_4;
	    m_cardIDs[i++] = Card.ID_RED_5;
	    m_cardIDs[i++] = Card.ID_RED_5_MAGIC;		
	    m_cardIDs[i++] = Card.ID_RED_6;
	    m_cardIDs[i++] = Card.ID_RED_7;
	    m_cardIDs[i++] = Card.ID_RED_8;
	    m_cardIDs[i++] = Card.ID_RED_9;
	    m_cardIDs[i++] = Card.ID_RED_D;
	    m_cardIDs[i++] = Card.ID_RED_D_SPREADER;		
	    m_cardIDs[i++] = Card.ID_RED_S;
	    m_cardIDs[i++] = Card.ID_RED_S_DOUBLE;		
	    m_cardIDs[i++] = Card.ID_RED_R;		
	    m_cardIDs[i++] = Card.ID_RED_R_SKIP;
	    	    
	    m_cardIDs[i++] = Card.ID_GREEN_0;
	    m_cardIDs[i++] = Card.ID_GREEN_0_QUITTER;		
	    m_cardIDs[i++] = Card.ID_GREEN_1;
	    m_cardIDs[i++] = Card.ID_GREEN_2;
	    m_cardIDs[i++] = Card.ID_GREEN_3;
	    m_cardIDs[i++] = Card.ID_GREEN_3_AIDS;		
	    m_cardIDs[i++] = Card.ID_GREEN_4;
	    m_cardIDs[i++] = Card.ID_GREEN_4_IRISH;
	    m_cardIDs[i++] = Card.ID_GREEN_5;
	    m_cardIDs[i++] = Card.ID_GREEN_6;
	    m_cardIDs[i++] = Card.ID_GREEN_7;
	    m_cardIDs[i++] = Card.ID_GREEN_8;
	    m_cardIDs[i++] = Card.ID_GREEN_9;
	    m_cardIDs[i++] = Card.ID_GREEN_D;
	    m_cardIDs[i++] = Card.ID_GREEN_D_SPREADER;
	    m_cardIDs[i++] = Card.ID_GREEN_S;
	    m_cardIDs[i++] = Card.ID_GREEN_S_DOUBLE;
	    m_cardIDs[i++] = Card.ID_GREEN_R;
	    m_cardIDs[i++] = Card.ID_GREEN_R_SKIP;
	    	    
	    m_cardIDs[i++] = Card.ID_BLUE_0;
	    m_cardIDs[i++] = Card.ID_BLUE_0_FUCK_YOU;
	    m_cardIDs[i++] = Card.ID_BLUE_1;
	    m_cardIDs[i++] = Card.ID_BLUE_2;
	    m_cardIDs[i++] = Card.ID_BLUE_2_SHIELD;
	    m_cardIDs[i++] = Card.ID_BLUE_3;
	    m_cardIDs[i++] = Card.ID_BLUE_4;
	    m_cardIDs[i++] = Card.ID_BLUE_5;
	    m_cardIDs[i++] = Card.ID_BLUE_6;
	    m_cardIDs[i++] = Card.ID_BLUE_7;
	    m_cardIDs[i++] = Card.ID_BLUE_8;
	    m_cardIDs[i++] = Card.ID_BLUE_9;
	    m_cardIDs[i++] = Card.ID_BLUE_D;
	    m_cardIDs[i++] = Card.ID_BLUE_D_SPREADER;
	    m_cardIDs[i++] = Card.ID_BLUE_S;
	    m_cardIDs[i++] = Card.ID_BLUE_S_DOUBLE;
	    m_cardIDs[i++] = Card.ID_BLUE_R;
	    m_cardIDs[i++] = Card.ID_BLUE_R_SKIP;		

	    m_cardIDs[i++] = Card.ID_YELLOW_0;
	    m_cardIDs[i++] = Card.ID_YELLOW_0_SHITTER;		
	    m_cardIDs[i++] = Card.ID_YELLOW_1;
	    m_cardIDs[i++] = Card.ID_YELLOW_1_MAD;
	    m_cardIDs[i++] = Card.ID_YELLOW_2;
	    m_cardIDs[i++] = Card.ID_YELLOW_3;
	    m_cardIDs[i++] = Card.ID_YELLOW_4;
	    m_cardIDs[i++] = Card.ID_YELLOW_5;
	    m_cardIDs[i++] = Card.ID_YELLOW_6;
	    m_cardIDs[i++] = Card.ID_YELLOW_69;
	    m_cardIDs[i++] = Card.ID_YELLOW_7;
	    m_cardIDs[i++] = Card.ID_YELLOW_8;
	    m_cardIDs[i++] = Card.ID_YELLOW_9;
	    m_cardIDs[i++] = Card.ID_YELLOW_D;
	    m_cardIDs[i++] = Card.ID_YELLOW_D_SPREADER;
	    m_cardIDs[i++] = Card.ID_YELLOW_S;
	    m_cardIDs[i++] = Card.ID_YELLOW_S_DOUBLE;
	    m_cardIDs[i++] = Card.ID_YELLOW_R;	
	    m_cardIDs[i++] = Card.ID_YELLOW_R_SKIP;
	    	    
	    m_cardIDs[i++] = Card.ID_WILD;
	    m_cardIDs[i++] = Card.ID_WILD_DRAW_FOUR;
	    m_cardIDs[i++] = Card.ID_WILD_HOS;
	    m_cardIDs[i++] = Card.ID_WILD_HD;
	    m_cardIDs[i++] = Card.ID_WILD_MYSTERY;
	    m_cardIDs[i] = Card.ID_WILD_DB;
	}
	
	private void drawCard (Canvas cv, Card c)
	{
		drawCard(cv, c, (int) c.getX(), (int) c.getY(), c.getFlip());
	}

	private void drawCard (Canvas cv, Card c, int x, int y)
	{
		drawCard(cv, c, x, y, c.getFlip());
	}

	private void drawCard (Canvas cv, Card c, int x, int y, float flip)
	{
		Camera camera = new Camera();
		m_drawMatrix.reset();
		camera.save();
		camera.rotateY(flip);
		camera.getMatrix(m_drawMatrix);
		camera.restore();
		m_drawMatrix.preTranslate( - m_cardWidth / 2, - m_cardHeight / 2);
		m_drawMatrix.postTranslate(x + m_cardWidth / 2, y + m_cardHeight / 2);

		Bitmap b;
		if (c.isFaceUp())
		{
			b = m_imageLookup.get(c.getID());
		}
		else 
		{
			b = m_bmpCardBack;
			/*
			 * show some cards upside down -- this doesn't look as good as I thought it would
			 */
			/*
			Random rgen = new Random();
			int orientation = rgen.nextInt(100);
			if (orientation < 25)
			{
				m_drawMatrix.postRotate(180, x + b.getWidth() / 2, y + b.getHeight() / 2); 
			}
			*/
		}

		
		cv.drawBitmap(b, m_drawMatrix, null);
	}
	
	
	private void drawPenalty(Canvas cv)
	{
	    // draw penalty!
	    Penalty p = m_game.getPenalty();
	    
	    if (p == null || p.getType() == Penalty.PENTYPE_NONE)
	    {
	    	return;
	    }

        if (p.getType() == Penalty.PENTYPE_CARD)
        {
			m_drawMatrix.reset();
			m_drawMatrix.setScale(1, 1);
			m_drawMatrix.setTranslate(m_ptDiscardBadge.x, m_ptDiscardBadge.y);

			cv.drawBitmap(m_bmpCardBadge, m_drawMatrix, null);

			float fx = (float)(m_ptDiscardBadge.x + m_bmpCardBadge.getWidth() / 2);
			Rect textBounds = new Rect();
			String numCards = "+" + p.getNumCards();

			m_paintCardBadgeText.getTextBounds(numCards, 0, numCards.length(), textBounds);
			float fy = (float)(m_ptDiscardBadge.y + m_bmpCardBadge.getHeight() / 2 + (textBounds.height() / 2));

			cv.drawText(numCards, fx, fy, m_paintCardBadgeText);
        }
		else if (p.getOrigCard().getID() != m_game.getLastPlayedCard().getID())
		{
			Bitmap b = m_imageLookup.get(p.getOrigCard().getID());
			float scale = (float) m_bmpCardBadge.getWidth() / (float)b.getWidth();
			m_drawMatrix.reset();
			m_drawMatrix.postScale(scale, scale);
			m_drawMatrix.postTranslate(m_ptDiscardBadge.x, m_ptDiscardBadge.y);
			cv.drawBitmap(b, m_drawMatrix, null);
		}

        Point pt;
        
        Player pv = p.getVictim();
        if (pv != null) 
        {
			pt = m_ptEmoticon[pv.getSeat() - 1];

			int dx = 0;
			int dy = 0;

			// adjust emoticon position for table cards offset
			if (!pv.getHand().getTableCards().isEmpty())
			{
				switch (pv.getSeat()) {
					case Game.SEAT_SOUTH:
						dy -= m_cardHeight * 2 / 3;
						break;
					case Game.SEAT_WEST:
						dx += m_cardWidth / 2;
						break;
					case Game.SEAT_NORTH:
						dy += m_cardHeight / 2;
						break;
					case Game.SEAT_EAST:
						dx -= m_cardWidth / 2;
						break;
				}
			}

			m_drawMatrix.reset();
			m_drawMatrix.setScale(1, 1);
    		m_drawMatrix.setTranslate(pt.x + dx, pt.y + dy);
    		
            cv.drawBitmap(m_bmpEmoticonVictim, m_drawMatrix, null);
        }

        Player pa = p.getGeneratingPlayer();
        if (pa != null) 
        {
			pt = m_ptEmoticon[pa.getSeat() - 1];

			int dx = 0;
			int dy = 0;

			// adjust emoticon position for table cards offset
			if (!pa.getHand().getTableCards().isEmpty())
			{
				switch (pa.getSeat()) {
					case Game.SEAT_SOUTH:
						dy -= m_cardHeight * 2 / 3;
						break;
					case Game.SEAT_WEST:
						dx += m_cardWidth / 2;
						break;
					case Game.SEAT_NORTH:
						dy += m_cardHeight / 2;
						break;
					case Game.SEAT_EAST:
						dx -= m_cardWidth / 2;
						break;
				}
			}

			m_drawMatrix.reset();
			m_drawMatrix.setScale(1, 1);
			m_drawMatrix.setTranslate(pt.x + dx, pt.y + dy);
    		
            cv.drawBitmap(m_bmpEmoticonAggressor, m_drawMatrix, null);
        }



	}

	public static int getColorRgb(int gameColor)
	{
		switch (gameColor)
		{
			case Card.COLOR_RED: return Color.rgb(203, 13, 40);
			case Card.COLOR_GREEN: return Color.rgb(4,133,64);
			case Card.COLOR_BLUE: return Color.rgb(4, 86, 165);
			case Card.COLOR_YELLOW: return Color.rgb(233, 146, 6);
			case Card.COLOR_WILD: return Color.rgb(221,220,215);
		}
		return Color.TRANSPARENT;
	}
	
	public void ShowCardHelp (Card c)
	{
		m_helpCardID = c.getID();

		GameActivity a = (GameActivity)(getContext());
		//a.showDialog(GameActivity.DIALOG_CARD_HELP);
		a.showCardHelp();
	}
	
	public void Toast (String msg)
	{
		// not sure exactly how long it takes to fade out a Toast, but we're going to
		// show the toast for a duration that's a little lower than the game delay
		// to accommodate some fade out time.
		if (m_toast == null)
		{
			m_toast = Toast.makeText(this.getContext(), msg, m_game.getDelay() - 500);
			m_toast.setGravity(Gravity.TOP | Gravity.CENTER, 0, m_ptMessages.y);
		}
		else
		{
			m_toast.setText(msg);
		}
		
		m_toast.show();
	}
	
	public void displayScore (Canvas canvas)
	{
		int i;

		for (i = 0; i < 4; i++)
		{
			if ((i == Game.SEAT_SOUTH - 1) || (i == Game.SEAT_NORTH - 1))
			{
				m_paintScoreText.setTextAlign(Paint.Align.CENTER);
			}
			else if (i == Game.SEAT_WEST - 1)
			{
				m_paintScoreText.setTextAlign(Paint.Align.LEFT);
			}
			else if (i == Game.SEAT_EAST - 1)
			{
				m_paintScoreText.setTextAlign(Paint.Align.RIGHT);
			}

			String msg;
			if (!m_game.getRoundComplete())
			{
				msg = "" + m_game.getPlayer(i).getTotalScore();
			}
			else
			{
				Player p = m_game.getPlayer(i);

				int lastScore = p.getLastScore();
				int virusPenalty = p.getLastVirusPenalty();
				int totalScore = p.getTotalScore();

				if (lastScore < 0) 
				{
					msg = String.format (m_game.getString(R.string.msg_round_score_negative), totalScore - lastScore - virusPenalty, -lastScore, virusPenalty, totalScore);
				}
				else 
				{
					msg = String.format (m_game.getString(R.string.msg_round_score_positive), totalScore - lastScore - virusPenalty, lastScore, virusPenalty, totalScore);
				}
			}
			canvas.drawText(msg, 
				(float)(m_ptScoreText[i].x), (float)(m_ptScoreText[i].y),
				m_paintScoreText);
		}
	}
	
	public void PromptForVictim ()
	{
		int count = 0;
		if (m_game.getPlayer(Game.SEAT_WEST - 1).getActive())
		{
			count++;
		}
		if (m_game.getPlayer(Game.SEAT_NORTH - 1).getActive())
		{
			count++;
		}
		if (m_game.getPlayer(Game.SEAT_EAST - 1).getActive())
		{
			count++;
		}
		
		CharSequence[] items = new CharSequence[count];
		count = 0;
		if (m_game.getPlayer(Game.SEAT_WEST - 1).getActive())
		{
			items[count] = m_game.getString(R.string.seat_west);
			count++;
		}
		if (m_game.getPlayer(Game.SEAT_NORTH - 1).getActive())
		{
			items[count] = m_game.getString(R.string.seat_north);
			count++;
		}
		if (m_game.getPlayer(Game.SEAT_EAST - 1).getActive())
		{
			items[count] = m_game.getString(R.string.seat_east);
		}
		
		new AlertDialog.Builder(this.getContext())
		.setCancelable(false)
		.setTitle(R.string.prompt_victim)
		.setItems(items,
                (dialoginterface, i) -> {
                    Player p = m_game.getCurrPlayer();
                    if (p instanceof HumanPlayer)
                    {
                        if (m_game.getPlayer(Game.SEAT_WEST - 1).getActive())
                        {
                            if (i == 0)
                            {
                                ((HumanPlayer)p).setVictim(Game.SEAT_WEST);
                                return;
                            }
                            i--;
                        }
                        if (m_game.getPlayer(Game.SEAT_NORTH - 1).getActive())
                        {
                            if (i == 0)
                            {
                                ((HumanPlayer)p).setVictim(Game.SEAT_NORTH);
                                return;
                            }
                            i--;
                        }
                        if (m_game.getPlayer(Game.SEAT_EAST - 1).getActive())
                        {
                            if (i == 0)
                            {
                                ((HumanPlayer)p).setVictim(Game.SEAT_EAST);
                                return;
                            }
                            i--;
                        }
                    }
                })
			.show();
	}
	
	public void PromptForNumCardsToDeal ()
	{
		new AlertDialog.Builder(this.getContext())
			.setCancelable(false)
			.setTitle(R.string.prompt_deal)
			.setItems(R.array.deal_values,
                    (dialoginterface, i) -> {
                        Player p = m_game.getDealer();
                        if (p instanceof HumanPlayer)
                        {
                            ((HumanPlayer)p).setNumCardsToDeal(i + 5);
                        }
                    })
				.show();
	}
	
	public void PromptForColor ()
	{
		m_waitingForColor = true;
		startColorChooserAnimation(m_game.getDirection(), true);
//		new AlertDialog.Builder(this.getContext())
//			.setCancelable(false)
//			.setTitle(R.string.prompt_color)
//			.setItems(R.array.colors,
//                    (dialoginterface, i) -> {
//                       Player p = m_game.getCurrPlayer();
//                        if (p instanceof HumanPlayer)
//                        {
//                            ((HumanPlayer)p).setColor((int) (round(random() * 3) + 1));
//                        }
//                    })
//				.show();
//		startColorChooserAnimation(m_game.getDirection(), false);
	}
}
