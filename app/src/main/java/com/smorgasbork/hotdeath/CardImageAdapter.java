package com.smorgasbork.hotdeath;

import java.util.HashMap;

import android.widget.BaseAdapter;
import android.content.Context;
import android.view.*;
import android.widget.ImageView;
import android.widget.GridView;

public class CardImageAdapter extends BaseAdapter {
    private final Context mContext;
    private final Integer[] m_cardIDs;
    private final Integer[] m_thumbIDs;

    public Integer[] getCardIDs()
    {
    	return m_cardIDs;
    }
    
    public CardImageAdapter(Context c) 
    {
    	GameActivity ga = (GameActivity)c;
    	
    	// query the current card deck to see what cards are actually in use
    	Game g = ga.getGame();
    	CardDeck d = g.getDeck();
    	Card[] cary = d.getCards();
    	
    	HashMap<Integer, Boolean> usedIDs = new HashMap<>();
        for (Card card : cary) {
            if (usedIDs.containsKey(card.getID())) {
                continue;
            }

            usedIDs.put(card.getID(), true);
        }

    	// go through all cards in order and add them to the array
    	Integer[] cardids = ga.getCardIDs();
    	
    	int idx = 0;
    	m_thumbIDs = new Integer[usedIDs.size()];
    	m_cardIDs = new Integer[usedIDs.size()];
        for (Integer cardid : cardids) {
            if (usedIDs.containsKey(cardid)) {
                m_cardIDs[idx] = cardid;
                m_thumbIDs[idx] = ((GameActivity) c).getCardImageID(cardid);
                idx++;
            }
        }
    	
        mContext = c;
    }

    public int getCount() {
        return m_thumbIDs.length;
    }

    public Object getItem(int position) {
        return null;
    }

    public long getItemId(int position) {
        return 0;
    }

    // create a new ImageView for each item referenced by the Adapter
    public View getView(int position, View convertView, ViewGroup parent) 
    {

        ImageView imageView;
        if (convertView == null) {  // if it's not recycled, initialize some attributes
            imageView = new ImageView(mContext);
            final float scale = imageView.getContext().getResources().getDisplayMetrics().density;
            imageView.setLayoutParams(new GridView.LayoutParams((int)(85 * scale + 0.5f), (int)(85 * scale + 0.5f)));
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setPadding(8, 8, 8, 8);
        } else {
            imageView = (ImageView) convertView;
        }
        
        imageView.setImageResource(m_thumbIDs[position]);
        return imageView;
    }

}