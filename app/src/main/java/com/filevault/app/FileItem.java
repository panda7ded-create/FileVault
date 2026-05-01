package com.filevault.app;

public class FileItem {
    private String name;
    private String path;
    private long size;
    private long dateModified;

    public FileItem(String name, String path, long size, long dateModified) {
        this.name = name;
        this.path = path;
        this.size = size;
        this.dateModified = dateModified;
    }

    public String getName() { return name; }
    public String getPath() { return path; }
    public long getSize() { return size; }
    public long getDateModified() { return dateModified; }

    public String getExtension() {
        if (name.contains(".")) {
            return name.substring(name.lastIndexOf('.') + 1).toLowerCase();
        }
        return "";
    }
}