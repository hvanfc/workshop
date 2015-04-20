package com.barrysbeerbar.card;

import android.content.Context;
import android.content.SharedPreferences;

public final class CardStorage {

    public static final String BARRYS_BEER_BAR_PREFERENCES = "BarrysBeerBarPreferences";
    public static final String CARD_HOLDER_NAME = "cardHolderName";

    public static String getCardHolderName(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(BARRYS_BEER_BAR_PREFERENCES, Context.MODE_PRIVATE);
        return sharedPreferences.getString(CARD_HOLDER_NAME, "");
    }

    public static void setCardHolderName(Context context, String cardHolderName) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(BARRYS_BEER_BAR_PREFERENCES, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(CARD_HOLDER_NAME, cardHolderName);
        editor.apply();
    }
}
