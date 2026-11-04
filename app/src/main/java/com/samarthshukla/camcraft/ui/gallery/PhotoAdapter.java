
package com.samarthshukla.camcraft.ui.gallery;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.samarthshukla.camcraft.data.model.Photo;
import com.samarthshukla.camcraft.databinding.ItemPhotoBinding;

import java.util.ArrayList;
import java.util.List;

public class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder> {

    public interface OnPhotoClickListener {
        void onPhotoClick(Photo photo);
    }

    private final List<Photo> photos = new ArrayList<>();
    private final OnPhotoClickListener listener;

    public PhotoAdapter(OnPhotoClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<Photo> newPhotos) {
        photos.clear();
        photos.addAll(newPhotos);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        ItemPhotoBinding binding = ItemPhotoBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );

        return new PhotoViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(
            @NonNull PhotoViewHolder holder,
            int position
    ) {
        Photo photo = photos.get(position);

        Glide.with(holder.binding.photoImage.getContext())
                .load(photo.getUri())
                .centerCrop()
                .into(holder.binding.photoImage);

        holder.itemView.setOnClickListener(
                v -> listener.onPhotoClick(photo)
        );
    }

    @Override
    public int getItemCount() {
        return photos.size();
    }

    static class PhotoViewHolder extends RecyclerView.ViewHolder {

        final ItemPhotoBinding binding;

        PhotoViewHolder(ItemPhotoBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
