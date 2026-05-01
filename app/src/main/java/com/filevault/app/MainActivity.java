package com.filevault.app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private RecyclerView recyclerView;
    private FileAdapter adapter;
    private List<FileItem> fileList = new ArrayList<>();
    private FloatingActionButton fabAdd;
    private TextView tvStorageInfo, tvEmptyState;
    private LinearLayout layoutStorage;
    private String vaultPath;

    private final ActivityResultLauncher<String[]> filePickerLauncher = registerForActivityResult(
        new ActivityResultContracts.OpenMultipleDocuments(),
        uris -> {
            if (uris != null) {
                for (Uri uri : uris) {
                    copyFileToVault(uri);
                }
                loadFiles();
            }
        }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        vaultPath = getExternalFilesDir(null).getAbsolutePath() + "/FileVault";

        initViews();
        checkPermissions();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerView);
        fabAdd = findViewById(R.id.fabAdd);
        tvStorageInfo = findViewById(R.id.tvStorageInfo);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        layoutStorage = findViewById(R.id.layoutStorage);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FileAdapter(fileList, this);
        recyclerView.setAdapter(adapter);

        fabAdd.setOnClickListener(v -> openFilePicker());

        // Setup adapter callbacks
        adapter.setOnFileClickListener(file -> openFile(file));
        adapter.setOnFileMenuListener((file, item) -> {
            if (item.getTitle().equals("Delete")) {
                confirmDelete(file);
            } else if (item.getTitle().equals("Share")) {
                shareFile(file);
            } else if (item.getTitle().equals("Rename")) {
                showRenameDialog(file);
            }
        });

        // Sort button
        ImageButton btnSort = findViewById(R.id.btnSort);
        btnSort.setOnClickListener(v -> showSortDialog());
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                new AlertDialog.Builder(this)
                    .setTitle("Permission Required")
                    .setMessage("File Vault needs storage permission to save and manage your files.")
                    .setPositiveButton("Grant", (d, w) -> {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            } else {
                initVault();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                                 Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
            } else {
                initVault();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) {
                initVault();
            } else {
                showSnackbar("Permission denied. App may not work properly.");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (fileList.isEmpty() || new File(vaultPath).exists()) {
            loadFiles();
        }
    }

    private void initVault() {
        File vault = new File(vaultPath);
        if (!vault.exists()) {
            vault.mkdirs();
        }
        loadFiles();
    }

    private void loadFiles() {
        fileList.clear();
        File vault = new File(vaultPath);
        if (vault.exists() && vault.isDirectory()) {
            File[] files = vault.listFiles();
            if (files != null) {
                for (File file : files) {
                    fileList.add(new FileItem(
                        file.getName(),
                        file.getAbsolutePath(),
                        file.length(),
                        file.lastModified()
                    ));
                }
            }
        }

        // Sort by date (newest first)
        Collections.sort(fileList, (a, b) -> Long.compare(b.getDateModified(), a.getDateModified()));

        adapter.notifyDataSetChanged();
        updateStorageInfo();
        updateEmptyState();
    }

    private void updateStorageInfo() {
        long totalSize = 0;
        for (FileItem file : fileList) {
            totalSize += file.getSize();
        }
        tvStorageInfo.setText(fileList.size() + " files • " + formatFileSize(totalSize));
    }

    private void updateEmptyState() {
        if (fileList.isEmpty()) {
            tvEmptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            tvEmptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void openFilePicker() {
        filePickerLauncher.launch(new String[]{"*/*"});
    }

    private void copyFileToVault(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) return;

            String fileName = getFileName(uri);
            File outputFile = new File(vaultPath, fileName);

            // Handle duplicate names
            int counter = 1;
            String baseName = fileName;
            String ext = "";
            if (fileName.contains(".")) {
                baseName = fileName.substring(0, fileName.lastIndexOf('.'));
                ext = fileName.substring(fileName.lastIndexOf('.'));
            }
            while (outputFile.exists()) {
                fileName = baseName + "_" + counter + ext;
                outputFile = new File(vaultPath, fileName);
                counter++;
            }

            FileOutputStream outputStream = new FileOutputStream(outputFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.close();
            inputStream.close();

            showSnackbar("File added successfully!");
        } catch (Exception e) {
            showSnackbar("Failed to add file: " + e.getMessage());
        }
    }

    private String getFileName(Uri uri) {
        String result = "file";
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex("_display_name");
                    if (index >= 0) {
                        result = cursor.getString(index);
                    }
                }
            }
        }
        if (result == null || result.isEmpty()) {
            result = uri.getPath();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) {
                    result = result.substring(cut + 1);
                }
            }
        }
        return result != null ? result : "file";
    }

    private void openFile(FileItem file) {
        try {
            File f = new File(file.getPath());
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", f);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, getMimeType(file.getName()));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Open with"));
        } catch (Exception e) {
            showSnackbar("No app found to open this file type");
        }
    }

    private String getMimeType(String fileName) {
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        switch (ext) {
            case "pdf": return "application/pdf";
            case "doc": case "docx": return "application/msword";
            case "xls": case "xlsx": return "application/vnd.ms-excel";
            case "jpg": case "jpeg": return "image/jpeg";
            case "png": return "image/png";
            case "gif": return "image/gif";
            case "mp3": return "audio/mpeg";
            case "mp4": return "video/mp4";
            case "txt": return "text/plain";
            case "html": case "htm": return "text/html";
            default: return "*/*";
        }
    }

    private void shareFile(FileItem file) {
        try {
            File f = new File(file.getPath());
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", f);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType(getMimeType(file.getName()));
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share via"));
        } catch (Exception e) {
            showSnackbar("Failed to share file");
        }
    }

    private void confirmDelete(FileItem file) {
        new AlertDialog.Builder(this)
            .setTitle("Delete File")
            .setMessage("Are you sure you want to delete \"" + file.getName() + "\"?")
            .setPositiveButton("Delete", (d, w) -> {
                if (new File(file.getPath()).delete()) {
                    showSnackbar("File deleted");
                    loadFiles();
                } else {
                    showSnackbar("Failed to delete file");
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showRenameDialog(FileItem file) {
        EditText editText = new EditText(this);
        editText.setText(file.getName());
        editText.setSelection(file.getName().length());

        new AlertDialog.Builder(this)
            .setTitle("Rename File")
            .setView(editText)
            .setPositiveButton("Rename", (d, w) -> {
                String newName = editText.getText().toString().trim();
                if (!newName.isEmpty() && !newName.equals(file.getName())) {
                    File oldFile = new File(file.getPath());
                    File newFile = new File(oldFile.getParent(), newName);
                    if (oldFile.renameTo(newFile)) {
                        showSnackbar("File renamed");
                        loadFiles();
                    } else {
                        showSnackbar("Failed to rename file");
                    }
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showSortDialog() {
        String[] options = {"Date (Newest)", "Date (Oldest)", "Name (A-Z)", "Name (Z-A)", "Size (Largest)", "Size (Smallest)"};
        new AlertDialog.Builder(this)
            .setTitle("Sort by")
            .setItems(options, (d, which) -> {
                switch (which) {
                    case 0: Collections.sort(fileList, (a, b) -> Long.compare(b.getDateModified(), a.getDateModified())); break;
                    case 1: Collections.sort(fileList, (a, b) -> Long.compare(a.getDateModified(), b.getDateModified())); break;
                    case 2: Collections.sort(fileList, (a, b) -> a.getName().compareToIgnoreCase(b.getName())); break;
                    case 3: Collections.sort(fileList, (a, b) -> b.getName().compareToIgnoreCase(a.getName())); break;
                    case 4: Collections.sort(fileList, (a, b) -> Long.compare(b.getSize(), a.getSize())); break;
                    case 5: Collections.sort(fileList, (a, b) -> Long.compare(a.getSize(), b.getSize())); break;
                }
                adapter.notifyDataSetChanged();
            })
            .show();
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private void showSnackbar(String message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show();
    }
}