/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.content.Context;

import com.limelight.nvstream.input.ControllerPacket;

public class LeftAnalogStick extends AnalogStick {

    public LeftAnalogStick(final VirtualController controller, final Context context) {
        this(controller, context, EID_LS);
    }

    public LeftAnalogStick(final VirtualController controller, final Context context, int id) {
        super(controller, context, id);

        addAnalogStickListener(new AnalogStick.AnalogStickListener() {
            @Override
            public void onMovement(float x, float y) {
                if (isKeyboardMapping() || isMouseMapping() || isDynamicMode()) {
                    return;
                }

                VirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.leftStickX = (short) (x * 0x7FFE);
                inputContext.leftStickY = (short) (y * 0x7FFE);

                controller.sendControllerInputContext();
            }

            @Override
            public void onClick() {
            }

            @Override
            public void onDoubleClick() {
                if (isKeyboardMapping()) {
                    return;
                }
                VirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.inputMap |= ControllerPacket.LS_CLK_FLAG;

                controller.sendControllerInputContext();
            }

            @Override
            public void onRevoke() {
                if (isKeyboardMapping()) {
                    return;
                }
                VirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.inputMap &= ~ControllerPacket.LS_CLK_FLAG;

                controller.sendControllerInputContext();
            }
        });
    }
}
