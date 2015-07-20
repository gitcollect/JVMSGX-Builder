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
    public static String libsString = "", secureOutput = tempStorage + "secureCompile", insecureOutput = tempStorage + "insecureCompile";

    public static void main(String[] args) throws ClassNotFoundException, IOException, Prefs.NoSuchPreferenceException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        int executionMode = validateArgs(args);
        setup();

        if (executionMode == 0){
            //todo: Show help
            //temp: Load in local pref file
            System.out.println("Please include a file.  builder.jar <build_file.txt>");
        }

        else if (executionMode == 1) {
            //try to read in the preferences file
            try {
                Prefs.getInstance().loadPrefs(args[0]); //todo: Change to args[0] for production-esk dev
                System.out.println("prefs loaded: " + args[0]);
            } catch (FileNotFoundException e) {
                System.out.println(args[0] + " is not a file.  Please provide a prefereneces file");
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

            /*
             */
             URL[] filePath = new URL[classes.size()];
             for (int i = 0; i < classes.size(); ++i){
                 File classPath = classes.get(i);
                 filePath[i] = classPath.toURI().toURL();
             }

            URL[] classesRoot = new URL[] {new File(Prefs.<String>getPreference(Prefs.CLASS_HOME)).toURI().toURL()};

             URLClassLoader loader = new URLClassLoader(classesRoot);
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
            URL[] tempRoot = new URL[] {new File(tempStorage).toURI().toURL()};
            ClassLoader tempLoader = new URLClassLoader(tempRoot);

            generateStubs(toSecure, tempLoader);

            String projectSource = Prefs.getPreference(Prefs.PROJECT_SOURCE);
            compileProjects(projectSource, tempSrc);
            packageProjects();
        }
    }

    public static void compileProjects(String securePath, String insecurePath) throws IOException, Prefs.NoSuchPreferenceException, InterruptedException {
        compileProject(securePath, secureOutput);
        compileProject(insecurePath, insecureOutput);
    }

    public static void packageProjects(){
        try {
            String saveLocation = Prefs.getPreference(Prefs.OUTPUT_DIR);
            String secureOutputLoc = saveLocation + File.separator + Prefs.getPreference(Prefs.SECURE_OUTPUT);
            String insecureOutputLoc = saveLocation + File.separator + Prefs.getPreference(Prefs.INSECURE_OUTPUT);

            packageJar(secureOutput, secureOutputLoc);
            packageJar(insecureOutput, insecureOutputLoc);

        } catch (Prefs.NoSuchPreferenceException e) {
            //todo: Preference was not loaded, encourage user to check that it's included
            e.printStackTrace();
        } catch (InterruptedException e) {
            //todo: something happened during the waitFor() w/ processbuilder.
            e.printStackTrace();
        } catch (IOException e) {
            //todo: Problems communicating with runtime
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
        verifyDir(new File(output).getParent());
        ArrayList<File> classFiles = getAllFilesInDir(new File(classes), ".class");
        String packager = "jar";
        ArrayList<String> args = new ArrayList<>();
        args.add(packager);
        args.add("cvf");
        args.add(output);
        for (File classFile : classFiles){
            args.add(classFile.getAbsolutePath());
        }
        System.out.println("jar command: " + args);
        String[] array = new String[args.size()];
        execCommand(args.toArray(array), System.getProperty("user.dir"));

        String alias = Prefs.getPreference(Prefs.SIG_NAME);
        signJar(output, alias);
    }

    public static void signJar(String jar, String alias) throws IOException, InterruptedException, Prefs.NoSuchPreferenceException {
        //todo: Sign the jar
        Signer.sign(jar, alias);
    }

    public static void setup(){
        verifyDir(tempDir);
        verifyDir(tempStorage);
        verifyDir(tempOut);
        verifyDir(tempSrc);
        verifyDir(stubsDir);
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

    public static ArrayList<String> generateStubs(ArrayList<Class<?>> toSecure, ClassLoader tempLoader) throws ClassNotFoundException, IOException {
        ArrayList<String> stubLocations = new ArrayList<>();
        for (Class<?> classFile : toSecure){
            //get path
            String javaName = classFile.getSimpleName() + ".java";

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
            String prefsFile = args[0];
            File argsFile = new File(prefsFile);
            if (!argsFile.exists()){
                System.out.println("The provided file " + prefsFile + " does not exist");
                return 0;
            }
            else if (!argsFile.getAbsolutePath().endsWith(".txt")){
                System.out.println("Please include a text file.");
                return 0;
            }
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
        builder.redirectInput(ProcessBuilder.Redirect.INHERIT);
        if (!directory.equals("")) {
            builder.directory(new File(directory));
        }
        Process someProcess = builder.start();
        someProcess.waitFor();
    }
}
