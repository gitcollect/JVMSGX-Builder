package builder;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;

public class Builder {
    public static String tempDir = System.getProperty("java.io.tmpdir") + "SGXBuilder" + File.separator;
    public static String tempSrc = tempDir +"src/";
    public static String tempOut = tempDir + "out/";
    public static String stubsDir = tempDir + "stubs/";
    public static String tempStorage = tempDir + "temp/";
    public static String libsString = "";

    public static void main(String[] args) throws ClassNotFoundException, IOException, Prefs.NoSuchPreferenceException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        int executionMode = validateArgs(args);
        setup();

        /*
        if (executionMode == 0){
            //todo: Show help
            //temp: Load in local pref file
        }

        else
         */
        if (executionMode == 0) {
            //try to read in the preferences file
            try {
                //String path = Builder.class.getResource("prefs.txt").getPath();
                Prefs.getInstance().loadPrefs("prefs.txt"); //todo: Change to args[0] for production-esk dev
                System.out.println("prefs loaded: " + Prefs.getInstance().getPrefrenece(Prefs.OUTPUT_DIR));
            } catch (FileNotFoundException e) {
                //System.out.println(args[0] + " is not a file.  Please provide a prefereneces file");
                e.printStackTrace();
                return;
            } catch (Prefs.MisformattedPreferences misformattedPreferences) {
                System.out.println("there was a problem reading in your preferences file");
                misformattedPreferences.printStackTrace();
                return;
            }

            //add libraries.  MUST BE IN /lib/
            File libsFolder = new File(new File(Prefs.<String>getPreference(Prefs.PROJECT_SOURCE)).getParent() + File.separator + "lib");
            ArrayList<File> libs = getAllFilesInDir(libsFolder, ".jar");
            for (File lib : libs){
                libsString += lib.getAbsolutePath() + ":";
            }
            if (!libsString.equals("")) libsString += '.';

            //collect all the secured classes.  This is a bit memory intensive
            //I will try to make it lazy-load if time permits

             System.out.println("Collecting Secure Classes");
             File currentDir = new File(Prefs.<String>getPreference(Prefs.CLASS_HOME));
             ArrayList<File> classes = getAllFilesInDir(currentDir, ".class");

             System.out.println("Number of classes in collection: " + classes.size());

             URL[] filePath = new URL[classes.size()];
             for (int i = 0; i < classes.size(); ++i){
                 File classPath = classes.get(i);
                 filePath[i] = classPath.toURI().toURL();
             }

             ClassLoader loader = new URLClassLoader(filePath);
             ArrayList<Class<?>> toSecure = new ArrayList<>();
             ArrayList<String> securableObjects = (ArrayList) Prefs.getInstance().getPrefrenece(Prefs.SECURED_CLASSES);

             for (String clazzName : securableObjects){
                Class<?> clazz = loader.loadClass(clazzName);
                toSecure.add(clazz);
             }

            String srcPath = (String) Prefs.getInstance().getPrefrenece(Prefs.PROJECT_SOURCE);
            File srcFolder = new File(srcPath);
            if (!srcFolder.exists()){
                System.out.println("The provided source directory does not exist.  Cannot proceed.");
                return;
            }
            ArrayList<File> srcFiles = getAllFilesInDir(srcFolder, ".java");

            for (File file : srcFiles){
                //copy into tempdir.
                //get package pathing
                String path = file.getAbsolutePath().substring(srcPath.length(), file.getAbsolutePath().length());
                String[] folderStructure = path.split(File.separator);
                String hierarchy = "";
                for (int i = 0; i < folderStructure.length - 1; ++i){
                    hierarchy += folderStructure[i].equals("") ? "" : folderStructure[i] + File.separator;
                    String fp = tempSrc + hierarchy;
                    verifyDir(fp);
                }
                String destinationPath = tempSrc + hierarchy + File.separator + file.getName();

                File destFile = new File(destinationPath);
                destFile.createNewFile();

                FileOutputStream fos = new FileOutputStream(destFile);
                FileUtils.copyFile(file, fos);
            }

            //copy the classes to a tmpdir
            FileCollector collector = new FileCollector(tempStorage);
            for (Class<?> clazz : toSecure){
                collector.collectClass(clazz);
            }

            try {
                collector.performMove();
            } catch (IOException e) {
                System.out.println("Failed to move the files to a temp directory.  Does this utility have permissions?");
                System.out.println("Temp Dir path: " + tempStorage);
                e.printStackTrace();
                return;
            }

            //create class loader for tempdir

            File[] tempFiles = new File(tempStorage).listFiles();
            URL[] tempUrls = new URL[tempFiles.length];
            for (int i = 0; i < tempFiles.length; ++i){
                tempUrls[i] = tempFiles[i].toURI().toURL();
            }
            ClassLoader tempLoader = new URLClassLoader(tempUrls);

            generateStubs(toSecure, tempLoader);

            String projectSource = Prefs.getPreference(Prefs.PROJECT_SOURCE);
            compileProjects(projectSource, tempSrc);
        }
    }

    public static void compileProjects(String securePath, String insecurePath) throws IOException, Prefs.NoSuchPreferenceException, InterruptedException {
        String secureOutput = tempStorage + "secureCompile";
        String insecureOutput = tempStorage + "insecureCompile";
        compileProject(securePath, secureOutput);
        compileProject(insecurePath, insecureOutput);

        try {
            String saveLocation = Prefs.getPreference(Prefs.OUTPUT_DIR);
            String secureOutputLoc = saveLocation + File.separator + Prefs.getPreference(Prefs.SECURE_OUTPUT);
            String insecureOutputLoc = saveLocation + File.separator + Prefs.getPreference(Prefs.INSECURE_OUTPUT);


            packageJar(secureOutput, secureOutputLoc);
            packageJar(insecureOutput, insecureOutputLoc);
        } catch (Prefs.NoSuchPreferenceException e) {
            e.printStackTrace();
        }
    }

    public static void compileProject(String root, String output) throws IOException, Prefs.NoSuchPreferenceException, InterruptedException {
        verifyDir(output);
        ArrayList<File> projectSourceFiles = getAllFilesInDir(new File(root), ".java");
        String parentDir = new File(Prefs.<String>getPreference(Prefs.PROJECT_SOURCE)).getParent();
        ArrayList<String> args = new ArrayList<>();
        args.add("javac");
        args.add("-Xlint:unchecked");
        args.add("-d");
        args.add(output);
        args.add("-cp");
        args.add(libsString);

        for (File classFile : projectSourceFiles){
            args.add(classFile.getAbsolutePath());
        }
        System.out.println("Compile Command: " + args);
        String[] array = new String[args.size()];
        execCommand(args.toArray(array), parentDir);
    }

    public static void packageJar(String classes, String output) throws IOException, Prefs.NoSuchPreferenceException, InterruptedException {
        verifyDir(output);
        ArrayList<File> classFiles = getAllFilesInDir(new File(classes), ".class");
        String packager = "jar";
        ArrayList<String> args = new ArrayList<>();
        args.add(packager);
        args.add("cf");
        args.add(output);
        for (File classFile : classFiles){
            args.add(classFile.getAbsolutePath());
        }
        System.out.println("jar command: " + args);
        String[] array = new String[args.size()];
        execCommand(args.toArray(array), System.getProperty("user.dir"));

        String signature = Prefs.getPreference(Prefs.SIGNATURE);
        signJar(output, signature);
        encryptJar(output);
    }

    public static void signJar(String jar, String key){
        //todo: Sign the jar
    }

    public static void encryptJar(String jar) throws Prefs.NoSuchPreferenceException {
        //todo: Encrypt the JAR against the users prefered encryption type
        String encryption = Prefs.getPreference(Prefs.CRYPTO);
    }

    public static void setup(){
        verifyDir(tempDir);
        verifyDir(tempStorage);
        verifyDir(tempOut);
        verifyDir(tempSrc);
        verifyDir(stubsDir);
    }

    public static void verifyDir(String path){
        File dir = new File(path);
        if (dir.isDirectory() || !dir.exists()){
            dir.mkdir();
        }
    }

    public static ArrayList<String> generateStubs(ArrayList<Class<?>> toSecure, ClassLoader tempLoader) throws ClassNotFoundException, IOException {
        ArrayList<String> stubLocations = new ArrayList<>();
        for (Class<?> classFile : toSecure){
            //get path
            String className = classFile.getSimpleName() + ".class";
            String javaName = classFile.getSimpleName() + ".java";
            String classPath = classFile.getResource(className).getPath();
            String classFolder = new File(classPath).getParentFile().getAbsolutePath();

            //delete class file and replace with stub
            Class<?> clazz = tempLoader.loadClass(classFile.getCanonicalName());
            CodeMonkey author = new CodeMonkey(clazz);
            author.setOutput(tempStorage, javaName);
            author.createStub();

            stubLocations.add(tempStorage + File.separator + javaName);
            File stubFile = new File(tempStorage + File.separator + javaName);

            //replace file in temp src
            replaceJavaFile(javaName, stubFile);
        }

        return stubLocations;
    }

    public static void replaceJavaFile(String name, File file) throws IOException {
        File javaFile = getJavaFileBySimpleName(name);
        if (javaFile == null){
            System.out.println("no file named: " + name);
        }

        FileUtils.forceDelete(javaFile);
        FileUtils.copyFile(file, javaFile);
    }

    public static File getJavaFileBySimpleName(String name){
        String srcPath = tempSrc;
        File srcDir = new File(srcPath);
        ArrayList<File> sources = getAllFilesInDir(srcDir, ".java");
        for (File source : sources){
            if (source.getName().equals(name)){
                return source;
            }
        }
        return null;
    }

    public static int validateArgs(String[] args){
        if (args.length == 1){
            //todo: validate that folder exists
        }
        else if (args.length == 2){
            //todo: validate folder, and make sure all args exist
        }

        return args.length;
    }

    public static boolean containsInterface(String name, Class<?> clazz){
        Class<?>[] interfaces = clazz.getInterfaces();
        for (int i = 0; i < interfaces.length; ++i){
            Class<?> curr = interfaces[i];
            if (curr.getName().contains(name)){
                return true;
            }
        }
        return false;
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

    @Deprecated
    public static void compileJavaFile(String filename, String outputDir) throws IOException {
        System.out.println("Compiling " + filename);
        String command = "javac -d " + outputDir + " " + filename;
        //execCommand(command);
        System.out.println("Sent request to output compiled source to " + outputDir);
        System.out.println(command);
    }

    public static void execCommand(String[] command, String directory) throws IOException, InterruptedException {
        //this methodology allows for better logging.
        //todo: Change all logging to file
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        builder.directory(new File(directory));
        Process someProcess = builder.start();
        someProcess.waitFor();
    }
}
