package ru.fdman.bidfx;

/**
 * Created by fdman on 13.04.2014.
 */
public enum FileType {
    NEF(new String[]{"NEF"}),
    JPG(new String[]{"JPG","JPEG"}),
    GIF(new String[]{"GIF"}),
    BID(new String[]{"BID"}),
    ;



    private final String[] extensions;

    FileType(String[] extensions) {
        this.extensions = extensions;
    }

    public String[] getExtensions() {
        return extensions;
    }
}
