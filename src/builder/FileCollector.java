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

    public static void verifyFilePath(String path){
        //assumes a file on the end of the string
        String[] components = path.split(File.separator);
        String currPath = "";
        for (int i = 0; i < components.length - 1; ++i){
            String component = components[i];
            currPath += File.separator + component;
            verifyDir(currPath);
        }
    }

    public static void verifyDir(String path){
        File dir = new File(path);
        if (dir.isDirectory() || !dir.exists()){
            dir.mkdir();
        }
    }

    public static ArrayList<File> getAllFilesInDir(File file, String extension){
        File[] allFiles = file.listFiles();
        ArrayList<File> files = new ArrayList<>();
        if (allFiles == null){
            return files;
        }
        for (File f : allFiles){
            if (f.isFile()) {
                String name = f.getName();
                if (name.endsWith(extension)) {
                    files.add(f);
                }
            }
            if (f.isDirectory()){
                files.addAll(getAllFilesInDir(f, extension));
            }
        }

        return files;
    }

    //careful with this one.  Overwrites data super easily.
    public static void replaceFile(String name, File file) throws IOException {
        File javaFile = Builder.getJavaFileBySimpleName(name);
        if (javaFile == null){
            System.out.println("no file named: " + name);
        }

        FileUtils.forceDelete(javaFile);
        FileUtils.copyFile(file, javaFile);
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

    public void performMove() throws IOException, Prefs.NoSuchPreferenceException {
        for (String filePath : this.files){
            String relativePath = getRelativePath(Prefs.<String>getPreference(Prefs.CLASS_HOME), filePath);
            String path = this.newFolder + relativePath;
            verifyFilePath(path);

            move(path, filePath);
        }
    }

    public static String getRelativePath(String parent, String file){
        return file.substring(parent.length());
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
        } catch (Prefs.NoSuchPreferenceException e) {
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
