package com.filevault.app;

import android.content.Context;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.*;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {

    private List<FileItem> files;
    private Context context;
    private OnFileClickListener clickListener;
    private OnFileMenuListener menuListener;

    public interface OnFileClickListener { void onFileClick(FileItem file); }
    public interface OnFileMenuListener { void onFileMenu(FileItem file, android.view.MenuItem item); }

    public FileAdapter(List<FileItem> files, Context context) {
        this.files = files;
        this.context = context;
    }

    public void setOnFileClickListener(OnFileClickListener l) { this.clickListener = l; }
    public void setOnFileMenuListener(OnFileMenuListener l) { this.menuListener = l; }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_file, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FileItem file = files.get(position);
        holder.tvName.setText(file.getName());
        holder.tvSize.setText(formatSize(file.getSize()));
        holder.tvDate.setText(new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                .format(new Date(file.getDateModified())));
        holder.tvExt.setText(file.getExtension().toUpperCase());

        holder.icon.setImageResource(getFileIcon(file.getExtension()));
        holder.icon.setColorFilter(context.getResources().getColor(getFileColor(file.getExtension()), null));

        holder.btnMenu.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(context, holder.btnMenu);
            popup.getMenuInflater().inflate(R.menu.file_menu, popup.getMenu());
            popup.setOnMenuItemClickListener(item -> {
                if (menuListener != null) menuListener.onFileMenu(file, item);
                return true;
            });
            popup.show();
        });

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onFileClick(file);
        });
    }

    @Override
    public int getItemCount() { return files.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView tvName, tvSize, tvDate, tvExt;
        ImageButton btnMenu;

        ViewHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.ivIcon);
            tvName = itemView.findViewById(R.id.tvName);
            tvSize = itemView.findViewById(R.id.tvSize);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvExt = itemView.findViewById(R.id.tvExt);
            btnMenu = itemView.findViewById(R.id.btnMenu);
        }
    }

    private int getFileIcon(String ext) {
        switch (ext) {
            case "pdf": return android.R.drawable.ic_menu_agenda;
            case "doc": case "docx": return android.R.drawable.ic_menu_edit;
            case "jpg": case "jpeg": case "png": case "gif": return android.R.drawable.ic_menu_gallery;
            case "mp3": case "wav": case "aac": return android.R.drawable.ic_menu_compass;
            case "mp4": case "avi": case "mkv": return android.R.drawable.ic_menu_slideshow;
            case "zip": case "rar": case "7z": return android.R.drawable.ic_menu_archive;
            case "txt": return android.R.drawable.ic_menu_sort_by_size;
            default: return android.R.drawable.ic_menu_attachment;
        }
    }

    private int getFileColor(String ext) {
        switch (ext) {
            case "pdf": return android.R.color.holo_red_light;
            case "doc": case "docx": return android.R.color.holo_blue_light;
            case "jpg": case "jpeg": case "png": case "gif": return android.R.color.holo_purple;
            case "mp3": case "wav": case "aac": return android.R.color.holo_orange_light;
            case "mp4": case "avi": case "mkv": return android.R.color.holo_orange_dark;
            case "zip": case "rar": case "7z": return android.R.color.darker_gray;
            default: return android.R.color.holo_green_dark;
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}