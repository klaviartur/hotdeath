package com.smorgasbork.hotdeath;

import static java.lang.Math.*;

import android.os.Handler;
import android.os.VibrationEffect;
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
	
	private final int[] m_unrevealedOffset;
	private final int[] m_revealedOffset;
	private final int[] m_unrevealedDrag;
	private final int[] m_revealedDrag;
	
	private int m_maxCardsDisplay = 7;
	
	private final Matrix m_drawMatrix;
	
	private Point m_ptDiscardPile;
	private Point m_ptDrawPile;

	private Point m_ptDiscardBadge;
		
	private final Point[] m_ptSeat;
	private final Point[] m_ptEmoticon;
	private final Point[] m_ptPlayerIndicator;
	private final Point[] m_ptUnrevealedBadge;
	private final Point[] m_ptRevealedBadge;
	private final Point[] m_ptScoreText;
	private Point m_ptDirColor;
	private Point m_ptWinningMessage;	
	private Point m_ptMessages;
	
	private final Rect[] m_unrevealedBoundingRect;
	private final Rect[] m_revealedBoundingRect;
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
	private int m_touchUnrevealedSeat = 0;
	private int m_touchRevealedSeat = 0;
	
	private Integer[] m_cardIDs;
	private HashMap<Integer, Card> m_cardLookup;
	private HashMap<Integer, Integer> m_imageIDLookup;
	private HashMap<Integer, Bitmap> m_imageLookup;
	private HashMap<Integer, Integer> m_cardHelpLookup;
	
	private Bitmap m_bmpCardBack;
	
	private Bitmap m_bmpDirColorCCW, m_bmpDirColorCCWRed, m_bmpDirColorCCWGreen, m_bmpDirColorCCWBlue, m_bmpDirColorCCWYellow;
	private Bitmap m_bmpDirColorCW, m_bmpDirColorCWRed, m_bmpDirColorCWGreen, m_bmpDirColorCWBlue, m_bmpDirColorCWYellow;
	private Bitmap m_bmpEmoticonAggressor, m_bmpEmoticonVictim;
	private final Bitmap[][] m_bmpPlayerIndicator;
	private final Bitmap[] m_bmpWinningMessage;
	private Bitmap m_bmpCardBadge;

    private final Paint m_paintScoreText;
	private final Paint m_paintCardBadgeText;
	
	private boolean m_readyToStartGame = false;
	private boolean m_waitingToStartGame = false;
	
	private final Handler m_handler = new Handler();
	
	private Toast m_toast = null;
	
	private int m_helpCardID = -1;
	
	private Game m_game;
	private GameOptions m_go;
	
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

		this.setBackgroundResource(R.drawable.table_background);
		
		m_drawMatrix = new Matrix();
		
		setFocusable(true);
		setFocusableInTouchMode(true);
		setId(ID);

		m_go = go;
		m_game = g;
		m_game.setGameTable (this);
		
		m_unrevealedOffset = new int[4];
		m_revealedOffset = new int[4];
		m_unrevealedDrag = new int[4];
		m_revealedDrag = new int[4];
		for (int i = 0; i < 4; i++)
		{
			m_unrevealedOffset[i] = 0;
			m_revealedOffset[i] = 0;
			m_unrevealedDrag[i] = 0;
			m_revealedDrag[i] = 0;
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

		m_ptSeat = new Point[4];
		m_ptEmoticon = new Point[4];
		m_ptPlayerIndicator = new Point[4];
		m_ptUnrevealedBadge = new Point[4];
		m_ptRevealedBadge = new Point[4];
		m_ptScoreText = new Point[4];
		
		m_unrevealedBoundingRect = new Rect[4];
		m_revealedBoundingRect = new Rect[4];

		m_bmpPlayerIndicator = new Bitmap[5][4];
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
			m_ptDrawPile = new Point (w / 2 - 5 * m_cardWidth / 4, h / 2 - m_cardHeight / 2);
			m_ptDiscardPile = new Point (w / 2 + m_cardWidth / 4, h / 2 - m_cardHeight / 2);
			m_ptDirColor = new Point (m_ptDiscardPile.x + 2 * m_cardWidth + m_bmpDirColorCCW.getWidth() / 4 - m_bmpPlayerIndicator[0][0].getWidth(), h / 2 - m_bmpDirColorCCW.getWidth() / 2);
		}
		else
		{
			// portrait
			m_ptDrawPile = new Point (w / 2 - 5 * m_cardWidth / 4, h / 2 - m_cardHeight);
			m_ptDiscardPile = new Point (w / 2 + m_cardWidth / 4, h / 2 - m_cardHeight);
			m_ptDirColor = new Point (w /2 - m_bmpDirColorCCW.getWidth() / 2, h / 2 + m_cardHeight / 4);
		}

		m_ptDiscardBadge = new Point (m_ptDiscardPile.x + m_cardWidth - m_bmpCardBadge.getWidth() / 2, m_ptDiscardPile.y + m_cardHeight - m_bmpCardBadge.getHeight() / 2);

		m_ptPlayerIndicator[Game.SEAT_NORTH - 1] = new Point (m_ptDirColor.x + m_bmpDirColorCCW.getWidth() / 2 - m_bmpPlayerIndicator[0][0].getWidth() / 2, m_ptDirColor.y - m_bmpPlayerIndicator[0][0].getHeight());
		m_ptPlayerIndicator[Game.SEAT_EAST - 1] = new Point (m_ptDirColor.x + m_bmpDirColorCCW.getWidth(), m_ptDirColor.y + m_bmpDirColorCCW.getHeight() / 2 -  m_bmpPlayerIndicator[0][0].getHeight() / 2);
		m_ptPlayerIndicator[Game.SEAT_SOUTH - 1] = new Point (m_ptDirColor.x + m_bmpDirColorCCW.getWidth() / 2 - m_bmpPlayerIndicator[0][0].getWidth() / 2, m_ptDirColor.y + m_bmpDirColorCCW.getHeight());
		m_ptPlayerIndicator[Game.SEAT_WEST - 1] = new Point (m_ptDirColor.x - m_bmpPlayerIndicator[0][0].getWidth(), m_ptDirColor.y + m_bmpDirColorCCW.getHeight() / 2 -  m_bmpPlayerIndicator[0][0].getHeight() / 2);

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
		m_ptSeat[Game.SEAT_EAST - 1] = new Point (w - (m_cardWidth + rightMargin), h / 2);
		m_ptSeat[Game.SEAT_SOUTH - 1] = new Point (w / 2, h - (m_cardHeight + bottomMargin));
		m_ptSeat[Game.SEAT_WEST - 1] = new Point (leftMargin, h / 2);
		
		m_ptWinningMessage = new Point (m_ptSeat[Game.SEAT_SOUTH - 1].x - m_bmpWinningMessage[0].getWidth() / 2, m_ptSeat[Game.SEAT_SOUTH - 1].y - m_bmpWinningMessage[0].getHeight() * 5 / 4);
		
		m_ptEmoticon[Game.SEAT_NORTH - 1] = new Point (m_ptSeat[Game.SEAT_NORTH - 1].x - m_emoticonWidth / 2, m_ptSeat[Game.SEAT_NORTH - 1].y + m_cardHeight * 11 / 10);
		m_ptEmoticon[Game.SEAT_EAST - 1] = new Point (m_ptSeat[Game.SEAT_EAST - 1].x - m_emoticonWidth - m_cardWidth / 10, m_ptSeat[Game.SEAT_EAST - 1].y - m_emoticonHeight / 2);
		m_ptEmoticon[Game.SEAT_SOUTH - 1] = new Point (m_ptSeat[Game.SEAT_SOUTH - 1].x - m_emoticonWidth / 2, m_ptSeat[Game.SEAT_SOUTH - 1].y - m_emoticonHeight - m_cardHeight / 10);
		m_ptEmoticon[Game.SEAT_WEST - 1] = new Point (m_ptSeat[Game.SEAT_WEST - 1].x + m_cardWidth * 11 / 10, m_ptSeat[Game.SEAT_WEST - 1].y - m_emoticonHeight / 2);

		int x = m_ptSeat[Game.SEAT_NORTH - 1].x + maxWidthHand / 2 - m_bmpCardBadge.getWidth() / 2;
		int y = m_ptSeat[Game.SEAT_NORTH - 1].y - m_bmpCardBadge.getHeight()  / 2;
		m_ptUnrevealedBadge[Game.SEAT_NORTH - 1] = new Point (x,y);
		y += m_cardHeight * 3 / 2;
		m_ptRevealedBadge[Game.SEAT_NORTH - 1] = new Point (x, y);

		x = m_ptSeat[Game.SEAT_EAST - 1].x + m_cardWidth - m_bmpCardBadge.getWidth() / 2;
		y = m_ptSeat[Game.SEAT_EAST - 1].y + maxHeightHand / 2 - m_bmpCardBadge.getHeight() / 2;
		m_ptUnrevealedBadge[Game.SEAT_EAST - 1] = new Point (x, y);
		x -= m_cardWidth * 3 / 2;
		m_ptRevealedBadge[Game.SEAT_EAST - 1] = new Point (x, y);

		x = m_ptSeat[Game.SEAT_SOUTH - 1].x + maxWidthHandHuman / 2 - m_bmpCardBadge.getWidth() / 2;
		y = m_ptSeat[Game.SEAT_SOUTH - 1].y + m_cardHeight - m_bmpCardBadge.getHeight() / 2;
		m_ptUnrevealedBadge[Game.SEAT_SOUTH - 1] = new Point (x, y);
		y -= m_cardHeight * 5 / 3;
		m_ptRevealedBadge[Game.SEAT_SOUTH - 1] = new Point (x, y);

		x = m_ptSeat[Game.SEAT_WEST - 1].x - m_bmpCardBadge.getWidth() / 2;
		y = m_ptSeat[Game.SEAT_WEST - 1].y + maxHeightHand / 2 - m_bmpCardBadge.getHeight() / 2;
		m_ptUnrevealedBadge[Game.SEAT_WEST - 1] = new Point (x, y);
		x += m_cardWidth * 3 / 2;
		m_ptRevealedBadge[Game.SEAT_WEST - 1] = new Point (x, y);
		
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
			if (!c.getFaceUp() && !m_go.getFaceUp())
			{
				return;
			}

			android.os.Vibrator v = (android.os.Vibrator) GameTable.this.getContext().getSystemService(Context.VIBRATOR_SERVICE);
			if (v != null && v.hasVibrator())
			{
				VibrationEffect effect = VibrationEffect.createOneShot(
						100,
						255
				);
				v.vibrate (effect);
			}
			ShowCardHelp(c);
		}
	};
		
		
	private boolean heldSteadyHand()
	{
		if (m_touchUnrevealedSeat == 0 && m_touchRevealedSeat == 0)
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
			m_touchUnrevealedSeat = 0;
			m_touchRevealedSeat = 0;
			if (m_unrevealedBoundingRect[Game.SEAT_SOUTH - 1] != null
					&& m_unrevealedBoundingRect[Game.SEAT_SOUTH - 1].contains(x, y))
			{
				m_touchUnrevealedSeat = Game.SEAT_SOUTH;
			} else if (m_revealedBoundingRect[Game.SEAT_SOUTH - 1] != null
					&& m_revealedBoundingRect[Game.SEAT_SOUTH - 1].contains(x, y))
			{
				m_touchRevealedSeat = Game.SEAT_SOUTH;
			} else if (m_unrevealedBoundingRect[Game.SEAT_WEST - 1] != null
					&& m_unrevealedBoundingRect[Game.SEAT_WEST - 1].contains(x, y))
			{
				m_touchUnrevealedSeat = Game.SEAT_WEST;			
			} else if (m_revealedBoundingRect[Game.SEAT_WEST - 1] != null
					&& m_revealedBoundingRect[Game.SEAT_WEST - 1].contains(x, y))
			{
				m_touchRevealedSeat = Game.SEAT_WEST;
			} else if (m_unrevealedBoundingRect[Game.SEAT_NORTH - 1] != null
					&& m_unrevealedBoundingRect[Game.SEAT_NORTH - 1].contains(x, y))
			{
				m_touchUnrevealedSeat = Game.SEAT_NORTH;				
			} else if (m_revealedBoundingRect[Game.SEAT_NORTH - 1] != null
					&& m_revealedBoundingRect[Game.SEAT_NORTH - 1].contains(x, y)) {
				m_touchRevealedSeat = Game.SEAT_NORTH;
			} else if (m_unrevealedBoundingRect[Game.SEAT_EAST - 1] != null
					&& m_unrevealedBoundingRect[Game.SEAT_EAST - 1].contains(x, y))
			{
				m_touchUnrevealedSeat = Game.SEAT_EAST;			
			} else if (m_revealedBoundingRect[Game.SEAT_EAST - 1] != null
					&& m_revealedBoundingRect[Game.SEAT_EAST - 1].contains(x, y)) {
				m_touchRevealedSeat = Game.SEAT_EAST;
			}

			if (m_touchUnrevealedSeat != 0 || m_touchRevealedSeat != 0)
			{
				m_waitingForTouchAndHold = true;
				m_handler.postDelayed (m_touchAndHoldTask, 1000);

				m_ptTouchDown = new Point (x, y);
				return true;
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
				handCardTapped (max(m_touchUnrevealedSeat, m_touchRevealedSeat), m_ptTouchDown);
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
			if (m_touchUnrevealedSeat != 0 || m_touchRevealedSeat != 0)
			{
				int idx = max(m_touchUnrevealedSeat, m_touchRevealedSeat) - 1;
				if (m_unrevealedDrag[idx] != 0)
				{
					m_unrevealedOffset[idx] += m_unrevealedDrag[idx];

					// set bounds properly
					Player p = m_game.getPlayer(idx);
					int ncards = p.getHand().getUnrevealedCards().size();
					
					if (m_unrevealedOffset[idx] >= ncards - m_maxCardsDisplay)
					{
						m_unrevealedOffset[idx] = ncards - m_maxCardsDisplay;
					}
					
					if (m_unrevealedOffset[idx] < 0)
					{
						m_unrevealedOffset[idx] = 0;
					}
					
					m_unrevealedDrag[idx] = 0;
				}
				if (m_revealedDrag[idx] != 0)
				{
					m_revealedOffset[idx] += m_revealedDrag[idx];

					// set bounds properly
					Player p = m_game.getPlayer(idx);
					int ncards = p.getHand().getRevealedCards().size();

					if (m_revealedOffset[idx] >= ncards - m_maxCardsDisplay)
					{
						m_revealedOffset[idx] = ncards - m_maxCardsDisplay;
					}

					if (m_revealedOffset[idx] < 0)
					{
						m_revealedOffset[idx] = 0;
					}

					m_revealedDrag[idx] = 0;
				}
				m_touchUnrevealedSeat = 0;
				m_touchRevealedSeat = 0;
				return true;
			}
			
			return true;
		}
		else if (event.getAction() == MotionEvent.ACTION_MOVE)
		{
			int seat = max(m_touchUnrevealedSeat, m_touchRevealedSeat);
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
				if (m_touchUnrevealedSeat == seat)
				{
					m_unrevealedDrag[m_touchUnrevealedSeat - 1] = -cardoffset;
				} else if (m_touchRevealedSeat == seat) {
					m_revealedDrag[m_touchRevealedSeat - 1] = -cardoffset;
				}
				this.invalidate();
				
				return true;
			}
		}
		return super.onTouchEvent(event);
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

		Rect ru = m_unrevealedBoundingRect[seat - 1];
		Rect rr = m_revealedBoundingRect[seat - 1];

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

			int numcardsshowing = h.getUnrevealedCards().size() - m_unrevealedOffset[seat - 1];
			numcardsshowing = Math.min(numcardsshowing, m_maxCardsDisplay);

			if (idx >= numcardsshowing) {
				idx = numcardsshowing - 1;
			}
			idx += m_unrevealedOffset[seat - 1];
            return h.getUnrevealedCards().get(idx);

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

			int numcardsshowing = h.getRevealedCards().size() - m_revealedOffset[seat - 1];
			numcardsshowing = Math.min(numcardsshowing, m_maxCardsDisplay);

			if (idx >= numcardsshowing) {
				idx = numcardsshowing - 1;
			}
			idx += m_revealedOffset[seat - 1];
			return h.getRevealedCards().get(idx);
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
		if (max(m_touchUnrevealedSeat, m_touchRevealedSeat) != 0)
		{
			return findTouchedCardHand (max(m_touchUnrevealedSeat, m_touchRevealedSeat), pt);
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
	
		// canvas.drawRect(0, 0, getWidth(), getHeight(), m_paintTable);
						
		// draw the color and direction indicator
		
		Bitmap bmp = null;
		
		int curr_color = m_game.getCurrColor();

		if (!m_game.getRoundComplete())
		{
			if (m_game.getDirection() == Game.DIR_CCLOCKWISE)
			{
				switch (curr_color)
				{
				case Card.COLOR_WILD:
					bmp = m_bmpDirColorCCW;
					break;
				case Card.COLOR_RED:
					bmp = m_bmpDirColorCCWRed;
					break;
				case Card.COLOR_GREEN:
					bmp = m_bmpDirColorCCWGreen;
					break;
				case Card.COLOR_BLUE:
					bmp = m_bmpDirColorCCWBlue;
					break;
				case Card.COLOR_YELLOW:
					bmp = m_bmpDirColorCCWYellow;
					break;
				}
			}
			else
			{
				switch (curr_color)
				{
				case Card.COLOR_WILD:
					bmp = m_bmpDirColorCW;
					break;
				case Card.COLOR_RED:
					bmp = m_bmpDirColorCWRed;
					break;
				case Card.COLOR_GREEN:
					bmp = m_bmpDirColorCWGreen;
					break;
				case Card.COLOR_BLUE:
					bmp = m_bmpDirColorCWBlue;
					break;
				case Card.COLOR_YELLOW:
					bmp = m_bmpDirColorCWYellow;
					break;
				}
			}

			// before the deal, we don't have a direction
			if (bmp == null)
			{
				return;
			}
		
			m_drawMatrix.reset();
			m_drawMatrix.setScale(1, 1);
			m_drawMatrix.setTranslate(m_ptDirColor.x, m_ptDirColor.y);
			canvas.drawBitmap(bmp, m_drawMatrix, null);
		}

		displayScore (canvas);
				
		int x = 0;
		int y = 0;

		// draw the hands

		for (i = 0; i < 4; i++) 
		{
			Player p = m_game.getPlayer(i);


			// don't draw ejected players' cards
			
			if (p.getActive()) 
			{
				RedrawHand (canvas, i + 1);
			}
		}
		
		Player p = m_game.getCurrPlayer();
		if (p != null && !m_game.getRoundComplete())
		{
			Point pt = m_ptPlayerIndicator[p.getSeat() - 1];
	
			m_drawMatrix.reset();
			m_drawMatrix.setScale(1, 1);
			m_drawMatrix.setTranslate(pt.x, pt.y);
			
			canvas.drawBitmap(m_bmpPlayerIndicator[curr_color - 1][p.getSeat() - 1], m_drawMatrix, null);
		}		
		
		if (m_game.getFastForward())
		{
			return;
		}
		
		// draw the discard pile
		
		CardPile pile = m_game.getDiscardPile ();
		int numCardsInPlay = pile.getNumCards();
		CardDeck deck = m_game.getDeck ();

		int skip = 16;
		if (deck != null)
		{
			if (deck.getNumCards () > 108) 
			{
				skip = 32;
			}
		}
		
		if (pile != null)
		{			
			for (i = 0; i < numCardsInPlay; i += skip) 
			{
				// make sure that the top card is drawn...
				if (i >= numCardsInPlay - skip) 
				{
					i = numCardsInPlay - 1;
				}
				
				Card c = pile.getCard(i);
				if (c != null) 
				{
					// FIXME -- make resolution independent
					x = m_ptDiscardPile.x + (int)((float)i / (float)skip) * 2;
					y = m_ptDiscardPile.y + (int)((float)i / (float)skip) * 2;
					c.setFaceUp(true);

					this.drawCard (canvas, c, x, y, true);
				}
			}
		}

		
		m_discardPileBoundingRect = new Rect(m_ptDiscardPile.x, m_ptDiscardPile.y, x + m_cardWidth, y + m_cardHeight);
		
		// draw the draw pile
		
		pile = m_game.getDrawPile();
		

		if (pile != null)
		{
			x = m_ptDrawPile.x;
			y = m_ptDrawPile.y;
			int numCardsInPile = pile.getNumCards();
			for (i = 0; i < numCardsInPile; i += skip) 
			{
				if (i >= numCardsInPile - skip)
				{
					i = numCardsInPile - 1;
				}

				Card c = pile.getCard(i);
				if (c != null)
				{
					this.drawCard (canvas, c, x, y, false);
					// FIXME -- make resolution independent!
					x += 2;
					y += 2;
				}
			}
		}
		
		m_drawPileBoundingRect = new Rect(m_ptDrawPile.x, m_ptDrawPile.y, x + m_cardWidth, y + m_cardHeight);
		

		
		if (m_game.getWinner() != 0)
		{
			m_drawMatrix.reset();
			m_drawMatrix.setScale(1, 1);
			m_drawMatrix.setTranslate(m_ptWinningMessage.x, m_ptWinningMessage.y);

			canvas.drawBitmap(m_bmpWinningMessage[m_game.getWinner() - 1], m_drawMatrix, null);			
		}
		
		drawPenalty(canvas);		
	}
	
	private void RedrawHand (Canvas cv, int seat)
	{
		Hand h = m_game.getPlayer(seat - 1).getHand();
		if (h == null)
		{
			return;
		}

		List<Card> revealedHand = h.getRevealedCards();
		List<Card> unrevealedHand = h.getUnrevealedCards();

		int x = 0;
		int y = 0;
		int dx = 0;
		int dy = 0;
		int numRevealedCards = revealedHand.size();
		int numUnrevealedCards = unrevealedHand.size();

		// keep the offsets sane
		m_unrevealedOffset[seat-1] = max(0, min(m_unrevealedOffset[seat-1], numUnrevealedCards - m_maxCardsDisplay));
		m_revealedOffset[seat-1] = max(0, min(m_revealedOffset[seat-1], numRevealedCards - m_maxCardsDisplay));

		// apply the current drag
		int unrevealedOffset = m_unrevealedOffset[seat - 1] + m_unrevealedDrag[seat - 1];
		int revealedOffset = m_revealedOffset[seat - 1] + m_revealedDrag[seat - 1];
		unrevealedOffset = max(0, min(unrevealedOffset, numUnrevealedCards - m_maxCardsDisplay));
		revealedOffset = max(0, min(revealedOffset, numRevealedCards - m_maxCardsDisplay));

		int numUnrevealedShowing = min(numUnrevealedCards - m_unrevealedOffset[seat - 1], m_maxCardsDisplay);
		int numRevealedShowing = min(numRevealedCards - m_revealedOffset[seat - 1], m_maxCardsDisplay);

		int unrevealedWidth;
		int unrevealedHeight;
		int revealedWidth;
		int revealedHeight;


		int spacing = (seat == Game.SEAT_SOUTH) ? m_cardSpacingSouth : m_cardSpacing;
		
		switch (seat) {
		case Game.SEAT_SOUTH:
			dx = spacing;
			dy = 0;
			revealedWidth = (numRevealedShowing - 1) * spacing + m_cardWidth;
			x = m_ptSeat[Game.SEAT_SOUTH - 1].x - revealedWidth / 2;
			y = m_ptSeat[Game.SEAT_SOUTH - 1].y - m_cardHeight * 2 / 3;
			m_revealedBoundingRect[Game.SEAT_SOUTH - 1] = new Rect(x, y, x + revealedWidth, y + m_cardHeight);
			break;
		case Game.SEAT_WEST:
			dx = 0;
			dy = spacing;
			revealedHeight = (numRevealedShowing - 1) * spacing + m_cardHeight;
			x = m_ptSeat[Game.SEAT_WEST - 1].x + m_cardWidth / 2;
			y = m_ptSeat[Game.SEAT_WEST - 1].y - revealedHeight / 2;
			m_revealedBoundingRect[Game.SEAT_WEST - 1] = new Rect(x, y, x + m_cardWidth, y + revealedHeight);
			break;
		case Game.SEAT_NORTH:
			dx = spacing;
			dy = 0;
			revealedWidth = (numRevealedShowing - 1) * spacing + m_cardWidth;
			x = m_ptSeat[Game.SEAT_NORTH - 1].x - revealedWidth / 2;
			y = m_ptSeat[Game.SEAT_NORTH - 1].y + m_cardHeight / 2;
			m_revealedBoundingRect[Game.SEAT_NORTH - 1] = new Rect(x, y, x + revealedWidth, y + m_cardHeight);
			break;
		case Game.SEAT_EAST:
			dx = 0;
			dy = spacing;
			revealedHeight = (numRevealedShowing - 1) * spacing + m_cardHeight;
			x = m_ptSeat[Game.SEAT_EAST - 1].x - m_cardWidth / 2;
			y = m_ptSeat[Game.SEAT_EAST - 1].y - revealedHeight / 2;
			m_revealedBoundingRect[Game.SEAT_EAST - 1] = new Rect(x, y, x + m_cardWidth, y + revealedHeight);
			break;
		}

		// draw the cards that are on the table

        int stop = Math.min(revealedOffset + m_maxCardsDisplay, numRevealedCards);

		int j;
		for (j = revealedOffset; j < stop; j++)
		{
			Card c = revealedHand.get(j);
			if (c == null) 
			{
				continue;
			}

			this.drawCard (cv, c, x, y, c.getFaceUp());

			x += dx;
			y += dy;
		}

		switch (seat) {
			case Game.SEAT_SOUTH:
				dx = spacing;
				dy = 0;
				if (numUnrevealedShowing == 0)
				{
					unrevealedWidth = 0;
				} else {
					unrevealedWidth = (numUnrevealedShowing - 1) * spacing + m_cardWidth;
				}
				x = m_ptSeat[Game.SEAT_SOUTH - 1].x - unrevealedWidth / 2;
				y = m_ptSeat[Game.SEAT_SOUTH - 1].y;
				m_unrevealedBoundingRect[Game.SEAT_SOUTH - 1] = new Rect(x, y, x + unrevealedWidth, y + m_cardHeight);
				break;
			case Game.SEAT_WEST:
				dx = 0;
				dy = spacing;
				unrevealedHeight = (numUnrevealedShowing - 1) * spacing + m_cardHeight;
				x = m_ptSeat[Game.SEAT_WEST - 1].x;
				y = m_ptSeat[Game.SEAT_WEST - 1].y - unrevealedHeight / 2;
				m_unrevealedBoundingRect[Game.SEAT_WEST - 1] = new Rect(x, y, x + m_cardWidth, y + unrevealedHeight);
				break;
			case Game.SEAT_NORTH:
				dx = spacing;
				dy = 0;
				unrevealedWidth = (numUnrevealedShowing - 1) * spacing + m_cardWidth;
				x = m_ptSeat[Game.SEAT_NORTH - 1].x - unrevealedWidth / 2;
				y = m_ptSeat[Game.SEAT_NORTH - 1].y;
				m_unrevealedBoundingRect[Game.SEAT_NORTH - 1] = new Rect(x, y, x + unrevealedWidth, y + m_cardHeight);
				break;
			case Game.SEAT_EAST:
				dx = 0;
				dy = spacing;
				unrevealedHeight = (numUnrevealedShowing - 1) * spacing + m_cardHeight;
				x = m_ptSeat[Game.SEAT_EAST - 1].x;
				y = m_ptSeat[Game.SEAT_EAST - 1].y - unrevealedHeight / 2;
				m_unrevealedBoundingRect[Game.SEAT_EAST - 1] = new Rect(x, y, x + m_cardWidth, y + unrevealedHeight);
				break;
		}

		// draw the cards that are on the table

		stop = Math.min(unrevealedOffset + m_maxCardsDisplay, numUnrevealedCards);

		for (j = unrevealedOffset; j < stop; j++)
		{
			Card c = unrevealedHand.get(j);
			if (c == null)
			{
				continue;
			}

			this.drawCard (cv, c, x, y, c.getFaceUp());

			x += dx;
			y += dy;
		}

		if (numRevealedCards > m_maxCardsDisplay)
		{
			Point pt = m_ptRevealedBadge[seat - 1];

			m_drawMatrix.reset();
			m_drawMatrix.setScale(1, 1);
			m_drawMatrix.setTranslate(pt.x, pt.y);

			cv.drawBitmap(m_bmpCardBadge, m_drawMatrix, null);

			float fx = (float)(pt.x + m_bmpCardBadge.getWidth() / 2);
			Rect textBounds = new Rect();
			String numstr = "" + numRevealedCards;

			m_paintCardBadgeText.getTextBounds(numstr, 0, numstr.length(), textBounds);
			float fy = (float)(pt.y + m_bmpCardBadge.getHeight() / 2 + (textBounds.height() / 2));

			cv.drawText(numstr, fx, fy, m_paintCardBadgeText);
		}

		if (numUnrevealedCards > m_maxCardsDisplay)
		{
			Point pt = m_ptUnrevealedBadge[seat - 1];

			m_drawMatrix.reset();
			m_drawMatrix.setScale(1, 1);
			m_drawMatrix.setTranslate(pt.x, pt.y);

			cv.drawBitmap(m_bmpCardBadge, m_drawMatrix, null);

			float fx = (float)(pt.x + m_bmpCardBadge.getWidth() / 2);
			Rect textBounds = new Rect();
			String numstr = "" + numUnrevealedCards;

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

		m_bmpDirColorCCW = BitmapFactory.decodeResource(res, R.drawable.ccw, opt);
		m_bmpDirColorCCWRed = BitmapFactory.decodeResource(res, R.drawable.ccw_red, opt);
		m_bmpDirColorCCWBlue = BitmapFactory.decodeResource(res, R.drawable.ccw_blue, opt);
		m_bmpDirColorCCWGreen = BitmapFactory.decodeResource(res, R.drawable.ccw_green, opt);
		m_bmpDirColorCCWYellow = BitmapFactory.decodeResource(res, R.drawable.ccw_yellow, opt);

		m_bmpDirColorCW = BitmapFactory.decodeResource(res, R.drawable.cw, opt);
		m_bmpDirColorCWRed = BitmapFactory.decodeResource(res, R.drawable.cw_red, opt);
		m_bmpDirColorCWBlue = BitmapFactory.decodeResource(res, R.drawable.cw_blue, opt);
		m_bmpDirColorCWGreen = BitmapFactory.decodeResource(res, R.drawable.cw_green, opt);
		m_bmpDirColorCWYellow = BitmapFactory.decodeResource(res, R.drawable.cw_yellow, opt);
		
		m_bmpPlayerIndicator[Card.COLOR_RED - 1][Game.SEAT_SOUTH - 1] = BitmapFactory.decodeResource(res, R.drawable.player_red_south, opt);
		m_bmpPlayerIndicator[Card.COLOR_GREEN - 1][Game.SEAT_SOUTH - 1] = BitmapFactory.decodeResource(res, R.drawable.player_green_south, opt);
		m_bmpPlayerIndicator[Card.COLOR_BLUE - 1][Game.SEAT_SOUTH - 1] = BitmapFactory.decodeResource(res, R.drawable.player_blue_south, opt);
		m_bmpPlayerIndicator[Card.COLOR_YELLOW - 1][Game.SEAT_SOUTH - 1] = BitmapFactory.decodeResource(res, R.drawable.player_yellow_south, opt);
		m_bmpPlayerIndicator[Card.COLOR_WILD - 1][Game.SEAT_SOUTH - 1] = BitmapFactory.decodeResource(res, R.drawable.player_south, opt);

		m_bmpPlayerIndicator[Card.COLOR_RED - 1][Game.SEAT_WEST - 1] = BitmapFactory.decodeResource(res, R.drawable.player_red_west, opt);
		m_bmpPlayerIndicator[Card.COLOR_GREEN - 1][Game.SEAT_WEST - 1] = BitmapFactory.decodeResource(res, R.drawable.player_green_west, opt);
		m_bmpPlayerIndicator[Card.COLOR_BLUE - 1][Game.SEAT_WEST - 1] = BitmapFactory.decodeResource(res, R.drawable.player_blue_west, opt);
		m_bmpPlayerIndicator[Card.COLOR_YELLOW - 1][Game.SEAT_WEST - 1] = BitmapFactory.decodeResource(res, R.drawable.player_yellow_west, opt);
		m_bmpPlayerIndicator[Card.COLOR_WILD - 1][Game.SEAT_WEST - 1] = BitmapFactory.decodeResource(res, R.drawable.player_west, opt);
		
		m_bmpPlayerIndicator[Card.COLOR_RED - 1][Game.SEAT_NORTH - 1] = BitmapFactory.decodeResource(res, R.drawable.player_red_north, opt);
		m_bmpPlayerIndicator[Card.COLOR_GREEN - 1][Game.SEAT_NORTH - 1] = BitmapFactory.decodeResource(res, R.drawable.player_green_north, opt);
		m_bmpPlayerIndicator[Card.COLOR_BLUE - 1][Game.SEAT_NORTH - 1] = BitmapFactory.decodeResource(res, R.drawable.player_blue_north, opt);
		m_bmpPlayerIndicator[Card.COLOR_YELLOW - 1][Game.SEAT_NORTH - 1] = BitmapFactory.decodeResource(res, R.drawable.player_yellow_north, opt);
		m_bmpPlayerIndicator[Card.COLOR_WILD - 1][Game.SEAT_NORTH - 1] = BitmapFactory.decodeResource(res, R.drawable.player_north, opt);

		m_bmpPlayerIndicator[Card.COLOR_RED - 1][Game.SEAT_EAST - 1] = BitmapFactory.decodeResource(res, R.drawable.player_red_east, opt);
		m_bmpPlayerIndicator[Card.COLOR_GREEN - 1][Game.SEAT_EAST - 1] = BitmapFactory.decodeResource(res, R.drawable.player_green_east, opt);
		m_bmpPlayerIndicator[Card.COLOR_BLUE - 1][Game.SEAT_EAST - 1] = BitmapFactory.decodeResource(res, R.drawable.player_blue_east, opt);
		m_bmpPlayerIndicator[Card.COLOR_YELLOW - 1][Game.SEAT_EAST - 1] = BitmapFactory.decodeResource(res, R.drawable.player_yellow_east, opt);
		m_bmpPlayerIndicator[Card.COLOR_WILD - 1][Game.SEAT_EAST - 1] = BitmapFactory.decodeResource(res, R.drawable.player_east, opt);
		
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
	
	
	private void drawCard (Canvas cv, Card c, int x, int y, boolean faceup)
	{
		m_drawMatrix.reset();
		m_drawMatrix.setScale(1, 1);
		m_drawMatrix.setTranslate(x, y);

		Bitmap b;
		if (faceup || m_go.getFaceUp()) 
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
	    
	    if (p.getType() == Penalty.PENTYPE_NONE)
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
			String numCards = "" + p.getNumCards();

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

			// adjust emoticon position for revealed cards offset
			if (!pv.getHand().getRevealedCards().isEmpty())
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

			// adjust emoticon position for revealed cards offset
			if (!pa.getHand().getRevealedCards().isEmpty())
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
		new AlertDialog.Builder(this.getContext())
			.setCancelable(false)
			.setTitle(R.string.prompt_color)
			.setItems(R.array.colors,
                    (dialoginterface, i) -> {
                        Player p = m_game.getCurrPlayer();
                        if (p instanceof HumanPlayer)
                        {
                            ((HumanPlayer)p).setColor(i + 1);
                        }
                    })
				.show();
	}
}
