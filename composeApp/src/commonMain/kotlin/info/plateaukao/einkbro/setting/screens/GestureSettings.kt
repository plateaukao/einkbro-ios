package info.plateaukao.einkbro.setting.screens

import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.*
import info.plateaukao.einkbro.setting.BooleanSettingItem
import info.plateaukao.einkbro.setting.DividerSettingItem
import info.plateaukao.einkbro.setting.GestureActionSettingItem
import info.plateaukao.einkbro.setting.SettingItemInterface

fun buildGestureSettingItems(deps: SettingScreenDeps): List<SettingItemInterface> {
    val config = deps.config
    return listOf(
        DividerSettingItem(
            Res.string.setting_title_touch_area_actions,
        ),
        GestureActionSettingItem(
            Res.string.setting_touch_up_click,
            config = config.touch::upClickGesture,
        ),
        GestureActionSettingItem(
            Res.string.setting_touch_up_long_click,
            config = config.touch::upLongClickGesture,
        ),
        GestureActionSettingItem(
            Res.string.setting_touch_down_click,
            config = config.touch::downClickGesture,
        ),
        GestureActionSettingItem(
            Res.string.setting_touch_down_long_click,
            config = config.touch::downLongClickGesture,
        ),
        BooleanSettingItem(
            Res.string.show_touch_area_hint,
            config = config.touch::touchAreaHint,
            span = 2,
        ),
        BooleanSettingItem(
            Res.string.hie_touch_area_when_input,
            config = config.touch::hideTouchAreaWhenInput,
            span = 2,
        ),
        BooleanSettingItem(
            Res.string.switch_touch_area_action,
            config = config.touch::switchTouchAreaAction,
            span = 2,
        ),
        BooleanSettingItem(
            Res.string.enable_touch_area_as_arrow_key,
            config = config.touch::longClickAsArrowKey,
            span = 2,
        ),
        DividerSettingItem(Res.string.volume_page_turn),
        BooleanSettingItem(
            Res.string.volume_page_turn,
            null,
            Res.string.volume_page_turn_summary,
            config.touch::volumePageTurn,
            span = 2,
        ),
        BooleanSettingItem(
            Res.string.volume_double_click_back,
            null,
            Res.string.volume_double_click_back_summary,
            config.touch::volumeDoubleClickBack,
            span = 2,
        ),
        DividerSettingItem(Res.string.setting_multitouch_use_title),
        BooleanSettingItem(
            Res.string.setting_multitouch_use_title,
            null,
            Res.string.setting_multitouch_use_summary,
            config.touch::isMultitouchEnabled,
            span = 2,
        ),
        GestureActionSettingItem(
            Res.string.setting_gesture_up,
            config = config.touch::multitouchUp,
        ),
        GestureActionSettingItem(
            Res.string.setting_gesture_down,
            config = config.touch::multitouchDown,
        ),
        GestureActionSettingItem(
            Res.string.setting_gesture_left,
            config = config.touch::multitouchLeft,
        ),
        GestureActionSettingItem(
            Res.string.setting_gesture_right,
            config = config.touch::multitouchRight,
        ),
        DividerSettingItem(Res.string.gesture_on_floating_button),
        BooleanSettingItem(
            Res.string.setting_gestures_use_title,
            null,
            Res.string.setting_gestures_use_summary,
            config.touch::enableNavButtonGesture,
            span = 2,
        ),
        GestureActionSettingItem(
            Res.string.setting_gesture_up,
            config = config.touch::navGestureUp,
        ),
        GestureActionSettingItem(
            Res.string.setting_gesture_down,
            config = config.touch::navGestureDown,
        ),
        GestureActionSettingItem(
            Res.string.setting_gesture_left,
            config = config.touch::navGestureLeft,
        ),
        GestureActionSettingItem(
            Res.string.setting_gesture_right,
            config = config.touch::navGestureRight,
        ),
        GestureActionSettingItem(
            Res.string.setting_floating_button_long_click,
            config = config.touch::navButtonLongClickGesture,
        ),
    )
}
