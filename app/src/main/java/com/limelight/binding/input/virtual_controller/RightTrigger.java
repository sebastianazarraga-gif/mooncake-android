/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.content.Context;

public class RightTrigger extends DigitalButton {
    public RightTrigger(final VirtualController controller, final int layer, final Context context) {
        super(controller, EID_RT, layer, context);
    }

    @Override
    protected void onDefaultGamepadAction(boolean active) {
        VirtualController.ControllerInputContext inputContext =
                virtualController.getControllerInputContext();
        inputContext.rightTrigger = (byte) (active ? 0xFF : 0x00);

        virtualController.sendControllerInputContext();
    }
}
