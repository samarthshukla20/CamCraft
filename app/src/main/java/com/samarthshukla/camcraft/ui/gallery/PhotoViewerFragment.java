
package com.samarthshukla.camcraft.ui.gallery;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.samarthshukla.camcraft.R;
import com.samarthshukla.camcraft.databinding.FragmentPhotoViewerBinding;

public class PhotoViewerFragment extends Fragment {

    private FragmentPhotoViewerBinding binding;

    public PhotoViewerFragment() {
        super(R.layout.fragment_photo_viewer);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        binding = FragmentPhotoViewerBinding.bind(view);

        String uriString = requireArguments().getString("photo_uri");

        if (uriString != null) {
            Glide.with(this)
                    .load(Uri.parse(uriString))
                    .fitCenter()
                    .into(binding.fullPhoto);
        }

        binding.btnViewerBack.setOnClickListener(v ->
                getParentFragmentManager().popBackStack()
        );
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
