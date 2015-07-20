package builder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class Compiler {
    public static void compileProjects(String securePath, String insecurePath) throws IOException, Prefs.NoSuchPreferenceException, InterruptedException {
        compileProject(securePath, Builder.secureOutput);
        compileProject(insecurePath, Builder.insecureOutput);
    }

    public static void packageProjects(){
        try {
            String saveLocation = Prefs.getPreference(Prefs.OUTPUT_DIR);
            String secureOutputLoc = saveLocation + File.separator + Prefs.getPreference(Prefs.SECURE_OUTPUT);
            String insecureOutputLoc = saveLocation + File.separator + Prefs.getPreference(Prefs.INSECURE_OUTPUT);

            packageJar(Builder.secureOutput, secureOutputLoc);
            packageJar(Builder.insecureOutput, insecureOutputLoc);

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
        FileCollector.verifyDir(output);
        ArrayList<File> projectSourceFiles = FileCollector.getAllFilesInDir(new File(root), ".java");
        String parentDir = new File(Prefs.<String>getPreference(Prefs.PROJECT_SOURCE)).getParent();
        ArrayList<String> args = new ArrayList<>();
        args.add("javac");
        args.add("-Xlint:unchecked");
        args.add("-d");
        args.add(output);
        if (!Builder.libsString.equals("")) {
            args.add("-cp");
            args.add(Builder.libsString);
        }

        for (File classFile : projectSourceFiles){
            args.add(classFile.getAbsolutePath());
        }
        System.out.println("Compile Command: " + args);
        String[] array = new String[args.size()];
        Builder.execCommand(args.toArray(array), parentDir);
    }

    public static void packageJar(String classes, String output) throws IOException, Prefs.NoSuchPreferenceException, InterruptedException {
        FileCollector.verifyDir(new File(output).getParent());
        ArrayList<File> classFiles = FileCollector.getAllFilesInDir(new File(classes), ".class");
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
        Builder.execCommand(args.toArray(array), System.getProperty("user.dir"));

        String alias = Prefs.getPreference(Prefs.SIG_NAME);
        signJar(output, alias);
    }

    public static void signJar(String jar, String alias) throws IOException, InterruptedException, Prefs.NoSuchPreferenceException {
        //todo: Sign the jar
        Signer.sign(jar, alias);
    }
}
