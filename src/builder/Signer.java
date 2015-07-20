package builder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class Signer {
    private ArrayList<String> jarPaths;
    private String alias;

    public Signer(String alias, String... paths){
        jarPaths = new ArrayList<>();
        this.alias = alias;
        for (String path: paths){
            jarPaths.add(path);
        }
    }

    public void signAll(){
        for (String path : jarPaths){
            try {
                sign(path, alias);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (Prefs.NoSuchPreferenceException e) {
                e.printStackTrace();
            }
        }
    }

    public void addJar(String jar){
        jarPaths.add(jar);
    }

    public String[] getJarPaths(){
        String[] data = new String[jarPaths.size()];
        return jarPaths.toArray(data);
    }

    public static boolean isValidJar(String path){
        File jar = new File(path);
        if (!jar.exists()){
            System.out.println(path + " does not exist. aborting sign");
            return false;
        }
        if (!path.endsWith(".jar")){
            System.out.println(path + " is not a jar file, aborting sign");
            return false;
        }
        return true;
    }

    public static void sign(String path, String alias) throws IOException, InterruptedException, Prefs.NoSuchPreferenceException {
        if (!isValidJar(path)){
            return;
        }

        /*
        Console console = System.console();
        if (console == null) {
            System.out.println("Couldn't get a reference to the console. \nSigning aborted");
            return;
        }
        char storePassword[] = System.console().readPassword("Enter Passphrase for keystore: ");
        */
        String tsa = Prefs.getPreference(Prefs.TSA);
        boolean hasTSA = !tsa.equals("NONE");
        int length = hasTSA ? 5 : 3;

        String[] command = new String[length];
        command[0] = "jarsigner";
        command[1] = path;
        command[2] = alias;

        if (hasTSA){
            command[3] = "-tsa";
            command[4] = tsa;
        }

        Builder.execCommand(command, "");
    }
}
