/*
 * Copyright (C) 2017 Peter Gregus for GravityBox Project (C3C076@xda)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ceco.nougat.gravitybox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.ceco.nougat.gravitybox.quicksettings.AospTile;
import com.ceco.nougat.gravitybox.quicksettings.OnePlus3TTile;
import com.ceco.nougat.gravitybox.quicksettings.QsPanel;
import com.ceco.nougat.gravitybox.quicksettings.QsPanelQuick;
import com.ceco.nougat.gravitybox.quicksettings.QsQuickPulldownHandler;
import com.ceco.nougat.gravitybox.quicksettings.QsTile;
import com.ceco.nougat.gravitybox.quicksettings.QsTileEventDistributor;
import com.ceco.nougat.gravitybox.quicksettings.TileOrderActivity;

import android.content.Context;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam;

public class ModQsTiles {
    public static final String PACKAGE_NAME = "com.android.systemui";
    public static final String TAG = "GB:ModQsTile";
    public static final boolean DEBUG = false;

    public static final String CLASS_TILE_HOST = Utils.isXperiaDevice() ?
            "com.sonymobile.systemui.qs.SomcQSTileHost" :
            "com.android.systemui.statusbar.phone.QSTileHost";
    public static final String TILES_SETTING = "sysui_qs_tiles";

    public static final List<String> GB_TILE_KEYS = new ArrayList<String>(Arrays.asList(
            //"gb_tile_battery",
            "gb_tile_nfc",
            "gb_tile_gps_slimkat",
            "gb_tile_gps_alt",
            "gb_tile_ringer_mode",
            "gb_tile_volume",
            "gb_tile_network_mode",
            "gb_tile_smart_radio",
            "gb_tile_sync",
            "gb_tile_torch",
            "gb_tile_sleep",
            "gb_tile_stay_awake",
            "gb_tile_quickrecord",
            "gb_tile_quickapp",
            "gb_tile_quickapp2",
            "gb_tile_quickapp3",
            "gb_tile_quickapp4",
            "gb_tile_expanded_desktop",
            "gb_tile_screenshot",
            "gb_tile_gravitybox",
            "gb_tile_usb_tether",
            "gb_tile_music",
            "gb_tile_lock_screen",
            "gb_tile_quiet_hours",
            "gb_tile_compass",
            "gb_tile_bt_tethering"
    ));

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static QsTileEventDistributor mEventDistributor;
    private static QsQuickPulldownHandler mQuickPulldownHandler;
    private static QsPanel mQsPanel;
    private static QsPanelQuick mQsPanelQuick;
    private static Map<String,Object> mTiles = new HashMap<>();

    public static void initResources(final InitPackageResourcesParam resparam) {
        if (Utils.isXperiaDevice()) {
            resparam.res.setReplacement(PACKAGE_NAME, "integer", "config_maxToolItems", 60);
        }
    }

    public static void init(final XSharedPreferences prefs, final ClassLoader classLoader) {
        try {
            if (DEBUG) log("init");

            Class<?> classTileHost = XposedHelpers.findClass(CLASS_TILE_HOST, classLoader);
            mQsPanel = new QsPanel(prefs, classLoader);
            if (Utils.isOxygenOs35Rom()) {
                mQsPanelQuick = new QsPanelQuick(prefs, classLoader);
            }

            XposedHelpers.findAndHookMethod(classTileHost, "onTuningChanged",
                    String.class, String.class, new XC_MethodHook() {
                @SuppressWarnings("unchecked")
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!TILES_SETTING.equals(param.args[0])) return;
                    if (Utils.isOxygenOs35Rom()) {
                        param.args[1] = OnePlus3TTile.ALL_TILES;
                    }

                    if (mEventDistributor != null) {
                        for (Object tile : mTiles.values()) {
                            XposedHelpers.callMethod(tile, "handleDestroy");
                        }
                        mTiles.clear();
                        Map<String, Object> tileMap = (Map<String, Object>)
                                XposedHelpers.getObjectField(param.thisObject, "mTiles");
                        tileMap.clear();
                        ((List<?>)XposedHelpers.getObjectField(param.thisObject, "mTileSpecs")).clear();
                    }
                }
                @SuppressWarnings("unchecked")
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!TILES_SETTING.equals(param.args[0])) return;

                    if (mEventDistributor == null) {
                        mEventDistributor = new QsTileEventDistributor(param.thisObject, prefs);
                        mQsPanel.setEventDistributor(mEventDistributor);
                        if (DEBUG) log("Tile event distributor created");
                    }

                    Map<String, Object> tileMap = (Map<String, Object>)
                            XposedHelpers.getObjectField(param.thisObject, "mTiles");
                    Map<String, Object> gbTileMap = new HashMap<String,Object>();
                    Context context = (Context) XposedHelpers.callMethod(param.thisObject, "getContext");

                    // prepare AOSP tile wrappers
                    for (Entry<String,Object> entry : tileMap.entrySet()) {
                        AospTile aospTile = AospTile.create(param.thisObject, entry.getValue(), 
                                entry.getKey(), prefs, mEventDistributor);
                        if (aospTile != null) {
                            mTiles.put(aospTile.getKey(), aospTile.getTile());
                            gbTileMap.put(aospTile.getKey(), aospTile);
                        }
                    }

                    // prepare GB tiles
                    for (String key : GB_TILE_KEYS) {
                        QsTile tile = QsTile.create(param.thisObject, key, prefs, mEventDistributor);
                        if (tile != null) {
                            mTiles.put(key, tile.getTile());
                            gbTileMap.put(key, tile);
                        }
                    }

                    // sort tiles
                    LinkedHashMap<String, Object> orderedMap = new LinkedHashMap<String,Object>();
                    String[] orderedKeys = prefs.getString(
                            TileOrderActivity.PREF_KEY_TILE_ENABLED,
                            TileOrderActivity.getDefaultTileList(
                                    Utils.getGbContext(context))).split(",");
                    for (String key : orderedKeys) {
                         for (Entry<String, Object> entry : gbTileMap.entrySet()) {
                             if (entry.getValue() instanceof AospTile) {
                                 AospTile t = (AospTile) entry.getValue();
                                 if (key.equals(t.getKey())) {
                                     orderedMap.put(t.getAospKey(), tileMap.get(t.getAospKey()));
                                     tileMap.remove(t.getAospKey());
                                     if (DEBUG) log("orderedMap: added " + t.getKey());
                                     break;
                                 }
                             } else {
                                 QsTile t = (QsTile) entry.getValue();
                                 if (key.equals(t.getKey())) {
                                     orderedMap.put(key, t.getTile());
                                     gbTileMap.remove(key);
                                     if (DEBUG) log("orderedMap: added " + key);
                                     break;
                                 }
                             }
                         }
                    }

                    // add left-overs
                    for (Entry<String,Object> entry : tileMap.entrySet()) {
                        orderedMap.put(entry.getKey(), entry.getValue());
                        if (DEBUG) log("orderedMap: added disabled or unknown AOSP tile: " + entry.getKey());
                    }
                    for (Entry<String,Object> entry : gbTileMap.entrySet()) {
                        if (entry.getValue() instanceof QsTile) {
                            orderedMap.put(entry.getKey(), ((QsTile)entry.getValue()).getTile());
                            if (DEBUG) log("orderedMap: added disabled GB tile: " + entry.getKey());
                        }
                    }

                    // put all tiles into host tile map
                    tileMap.clear();
                    tileMap.putAll(orderedMap);

                    List<?> cbs = (List<?>) XposedHelpers.getObjectField(param.thisObject, "mCallbacks");
                    for (int i = 0; i < cbs.size(); i++) {
                        XposedHelpers.callMethod(cbs.get(i), "onTilesChanged");
                    }

                    if (DEBUG) log("Tiles created");

                    if (mQuickPulldownHandler == null) {
                        mQuickPulldownHandler = new QsQuickPulldownHandler(context, prefs, mEventDistributor);
                    }
                    mQsPanel.updateResources();
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
