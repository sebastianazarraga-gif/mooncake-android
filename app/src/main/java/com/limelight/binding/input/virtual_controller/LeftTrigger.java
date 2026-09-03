/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.content.Context;

public class LeftTrigger extends DigitalButton {
    public LeftTrigger(final VirtualController controller, final int layer, final Context context) {
        super(controller, EID_LT, layer, context);
    }

    @Override
    protected void onDefaultGamepadAction(boolean active) {
        VirtualController.ControllerInputContext inputContext =
                virtualController.getControllerInputContext();
        inputContext.leftTrigger = (byte) (active ? 0xFF : 0x00);

        virtualController.sendControllerInputContext();
    }
}
