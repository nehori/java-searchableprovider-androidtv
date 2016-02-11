package com.nehori.searchableprovider;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StorageUtil {

    private static final String TAG = StorageUtil.class.getSimpleName();

    private StorageUtil() {
    }

    public static List<String> getUsbDevicePaths(){
        List<String> mountList = new ArrayList<String>();
        Scanner scanner = null;

        try {
            scanner = new Scanner(new FileInputStream(new File("/proc/mounts")));

            while (scanner.hasNextLine()){
                String line = scanner.nextLine();
                if (line.contains("/storage/") && !line.contains("/storage/emulated")){
                   Log.d(TAG, "found line = " + line);
                    Pattern p = Pattern.compile("(/storage/\\S+)");
                    Matcher matcher = p.matcher(line);
                   if(matcher.find()){
                       Log.d(TAG, "args0 = " + matcher.group(1));
                       String path = matcher.group(1);
                       mountList.add(path);
                   } else {
                       Log.d(TAG, "error:");
                   }
                }
            }

        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } finally {
            if(scanner != null){
                scanner.close();
            }
        }
        return mountList;
    }
}
