package com.limelight;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.binding.input.ControllerHandler;
import com.limelight.nvstream.input.ControllerPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ControllerMappingActivity extends Activity {

    private static final String PREFS_NAME = "controller_mappings";

    private ListView mappingList;
    private FrameLayout overlay;
    private TextView promptText;
    private Button resetButton;
    private Button cancelButton;

    private int pendingActionIndex = -1;

    private static class Action {
        String name;
        int flag;
        int currentKeyCode = KeyEvent.KEYCODE_UNKNOWN;

        Action(String name, int flag) {
            this.name = name;
            this.flag = flag;
        }
    }

    private final List<Action> actions = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_controller_mapping);

        initActions();
        loadCurrentMappings();

        mappingList = findViewById(R.id.mappingList);
        overlay = findViewById(R.id.overlay);
        promptText = findViewById(R.id.promptText);
        resetButton = findViewById(R.id.resetButton);
        cancelButton = findViewById(R.id.cancelButton);

        mappingList.setAdapter(new MappingAdapter());
        mappingList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                startMapping(position);
            }
        });

        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetMappings();
            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelMapping();
            }
        });
    }

    private void initActions() {
        actions.add(new Action("A", ControllerPacket.A_FLAG));
        actions.add(new Action("B", ControllerPacket.B_FLAG));
        actions.add(new Action("X", ControllerPacket.X_FLAG));
        actions.add(new Action("Y", ControllerPacket.Y_FLAG));
        actions.add(new Action("D-Pad Up", ControllerPacket.UP_FLAG));
        actions.add(new Action("D-Pad Down", ControllerPacket.DOWN_FLAG));
        actions.add(new Action("D-Pad Left", ControllerPacket.LEFT_FLAG));
        actions.add(new Action("D-Pad Right", ControllerPacket.RIGHT_FLAG));
        actions.add(new Action("Left Bumper (LB)", ControllerPacket.LB_FLAG));
        actions.add(new Action("Right Bumper (RB)", ControllerPacket.RB_FLAG));
        actions.add(new Action("Left Trigger (LT)", ControllerHandler.LT_INTERNAL_FLAG));
        actions.add(new Action("Right Trigger (RT)", ControllerHandler.RT_INTERNAL_FLAG));
        actions.add(new Action("Start", ControllerPacket.PLAY_FLAG));
        actions.add(new Action("Select", ControllerPacket.BACK_FLAG));
        actions.add(new Action("Special (Mode)", ControllerPacket.SPECIAL_BUTTON_FLAG));
        actions.add(new Action("Left Stick Click", ControllerPacket.LS_CLK_FLAG));
        actions.add(new Action("Right Stick Click", ControllerPacket.RS_CLK_FLAG));
        actions.add(new Action("Paddle 1", ControllerPacket.PADDLE1_FLAG));
        actions.add(new Action("Paddle 2", ControllerPacket.PADDLE2_FLAG));
        actions.add(new Action("Paddle 3", ControllerPacket.PADDLE3_FLAG));
        actions.add(new Action("Paddle 4", ControllerPacket.PADDLE4_FLAG));
        actions.add(new Action("Touchpad Button", ControllerPacket.TOUCHPAD_FLAG));
        actions.add(new Action("Misc (Share/Mic)", ControllerPacket.MISC_FLAG));
    }

    private void loadCurrentMappings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Map<String, ?> allEntries = prefs.getAll();
        
        // Reset current key codes
        for (Action action : actions) {
            action.currentKeyCode = KeyEvent.KEYCODE_UNKNOWN;
        }

        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            try {
                int keyCode = Integer.parseInt(entry.getKey());
                if (entry.getValue() instanceof Integer) {
                    int flag = (Integer) entry.getValue();
                    for (Action action : actions) {
                        if (action.flag == flag) {
                            action.currentKeyCode = keyCode;
                            break;
                        }
                    }
                }
            } catch (Exception e) {}
        }
    }

    private void startMapping(int position) {
        pendingActionIndex = position;
        promptText.setText("Press a button for\n" + actions.get(position).name);
        overlay.setVisibility(View.VISIBLE);
    }

    private void cancelMapping() {
        overlay.setVisibility(View.GONE);
        pendingActionIndex = -1;
    }

    private void resetMappings() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().clear().apply();
        loadCurrentMappings();
        ((BaseAdapter) mappingList.getAdapter()).notifyDataSetChanged();
        Toast.makeText(this, "Mappings reset to default", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (pendingActionIndex != -1) {
            // Ignore volume keys unless we really want them
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
                return super.onKeyDown(keyCode, event);
            }

            // Map the key
            saveMapping(keyCode, actions.get(pendingActionIndex).flag);
            cancelMapping();
            loadCurrentMappings();
            ((BaseAdapter) mappingList.getAdapter()).notifyDataSetChanged();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void saveMapping(int keyCode, int flag) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        // Remove existing mapping for this keyCode if any
        editor.remove(String.valueOf(keyCode));
        
        // Also remove any other key mapped to this flag to avoid duplicates
        Map<String, ?> all = prefs.getAll();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            if (entry.getValue() instanceof Integer && (Integer) entry.getValue() == flag) {
                editor.remove(entry.getKey());
            }
        }

        editor.putInt(String.valueOf(keyCode), flag);
        editor.apply();
    }

    private class MappingAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return actions.size();
        }

        @Override
        public Object getItem(int position) {
            return actions.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.mapping_list_item, parent, false);
            }

            Action action = actions.get(position);
            TextView nameText = convertView.findViewById(R.id.actionName);
            TextView keyText = convertView.findViewById(R.id.mappedKey);

            nameText.setText(action.name);
            
            if (action.currentKeyCode != KeyEvent.KEYCODE_UNKNOWN) {
                keyText.setText("Mapped to: " + KeyEvent.keyCodeToString(action.currentKeyCode));
                keyText.setTextColor(0xBBBB86FC); // Highlighted
            } else {
                keyText.setText("Default");
                keyText.setTextColor(0xFFAAAAAA);
            }

            return convertView;
        }
    }
}
