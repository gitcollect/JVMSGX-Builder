package builder;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
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

        if (executionMode == 0){
            System.out.println("Please include a file.  builder.jar <build_file.txt>");
        }

        else if (executionMode == 1) {
            setup(args);

            String projectSource = Prefs.getPreference(Prefs.PROJECT_SOURCE);
            File libsFolder = new File(new File(Prefs.<String>getPreference(Prefs.PROJECT_SOURCE)).getParent() + File.separator + "lib");
            String srcPath = (String) Prefs.getInstance().getPrefrenece(Prefs.PROJECT_SOURCE);

            //add libraries.  MUST BE IN /lib/
            ArrayList<File> libs = FileCollector.getAllFilesInDir(libsFolder, ".jar");
            for (File lib : libs){
                libsString += lib.getAbsolutePath() + ":";
            }
            if (!libsString.equals("")) libsString += '.';

            File currentDir = new File(Prefs.<String>getPreference(Prefs.CLASS_HOME));

            URL[] classesRoot = new URL[] { currentDir.toURI().toURL() };

            URLClassLoader precompileLoader = new URLClassLoader(classesRoot);
            ArrayList<Class<?>> toSecure = new ArrayList<>();
            ArrayList<String> securableObjects = (ArrayList) Prefs.getInstance().getPrefrenece(Prefs.SECURED_CLASSES);

            for (String clazzName : securableObjects){
                Class<?> clazz = precompileLoader.loadClass(clazzName);
                toSecure.add(clazz);
            }

            File srcFolder = new File(srcPath);
            if (!srcFolder.exists()){
                System.out.println("The provided source directory does not exist.  Cannot proceed.");
                return;
            }
            ArrayList<File> srcFiles = FileCollector.getAllFilesInDir(srcFolder, ".java");

            for (File file : srcFiles){
                String path = FileCollector.getRelativePath(srcPath, file.getAbsolutePath());
                String[] folderStructure = path.split(File.separator);
                String hierarchy = "";
                for (int i = 0; i < folderStructure.length - 1; ++i){
                    hierarchy += folderStructure[i].equals("") ? "" : folderStructure[i] + File.separator;
                    String subDir = tempSrc + hierarchy;
                    FileCollector.verifyDir(subDir);
                }

                String destinationPath = tempSrc + hierarchy + File.separator + file.getName();
                FileCollector.move(file.getAbsolutePath(), destinationPath);
            }

            FileCollector collector = new FileCollector(tempStorage);
            for (Class<?> clazz : toSecure){
                collector.collectClass(clazz);
            }

            try {
                collector.performMove();
            } catch (IOException e) {
                System.out.println("Failed to move the files to a temp directory.  Does this utility have permissions?");
                System.out.println("Temp Dir path: " + tempStorage + "\nFiles");
                for (String file : collector.files){
                    System.out.println(file);
                }
                e.printStackTrace();
                return;
            }

            URL[] tempRoot = new URL[] {new File(tempStorage).toURI().toURL()};
            ClassLoader tempLoader = new URLClassLoader(tempRoot);

            generateStubs(toSecure, tempLoader);
            Compiler.compileProjects(projectSource, tempSrc);
            Compiler.packageProjects();

            cleanupTemp();
        }
    }

    public static void setup(String[] args){
        FileCollector.verifyDir(tempDir);
        FileCollector.verifyDir(tempStorage);
        FileCollector.verifyDir(tempOut);
        FileCollector.verifyDir(tempSrc);
        FileCollector.verifyDir(stubsDir);

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

            FileCollector.replaceFile(javaName, stubFile);
        }

        return stubLocations;
    }

    public static File getJavaFileBySimpleName(String name){
        String srcPath = tempSrc;
        File srcDir = new File(srcPath);
        ArrayList<File> sources = FileCollector.getAllFilesInDir(srcDir, ".java");
        for (File source : sources){
            if (source.getName().equals(name)){
                return source;
            }
        }
        return null;
    }

    public static int validateArgs(String[] args){
        if (args.length == 1){
            String prefsFile = args[0];
            File argsFile = new File(prefsFile);
            if (!argsFile.exists()){
                System.out.println("The provided file " + prefsFile + " does not exist");
                return 0;
            }
            else if (!argsFile.getAbsolutePath().endsWith(".txt")){
                System.out.println("Please include a text file with preferences.  Please see the documentation for more information");
                return 0;
            }
        }

        return args.length;
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

    public static void cleanupTemp() throws IOException {
        FileUtils.deleteDirectory(new File(tempDir));
    }
}
