package com.limelight.grid;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.limelight.PcView;
import com.mooncake.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.Collections;
import java.util.Comparator;

public class PcGridAdapter extends GenericGridAdapter<PcView.ComputerObject> {

    public PcGridAdapter(Context context, PreferenceConfiguration prefs) {
        super(context, getLayoutIdForPreferences(prefs));
    }

    private static int getLayoutIdForPreferences(PreferenceConfiguration prefs) {
        return prefs.listView ? R.layout.pc_list_item : R.layout.pc_grid_item;
    }

    public void updateLayoutWithPreferences(Context context, PreferenceConfiguration prefs) {
        // This will trigger the view to reload with the new layout
        setLayoutId(getLayoutIdForPreferences(prefs));
    }

    public void addComputer(PcView.ComputerObject computer) {
        itemList.add(computer);
        sortList();
    }

    private void sortList() {
        Collections.sort(itemList, new Comparator<PcView.ComputerObject>() {
            @Override
            public int compare(PcView.ComputerObject lhs, PcView.ComputerObject rhs) {
                return lhs.details.name.toLowerCase().compareTo(rhs.details.name.toLowerCase());
            }
        });
    }

    public boolean removeComputer(PcView.ComputerObject computer) {
        return itemList.remove(computer);
    }

    @Override
    public void populateView(View parentView, ImageView imgView, ProgressBar prgView, TextView txtView, ImageView overlayView, PcView.ComputerObject obj) {
        imgView.setImageResource(R.drawable.ic_computer);
        if (obj.details.state == ComputerDetails.State.ONLINE) {
            imgView.setAlpha(1.0f);
        }
        else {
            imgView.setAlpha(0.4f);
        }

        if (obj.details.state == ComputerDetails.State.UNKNOWN) {
            prgView.setVisibility(View.VISIBLE);
        }
        else {
            prgView.setVisibility(View.INVISIBLE);
        }

        txtView.setText(obj.details.name);
        if (obj.details.state == ComputerDetails.State.ONLINE) {
            txtView.setAlpha(1.0f);
        }
        else {
            txtView.setAlpha(0.4f);
        }

        if (obj.details.state == ComputerDetails.State.OFFLINE) {
            overlayView.setImageResource(R.drawable.ic_pc_offline);
            overlayView.setAlpha(0.4f);
            overlayView.setVisibility(View.VISIBLE);
        }
        // We must check if the status is exactly online and unpaired
        // to avoid colliding with the loading spinner when status is unknown
        else if (obj.details.state == ComputerDetails.State.ONLINE &&
                obj.details.pairState == PairingManager.PairState.NOT_PAIRED) {
            overlayView.setImageResource(R.drawable.ic_lock);
            overlayView.setAlpha(1.0f);
            overlayView.setVisibility(View.VISIBLE);
        }
        else {
            overlayView.setVisibility(View.GONE);
        }

        // List Mode specific views
        TextView statusText = parentView.findViewById(R.id.pc_status_text);
        if (statusText != null) {
            switch (obj.details.state) {
                case ONLINE:
                    statusText.setText("● ONLINE");
                    statusText.setTextColor(0xFF00FF00); // Green
                    break;
                case OFFLINE:
                    statusText.setText("○ OFFLINE");
                    statusText.setTextColor(0xFFAAAAAA); // Grey
                    break;
                case UNKNOWN:
                    statusText.setText("? UNKNOWN");
                    statusText.setTextColor(0xFFFFFF00); // Yellow
                    break;
            }
        }

        TextView ipAddress = parentView.findViewById(R.id.pc_ip_address);
        if (ipAddress != null) {
            if (obj.details.activeAddress != null) {
                ipAddress.setText(obj.details.activeAddress.address);
                ipAddress.setVisibility(View.VISIBLE);
            } else {
                ipAddress.setVisibility(View.GONE);
            }
        }

        TextView controllerProfile = parentView.findViewById(R.id.pc_controller_profile);
        if (controllerProfile != null) {
            if (obj.details.runningGameId != 0) {
                controllerProfile.setText("Playing: ID " + obj.details.runningGameId);
            } else {
                controllerProfile.setText("Controller: Standard");
            }
        }

        TextView playButton = parentView.findViewById(R.id.play_button_text);
        if (playButton != null) {
            if (obj.details.state == ComputerDetails.State.ONLINE && 
                obj.details.pairState == PairingManager.PairState.PAIRED) {
                playButton.setVisibility(View.VISIBLE);
            } else {
                playButton.setVisibility(View.GONE);
            }
        }
        
        TextView latencyText = parentView.findViewById(R.id.pc_latency);
        if (latencyText != null) {
            // Latency isn't readily available on this screen in Moonlight
            latencyText.setVisibility(View.GONE);
        }
    }
}
