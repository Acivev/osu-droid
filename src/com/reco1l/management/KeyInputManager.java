package com.reco1l.management;

import android.view.KeyEvent;
import android.view.MotionEvent;

import com.reco1l.Game;
import com.reco1l.UI;
import com.reco1l.interfaces.IBaseScene;
import com.reco1l.ui.custom.Dialog;
import com.reco1l.ui.BaseFragment;

import org.anddev.andengine.entity.scene.Scene;

import java.util.List;

// This only have support for Back button.
public class KeyInputManager {

    private static final List<Dialog> dialogs = Game.platform.dialogs;

    //--------------------------------------------------------------------------------------------//

    public static void performBack() {
        handle(KeyEvent.KEYCODE_BACK, MotionEvent.ACTION_DOWN);
    }

    public static boolean handle(final int key, final int action) {
        if (!Game.engine.isGlobalInitialized) {
            return false;
        }

        Scene currentScene = Game.engine.getScene();
        Scene lastScene = Game.engine.lastScene;

        if (currentScene == Game.loaderScene) {
            return false;
        }

        if (key == KeyEvent.KEYCODE_BACK && action == MotionEvent.ACTION_DOWN) {

            for (BaseFragment fragment : UI.getExtras()) {
                if (fragment.isAdded()) {
                    fragment.close();
                    return true;
                }
            }

            if (dialogs.size() > 0) {
                Dialog dialog = dialogs.get(dialogs.size() - 1);
                if (dialog.builder.closeOnBackPress) {
                    dialog.close();
                    return true;
                }
                return true;
            }

            IBaseScene currentHandler = Game.engine.getCurrentSceneHandler();
            if (currentHandler != null) {
                return currentHandler.onBackPress();
            }

            if (currentScene == Game.gameScene.getScene()) {
                if (Game.gameScene.isPaused()) {
                    Game.gameScene.resume();
                    return true;
                }
                Game.gameScene.pause();
                return true;
            }

            if (lastScene != null) {
                Game.engine.setScene(lastScene);
                return true;
            }
            Game.engine.setScene(Game.mainScene);
            return true;
        }
        return false;
    }
}