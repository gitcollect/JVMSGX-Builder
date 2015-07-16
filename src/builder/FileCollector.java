package builder;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;

public class FileCollector {
    String workingDirectory, newFolder;
    ArrayList<String> files;

    public FileCollector(String folderPath){
        this.workingDirectory = System.getProperty("user.dir");
        this.newFolder = folderPath;
        this.files = new ArrayList<>();
    }

    public FileCollector() throws Prefs.NoSuchPreferenceException {
        this((String) Prefs.getInstance().getPrefrenece(Prefs.OUTPUT_DIR));
    }

    public void collect(String file, boolean local){
        if (local){
            file = this.workingDirectory + File.separator + file;
        }
        this.files.add(file);
    }

    public void collectClass(Class<?> clazz){
        String path = getPathForClass(clazz);
        this.files.add(path);
    }

    public void collectClass(String className) throws ClassNotFoundException {
        Class<?> clazz = Class.forName(className);
        this.collectClass(clazz);
    }

    public static String getPathForClass(Class<?> clazz){
        URL path = clazz.getResource(clazz.getSimpleName()+ ".class");
        String strPath = path.getPath();
        return strPath;
    }

    public static String getPathForClassName(String name) throws ClassNotFoundException {
        Class<?> clazz = Class.forName(name);
        return getPathForClass(clazz);
    }

    public void performMove() throws IOException {
        for (String filePath : this.files){

            System.out.print("Moving file: " + filePath + " ");

            //get file name
            String[] delimited = filePath.split(File.separator);
            String filename = delimited[delimited.length - 1];

            //open stream to new file
            String path = this.newFolder + filename;
            System.out.println("To new directory: " + path);
            File newFile = new File(path);
            FileOutputStream fos = new FileOutputStream(newFile);

            //get current file and perform copy
            File currentFile = new File(filePath);
            FileUtils.copyFile(currentFile, fos);

            //cleanup
            fos.flush();
            fos.close();
        }
    }

    public static void main(String[] args){
        //test main.  moves files to desktop
        String desktopDir = System.getProperty("user.home") + File.separator + "Desktop";
        FileCollector collector = new FileCollector(desktopDir);

        collector.collectClass(FileCollector.class);
        collector.collectClass(Prefs.class);
        collector.collectClass(CodeMonkey.class);

        try {
            collector.performMove();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void move(String source, String dest) throws IOException {
        File sourceFile = new File(source);
        FileOutputStream fos = new FileOutputStream(new File(dest));
        FileUtils.copyFile(sourceFile, fos);

        fos.flush();
        fos.close();
    }
}