package ru.nsu.ccfit.zuev.osu.menu;

import android.content.Context;

public interface IFilterMenu {

    String getFilter();

    SongMenu.SortOrder getOrder();

    boolean isFavoritesOnly();

    String getFavoriteFolder();

    void loadConfig(Context context);

    void hideMenu();

    void showMenu(SongMenu parent);
}
