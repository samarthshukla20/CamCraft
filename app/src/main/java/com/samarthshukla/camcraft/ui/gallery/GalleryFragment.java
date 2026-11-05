
package com.samarthshukla.camcraft.ui.gallery;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;

import com.samarthshukla.camcraft.R;
import com.samarthshukla.camcraft.databinding.FragmentGalleryBinding;

import com.samarthshukla.camcraft.data.model.Photo;
import java.util.ArrayList;
import java.util.List;

public class GalleryFragment extends Fragment {

    private FragmentGalleryBinding binding;
    private GalleryViewModel viewModel;
    private PhotoAdapter adapter;

    public GalleryFragment() {
        super(R.layout.fragment_gallery);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        binding = FragmentGalleryBinding.bind(view);
        viewModel = new ViewModelProvider(this).get(GalleryViewModel.class);


        adapter = new PhotoAdapter(photo -> {
            List<Photo> currentPhotos = viewModel.getPhotos().getValue();

            if (currentPhotos == null || currentPhotos.isEmpty()) {
                return;
            }

            int selectedIndex = currentPhotos.indexOf(photo);

            Bundle args = new Bundle();
            args.putInt("selected_index", selectedIndex);

            ArrayList<String> photoUris = new ArrayList<>();

            for (Photo item : currentPhotos) {
                photoUris.add(item.getUri().toString());
            }

            args.putStringArrayList("photo_uris", photoUris);

            PhotoViewerFragment viewer = new PhotoViewerFragment();
            viewer.setArguments(args);

            getParentFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, viewer)
                    .addToBackStack(null)
                    .commit();
        });


        binding.recyclerPhotos.setLayoutManager(
                new GridLayoutManager(requireContext(), 3)
        );

        binding.recyclerPhotos.setAdapter(adapter);

        binding.btnBack.setOnClickListener(v ->
                getParentFragmentManager().popBackStack()
        );

        viewModel.getPhotos().observe(getViewLifecycleOwner(), photos -> {
            adapter.submitList(photos);

            boolean empty = photos == null || photos.isEmpty();

            binding.txtEmpty.setVisibility(
                    empty ? View.VISIBLE : View.GONE
            );

            binding.recyclerPhotos.setVisibility(
                    empty ? View.GONE : View.VISIBLE
            );
        });

        viewModel.loadPhotos();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (viewModel != null) {
            viewModel.loadPhotos();
        }
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}